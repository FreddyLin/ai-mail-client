package net.thunderbird.feature.ai.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.ai.api.AiAccountPolicy
import net.thunderbird.feature.ai.api.AiCapability
import net.thunderbird.feature.ai.api.AiClassificationInput
import net.thunderbird.feature.ai.api.AiDataAccessLevel
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProvider
import net.thunderbird.feature.ai.api.AiProviderAvailability
import net.thunderbird.feature.ai.api.AiProviderConfiguration
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiProviderRegistry
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestDecision
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiSettings
import net.thunderbird.feature.ai.api.AiSettingsRepository
import net.thunderbird.feature.ai.api.AiSummarizationInput
import net.thunderbird.feature.ai.api.AiWritingInput
import net.thunderbird.feature.ai.api.AiWritingOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultAiRequestPolicyTest {
    @Test
    fun `global AI disabled by default`() = runTest {
        val testSubject = createPolicy(AiSettings())

        val result = testSubject.evaluate(ACCOUNT_ID, request())

        assertEquals(AiRequestDecision.Denied(AiError.Disabled), result)
    }

    @Test
    fun `account AI disabled by default`() = runTest {
        val testSubject = createPolicy(enabledSettings(AiAccountPolicy(enabled = false)))

        val result = testSubject.evaluate(ACCOUNT_ID, request())

        assertEquals(AiRequestDecision.Denied(AiError.AccountNotAllowed), result)
    }

    @Test
    fun `metadata only removes preview and full message`() = runTest {
        val testSubject = createPolicy(enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.METADATA_ONLY)))

        val result = assertIs<AiRequestDecision.Allowed>(testSubject.evaluate(ACCOUNT_ID, request()))
        val input = assertIs<AiRequest.Classification>(result.request).input

        assertEquals("sender", input.sender)
        assertEquals("subject", input.subject)
        assertEquals(null, input.preview)
        assertEquals(null, input.fullMessage)
    }

    @Test
    fun `preview access removes full message and limits preview`() = runTest {
        val testSubject = createPolicy(enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.PREVIEW)))

        val result = assertIs<AiRequestDecision.Allowed>(testSubject.evaluate(ACCOUNT_ID, request()))
        val input = (result.request as AiRequest.Classification).input

        assertEquals(2_000, input.preview?.length)
        assertEquals(null, input.fullMessage)
    }

    @Test
    fun `metadata only denies summarization`() = runTest {
        val settings = enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.METADATA_ONLY))
        val result = createPolicy(settings, FakeProvider(setOf(AiCapability.SUMMARIZATION)))
            .evaluate(ACCOUNT_ID, summarizationRequest())

        assertEquals(AiRequestDecision.Denied(AiError.InsufficientDataAccess), result)
    }

    @Test
    fun `preview access keeps only bounded preview for summarization`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.PREVIEW)),
            FakeProvider(setOf(AiCapability.SUMMARIZATION)),
        )

        val result = assertIs<AiRequestDecision.Allowed>(testSubject.evaluate(ACCOUNT_ID, summarizationRequest()))
        val input = assertIs<AiRequest.Summarization>(result.request).input

        assertEquals(2_000, input.preview?.length)
        assertEquals(null, input.content)
    }

    @Test
    fun `empty preview denies summarization`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.PREVIEW)),
            FakeProvider(setOf(AiCapability.SUMMARIZATION)),
        )

        val result = testSubject.evaluate(
            ACCOUNT_ID,
            AiRequest.Summarization(AiSummarizationInput(preview = " ", content = "content")),
        )

        assertEquals(AiRequestDecision.Denied(AiError.InsufficientDataAccess), result)
    }

    @Test
    fun `full message access keeps bounded content for summarization`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.FULL_MESSAGE)),
            FakeProvider(setOf(AiCapability.SUMMARIZATION)),
        )

        val result = assertIs<AiRequestDecision.Allowed>(testSubject.evaluate(ACCOUNT_ID, summarizationRequest()))
        val input = assertIs<AiRequest.Summarization>(result.request).input

        assertEquals(32_000, input.content?.length)
        assertEquals("preview", input.preview)
    }

    @Test
    fun `empty full message denies summarization`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.FULL_MESSAGE)),
            FakeProvider(setOf(AiCapability.SUMMARIZATION)),
        )

        val result = testSubject.evaluate(
            ACCOUNT_ID,
            AiRequest.Summarization(AiSummarizationInput(content = "")),
        )

        assertEquals(AiRequestDecision.Denied(AiError.InsufficientDataAccess), result)
    }

    @Test
    fun `metadata only denies writing with source content`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.METADATA_ONLY)),
            FakeProvider(setOf(AiCapability.WRITING)),
        )

        val result = testSubject.evaluate(
            ACCOUNT_ID,
            writingRequest(sourceContent = "source content", draftText = "draft text"),
        )

        assertEquals(AiRequestDecision.Denied(AiError.InsufficientDataAccess), result)
    }

    @Test
    fun `metadata only preserves draft-only writing without source content`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.METADATA_ONLY)),
            FakeProvider(setOf(AiCapability.WRITING)),
        )

        val result = assertIs<AiRequestDecision.Allowed>(
            testSubject.evaluate(
                ACCOUNT_ID,
                writingRequest(sourceContent = null, draftText = "draft text"),
            ),
        )
        val input = assertIs<AiRequest.Writing>(result.request).input

        assertEquals(null, input.sourceContent)
        assertEquals("draft text", input.draftText)
    }

    @Test
    fun `preview access limits writing source content and preserves draft separately`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.PREVIEW)),
            FakeProvider(setOf(AiCapability.WRITING)),
        )

        val result = assertIs<AiRequestDecision.Allowed>(
            testSubject.evaluate(
                ACCOUNT_ID,
                writingRequest(sourceContent = "source".repeat(1_000), draftText = "draft text"),
            ),
        )
        val input = assertIs<AiRequest.Writing>(result.request).input

        assertEquals(2_000, input.sourceContent?.length)
        assertEquals("draft text", input.draftText)
    }

    @Test
    fun `full message access limits only explicitly supplied writing source content`() = runTest {
        val testSubject = createPolicy(
            enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.FULL_MESSAGE)),
            FakeProvider(setOf(AiCapability.WRITING)),
        )

        val result = assertIs<AiRequestDecision.Allowed>(
            testSubject.evaluate(
                ACCOUNT_ID,
                writingRequest(sourceContent = "source".repeat(20_000), draftText = "draft text"),
            ),
        )
        val input = assertIs<AiRequest.Writing>(result.request).input

        assertEquals(32_000, input.sourceContent?.length)
        assertEquals("draft text", input.draftText)
    }

    @Test
    fun `full message access preserves explicit input`() = runTest {
        val testSubject = createPolicy(enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.FULL_MESSAGE)))

        val result = assertIs<AiRequestDecision.Allowed>(testSubject.evaluate(ACCOUNT_ID, request()))
        val input = (result.request as AiRequest.Classification).input

        assertEquals("preview".repeat(1_000), input.preview)
        assertEquals("full message", input.fullMessage)
    }

    @Test
    fun `missing provider configuration is denied`() = runTest {
        val settings = AiSettings(
            enabled = true,
            accountPolicies = mapOf(ACCOUNT_ID to AiAccountPolicy(enabled = true)),
        )

        val result = createPolicy(settings).evaluate(ACCOUNT_ID, request())

        assertEquals(AiRequestDecision.Denied(AiError.ProviderNotConfigured), result)
    }

    @Test
    fun `unsupported capability is denied`() = runTest {
        val testSubject = createPolicy(
            settings = enabledSettings(AiAccountPolicy(enabled = true)),
            provider = FakeProvider(capabilities = emptySet()),
        )

        val result = testSubject.evaluate(ACCOUNT_ID, request())

        assertEquals(
            AiRequestDecision.Denied(AiError.UnsupportedCapability(AiCapability.CLASSIFICATION)),
            result,
        )
    }

    @Test
    fun `provider and model selection are retained`() = runTest {
        val configuration = AiProviderConfiguration(AiProviderId("fake"), AiModelId("model"))
        val settings = enabledSettings(AiAccountPolicy(enabled = true)).copy(
            providerConfiguration = configuration,
        )

        val result = assertIs<AiRequestDecision.Allowed>(createPolicy(settings).evaluate(ACCOUNT_ID, request()))

        assertEquals(configuration, result.providerConfiguration)
    }

    @Test
    fun `connection test is denied when global AI is disabled`() = runTest {
        val result = createPolicy(AiSettings()).evaluateConnectionTest()

        assertEquals(AiRequestDecision.Denied(AiError.Disabled), result)
    }

    @Test
    fun `connection test is denied when provider is not configured`() = runTest {
        val result = createPolicy(AiSettings(enabled = true)).evaluateConnectionTest()

        assertEquals(AiRequestDecision.Denied(AiError.ProviderNotConfigured), result)
    }

    @Test
    fun `connection test is denied when configured provider is not available`() = runTest {
        val settings = AiSettings(
            enabled = true,
            providerConfiguration = AiProviderConfiguration(AiProviderId("fake"), AiModelId("model")),
        )

        val result = createPolicy(settings = settings, provider = null).evaluateConnectionTest()

        assertEquals(AiRequestDecision.Denied(AiError.ProviderNotConfigured), result)
    }

    @Test
    fun `connection test is denied when classification is not supported`() = runTest {
        val settings = AiSettings(
            enabled = true,
            providerConfiguration = AiProviderConfiguration(AiProviderId("fake"), AiModelId("model")),
        )
        val testSubject = DefaultAiRequestPolicy(
            settingsRepository = InMemoryAiSettingsRepository(settings),
            providerRegistry = DefaultAiProviderRegistry(
                providers = listOf(FakeProvider(capabilities = emptySet())),
            ),
        )

        val result = testSubject.evaluateConnectionTest()

        assertEquals(
            AiRequestDecision.Denied(AiError.UnsupportedCapability(AiCapability.CLASSIFICATION)),
            result,
        )
    }

    @Test
    fun `connection test does not require an account policy`() = runTest {
        val configuration = AiProviderConfiguration(AiProviderId("fake"), AiModelId("model"))
        val settings = AiSettings(
            enabled = true,
            providerConfiguration = configuration,
        )

        val result = assertIs<AiRequestDecision.Allowed>(
            createPolicy(settings).evaluateConnectionTest(),
        )

        assertEquals(configuration, result.providerConfiguration)
    }

    @Test
    fun `connection test uses only fixed synthetic classification data`() = runTest {
        val settings = AiSettings(
            enabled = true,
            providerConfiguration = AiProviderConfiguration(AiProviderId("fake"), AiModelId("model")),
        )

        val result = assertIs<AiRequestDecision.Allowed>(
            createPolicy(settings).evaluateConnectionTest(),
        )
        val input = assertIs<AiRequest.Classification>(result.request).input

        assertEquals("newsletter@example.com", input.sender)
        assertEquals("September product news", input.subject)
        assertEquals("Discover our latest product updates and new features.", input.preview)
        assertEquals(null, input.fullMessage)
        assertEquals(emptySet(), input.existingCategories)
    }

    @Test
    fun `unknown account remains denied for normal requests`() = runTest {
        val testSubject = createPolicy(enabledSettings(AiAccountPolicy(enabled = true)))

        val result = testSubject.evaluate("unknown-account", request())

        assertEquals(AiRequestDecision.Denied(AiError.AccountNotAllowed), result)
    }

    @Test
    fun `connection test executor sends the policy request through the provider registry`() = runTest {
        val provider = FakeProvider(setOf(AiCapability.CLASSIFICATION))
        val registry = FakeProviderRegistry(provider)
        val settings = AiSettings(
            enabled = true,
            providerConfiguration = AiProviderConfiguration(provider.id, AiModelId("model")),
        )
        val policy = DefaultAiRequestPolicy(
            settingsRepository = InMemoryAiSettingsRepository(settings),
            providerRegistry = registry,
        )
        val testSubject = DefaultAiRequestExecutor(
            requestPolicy = policy,
            providerRegistry = registry,
        )

        testSubject.testConnection()

        val input = assertIs<AiRequest.Classification>(provider.executedRequest).input
        assertEquals("newsletter@example.com", input.sender)
        assertEquals("September product news", input.subject)
        assertEquals("Discover our latest product updates and new features.", input.preview)
    }

    private fun createPolicy(
        settings: AiSettings,
        provider: FakeProvider? = FakeProvider(setOf(AiCapability.CLASSIFICATION)),
    ): DefaultAiRequestPolicy = DefaultAiRequestPolicy(
        settingsRepository = InMemoryAiSettingsRepository(settings),
        providerRegistry = FakeProviderRegistry(provider),
    )

    private fun enabledSettings(policy: AiAccountPolicy): AiSettings = AiSettings(
        enabled = true,
        providerConfiguration = AiProviderConfiguration(AiProviderId("fake"), AiModelId("model")),
        accountPolicies = mapOf(ACCOUNT_ID to policy),
    )

    private fun request() = AiRequest.Classification(
        AiClassificationInput(
            sender = "sender",
            subject = "subject",
            preview = "preview".repeat(1_000),
            fullMessage = "full message",
        ),
    )

    private fun summarizationRequest() = AiRequest.Summarization(
        AiSummarizationInput(
            sender = "sender",
            subject = "subject",
            preview = "preview".repeat(1_000),
            content = "content".repeat(20_000),
        ),
    )

    private fun writingRequest(sourceContent: String?, draftText: String?) = AiRequest.Writing(
        AiWritingInput(
            operation = AiWritingOperation.REPLY,
            subject = "subject",
            sourceContent = sourceContent,
            draftText = draftText,
        ),
    )

    private class InMemoryAiSettingsRepository(settings: AiSettings) : AiSettingsRepository {
        private val state = MutableStateFlow(settings)
        override val settings: Flow<AiSettings> = state

        override suspend fun updateGlobal(enabled: Boolean, providerConfiguration: AiProviderConfiguration?) = Unit

        override suspend fun updateAccountPolicy(accountId: String, policy: AiAccountPolicy) = Unit
    }

    private class FakeProviderRegistry(private val provider: FakeProvider?) : AiProviderRegistry {
        override val availability = AiProviderAvailability(
            enabled = provider != null,
            providerId = provider?.id,
            capabilities = provider?.capabilities.orEmpty(),
        )

        override fun providerFor(capability: AiCapability): AiProvider? =
            provider?.takeIf { it.supports(capability) }

        override fun providerFor(providerId: AiProviderId): AiProvider? =
            provider?.takeIf { it.id == providerId }
    }

    private class FakeProvider(
        override val capabilities: Set<AiCapability>,
    ) : AiProvider {
        override val id = AiProviderId("fake")
        override val modelId = AiModelId("model")
        var executedRequest: AiRequest? = null

        override suspend fun execute(request: AiRequest): AiResult {
            executedRequest = request
            return AiResult.Failure(AiError.Unknown)
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
