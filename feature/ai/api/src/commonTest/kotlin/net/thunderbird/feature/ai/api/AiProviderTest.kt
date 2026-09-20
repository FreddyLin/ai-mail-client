package net.thunderbird.feature.ai.api

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiProviderTest {
    @Test
    fun `provider exposes supported capabilities`() {
        val testSubject = FakeAiProvider(setOf(AiCapability.CLASSIFICATION))

        assertTrue(testSubject.supports(AiCapability.CLASSIFICATION))
    }

    @Test
    fun `unsupported capability is represented by provider-independent error`() = runTest {
        val testSubject = FakeAiProviderRegistry(FakeAiProvider(emptySet()))

        val result = testSubject.execute(
            AiRequest.Classification(AiClassificationInput(subject = "Test")),
        )

        assertEquals(
            AiResult.Failure(AiError.UnsupportedCapability(AiCapability.CLASSIFICATION)),
            result,
        )
    }

    @Test
    fun `classification request returns provider-independent result`() = runTest {
        val testSubject = FakeAiProviderRegistry(FakeAiProvider(setOf(AiCapability.CLASSIFICATION)))

        val result = testSubject.execute(
            AiRequest.Classification(
                AiClassificationInput(
                    sender = "sender@example.com",
                    subject = "Order confirmation",
                    preview = "Your order has shipped",
                ),
            ),
        )

        assertEquals(
            AiResult.Classification(
                AiClassificationResult(
                    categories = setOf(AiClassificationCategory.ORDER),
                    confidence = 0.9,
                    metadata = AiResultMetadata(
                        providerId = AiProviderId("fake"),
                        modelId = AiModelId("fake-model"),
                        completedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun `missing provider is represented by provider-independent error`() = runTest {
        val testSubject = FakeAiProviderRegistry(provider = null)

        val result = testSubject.execute(
            AiRequest.Classification(AiClassificationInput(subject = "Test")),
        )

        assertEquals(AiResult.Failure(AiError.ProviderNotConfigured), result)
    }

    @Test
    fun `credentials are not part of an AI request`() {
        val credential = AiCredential("secret-value")
        val request = AiRequest.Classification(AiClassificationInput(subject = "Subject"))

        assertFalse(request.toString().contains(credential.secret))
        assertEquals("AiCredential(redacted)", credential.toString())
    }

    private class FakeAiProviderRegistry(
        private val provider: FakeAiProvider?,
    ) : AiProviderRegistry {
        override val availability = AiProviderAvailability(
            enabled = provider != null,
            providerId = provider?.id,
            capabilities = provider?.capabilities.orEmpty(),
        )

        override fun providerFor(capability: AiCapability): AiProvider? = provider

        override fun providerFor(providerId: AiProviderId): AiProvider? =
            provider?.takeIf { it.id == providerId }
    }

    private class FakeAiProvider(
        override val capabilities: Set<AiCapability>,
    ) : AiProvider {
        override val id = AiProviderId("fake")
        override val modelId = AiModelId("fake-model")

        override suspend fun execute(request: AiRequest): AiResult {
            if (!supports(request.capability)) {
                return AiResult.Failure(AiError.UnsupportedCapability(request.capability))
            }

            return AiResult.Classification(
                AiClassificationResult(
                    categories = setOf(AiClassificationCategory.ORDER),
                    confidence = 0.9,
                    metadata = AiResultMetadata(
                        providerId = id,
                        modelId = modelId,
                        completedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    ),
                ),
            )
        }
    }
}
