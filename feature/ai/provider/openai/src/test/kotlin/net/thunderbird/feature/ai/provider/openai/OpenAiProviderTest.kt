package net.thunderbird.feature.ai.provider.openai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.ai.api.AiAccountPolicy
import net.thunderbird.feature.ai.api.AiClassificationCategory
import net.thunderbird.feature.ai.api.AiClassificationInput
import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiCredentialOperationResult
import net.thunderbird.feature.ai.api.AiCredentialStatus
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProviderConfiguration
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiSettings
import net.thunderbird.feature.ai.api.AiSettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class OpenAiProviderTest {
    private val json = Json
    private val settingsRepository = FakeAiSettingsRepository(
        AiSettings(
            enabled = true,
            providerConfiguration = AiProviderConfiguration(
                providerId = AiProviderId("openai"),
                modelId = AiModelId("configured-model"),
            ),
            accountPolicies = mapOf("account" to AiAccountPolicy(enabled = true)),
        ),
    )

    @Test
    fun `successful classification parses categories and confidence`() = runTest {
        val captured = CapturingInterceptor(responseFor("""{"categories":["NEWSLETTER"],"confidence":0.95}"""))
        val testSubject = createTestSubject(captured)

        val result = testSubject.execute(classificationRequest())

        val classification = assertIs<AiResult.Classification>(result).output
        assertEquals(setOf(AiClassificationCategory.NEWSLETTER), classification.categories)
        assertEquals(0.95, classification.confidence)
        assertEquals(AiModelId("configured-model"), classification.metadata.modelId)
        assertEquals("Bearer test-secret", captured.request?.header("Authorization"))
    }

    @Test
    fun `multiple categories are preserved`() = runTest {
        val testSubject = createTestSubject(
            CapturingInterceptor(responseFor("""{"categories":["INVOICE","ACTION"],"confidence":0.8}""")),
        )

        val result = testSubject.execute(classificationRequest())

        assertEquals(
            setOf(AiClassificationCategory.INVOICE, AiClassificationCategory.ACTION),
            assertIs<AiResult.Classification>(result).output.categories,
        )
    }

    @Test
    fun `unknown category returns invalid response`() = runTest {
        val testSubject = createTestSubject(
            CapturingInterceptor(responseFor("""{"categories":["UNKNOWN"],"confidence":0.8}""")),
        )

        assertEquals(
            AiError.InvalidResponse,
            assertIs<AiResult.Failure>(testSubject.execute(classificationRequest())).error,
        )
    }

    @Test
    fun `malformed json returns invalid response`() = runTest {
        val testSubject = createTestSubject(CapturingInterceptor(responseFor("not-json")))

        assertEquals(
            AiError.InvalidResponse,
            assertIs<AiResult.Failure>(testSubject.execute(classificationRequest())).error,
        )
    }

    @Test
    fun `http authentication and rate limit errors are mapped`() = runTest {
        assertEquals(AiError.Authentication, executeWithStatus(401))
        assertEquals(AiError.Authentication, executeWithStatus(403))
        assertEquals(AiError.RateLimited, executeWithStatus(429))
        assertEquals(AiError.Unknown, executeWithStatus(500))
    }

    @Test
    fun `request contains only provided classification data and configured model`() = runTest {
        val captured = CapturingInterceptor(responseFor("""{"categories":[],"confidence":0.0}"""))
        val testSubject = createTestSubject(captured)

        testSubject.execute(
            AiRequest.Classification(
                AiClassificationInput(
                    sender = "sender@example.com",
                    subject = "Subject",
                    preview = "Preview",
                ),
            ),
        )

        val body = captured.request!!.body!!.let { body ->
            okio.Buffer().also(body::writeTo).readUtf8()
        }
        assertTrue(body.contains("configured-model"))
        assertTrue(body.contains("sender@example.com"))
        assertTrue(body.contains("Subject"))
        assertTrue(body.contains("Preview"))
        assertFalse(body.contains("test-secret"))
    }

    @Test
    fun `missing credential returns authentication`() = runTest {
        val testSubject = OpenAiProvider(
            httpClient = OkHttpClient(),
            credentialStore = FakeAiCredentialStore(null),
            settingsRepository = settingsRepository,
        )

        assertEquals(
            AiError.Authentication,
            assertIs<AiResult.Failure>(testSubject.execute(classificationRequest())).error,
        )
    }

    @Test
    fun `cancellation is mapped to cancelled`() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .addInterceptor {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                CapturingInterceptor(responseFor("""{"categories":[],"confidence":0.0}""")).intercept(it)
            }
            .build()
        val testSubject = OpenAiProvider(
            httpClient = client,
            credentialStore = FakeAiCredentialStore(AiCredential("test-secret")),
            settingsRepository = settingsRepository,
        )
        var result: AiResult? = null
        val job = launch(Dispatchers.IO) { result = testSubject.execute(classificationRequest()) }

        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        job.join()

        assertEquals(AiError.Cancelled, assertIs<AiResult.Failure>(result).error)
    }

    @Test
    fun `non HTTPS endpoint is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            OpenAiProvider(
                httpClient = OkHttpClient(),
                credentialStore = FakeAiCredentialStore(null),
                settingsRepository = settingsRepository,
                endpoint = "http://localhost/responses".toHttpUrl(),
            )
        }
    }

    private suspend fun executeWithStatus(status: Int): AiError {
        val testSubject = createTestSubject(CapturingInterceptor(status = status))
        return assertIs<AiResult.Failure>(testSubject.execute(classificationRequest())).error
    }

    private fun createTestSubject(interceptor: Interceptor): OpenAiProvider = OpenAiProvider(
        httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        credentialStore = FakeAiCredentialStore(AiCredential("test-secret")),
        settingsRepository = settingsRepository,
    )

    private fun classificationRequest() = AiRequest.Classification(
        AiClassificationInput(
            sender = "sender@example.com",
            subject = "Subject",
            preview = "Preview",
        ),
    )

    private fun responseFor(output: String): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("output", buildJsonArray {
                add(buildJsonObject {
                    put("type", "message")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "output_text")
                            put("text", output)
                        })
                    })
                })
            })
        },
    )

    private class CapturingInterceptor(
        private val body: String = "{}",
        private val status: Int = 200,
    ) : Interceptor {
        var request: okhttp3.Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            request = chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private class FakeAiCredentialStore(
        private val credential: AiCredential?,
    ) : AiCredentialStore {
        override suspend fun read(providerId: AiProviderId): AiCredential? = credential
        override suspend fun status(providerId: AiProviderId): AiCredentialStatus =
            if (credential == null) AiCredentialStatus.Missing else AiCredentialStatus.Available

        override suspend fun write(
            providerId: AiProviderId,
            credential: AiCredential,
        ): AiCredentialOperationResult = AiCredentialOperationResult.Success

        override suspend fun delete(providerId: AiProviderId): AiCredentialOperationResult =
            AiCredentialOperationResult.Success
    }

    private class FakeAiSettingsRepository(
        initialSettings: AiSettings,
    ) : AiSettingsRepository {
        override val settings = MutableStateFlow(initialSettings)
        override suspend fun updateGlobal(
            enabled: Boolean,
            providerConfiguration: AiProviderConfiguration?,
        ) = Unit

        override suspend fun updateAccountPolicy(accountId: String, policy: AiAccountPolicy) = Unit
    }
}
