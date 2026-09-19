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

        assertEquals("sender", (result.request as AiRequest.Classification).input.sender)
        assertEquals("subject", result.request.input.subject)
        assertEquals(null, result.request.input.preview)
        assertEquals(null, result.request.input.fullMessage)
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
    fun `full message access preserves explicit input`() = runTest {
        val testSubject = createPolicy(enabledSettings(AiAccountPolicy(true, AiDataAccessLevel.FULL_MESSAGE)))

        val result = assertIs<AiRequestDecision.Allowed>(testSubject.evaluate(ACCOUNT_ID, request()))
        val input = (result.request as AiRequest.Classification).input

        assertEquals("preview", input.preview)
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

    private fun createPolicy(
        settings: AiSettings,
        provider: FakeProvider = FakeProvider(setOf(AiCapability.CLASSIFICATION)),
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

    private class InMemoryAiSettingsRepository(settings: AiSettings) : AiSettingsRepository {
        private val state = MutableStateFlow(settings)
        override val settings: Flow<AiSettings> = state

        override suspend fun updateGlobal(enabled: Boolean, providerConfiguration: AiProviderConfiguration?) = Unit

        override suspend fun updateAccountPolicy(accountId: String, policy: AiAccountPolicy) = Unit
    }

    private class FakeProviderRegistry(private val provider: FakeProvider) : AiProviderRegistry {
        override val availability = AiProviderAvailability(
            enabled = true,
            providerId = provider.id,
            capabilities = provider.capabilities,
        )

        override fun providerFor(capability: AiCapability): AiProvider? = provider
    }

    private class FakeProvider(
        override val capabilities: Set<AiCapability>,
    ) : AiProvider {
        override val id = AiProviderId("fake")
        override val modelId = AiModelId("model")

        override suspend fun execute(request: AiRequest): AiResult = error("Not needed for policy tests")
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
