package net.thunderbird.android.ai.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.ai.api.AiAccountPolicy
import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiCredentialOperationResult
import net.thunderbird.feature.ai.api.AiCredentialStatus
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProviderConfiguration
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiResultMetadata
import net.thunderbird.feature.ai.api.AiSettings
import net.thunderbird.feature.ai.api.AiSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val mainDispatcher = MainDispatcherHelper(dispatcher)

    @BeforeTest
    fun setUp() {
        mainDispatcher.setUp()
    }

    @AfterTest
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    @Test
    fun `loads global AI setting and does not start a connection test`() {
        val executor = FakeAiRequestExecutor()
        val testSubject = createTestSubject(
            settings = AiSettings(enabled = true, providerConfiguration = openAiConfiguration),
            requestExecutor = executor,
        )

        assertThat(testSubject.state.value.aiEnabled).isTrue()
        assertThat(testSubject.state.value.openAiConfigured).isTrue()
        assertThat(executor.testConnectionCalls).isEqualTo(0)
    }

    @Test
    fun `enabling AI stores the OpenAI configuration when none exists`() {
        val repository = FakeAiSettingsRepository()
        val testSubject = createTestSubject(settingsRepository = repository)

        testSubject.setAiEnabled(true)

        assertThat(repository.current.value.enabled).isTrue()
        assertThat(repository.current.value.providerConfiguration).isEqualTo(openAiConfiguration)
    }

    @Test
    fun `credential status is exposed without exposing a credential`() {
        val credentialStore = FakeAiCredentialStore(status = AiCredentialStatus.Unavailable)
        val testSubject = createTestSubject(credentialStore = credentialStore)

        assertThat(testSubject.state.value.credentialStatus).isEqualTo(AiCredentialStatus.Unavailable)
        assertThat(testSubject.state.value.toString().contains("secret")).isFalse()
    }

    @Test
    fun `successful credential save updates status and emits saved effect`() = runTest {
        val credentialStore = FakeAiCredentialStore()
        val testSubject = createTestSubject(credentialStore = credentialStore)

        testSubject.saveCredential("test-secret")

        assertThat(credentialStore.lastWrittenProviderId).isEqualTo(openAiProviderId)
        assertThat(credentialStore.lastWrittenCredential?.secret).isEqualTo("test-secret")
        assertThat(testSubject.state.value.credentialStatus).isEqualTo(AiCredentialStatus.Available)
        assertThat(testSubject.effects.first()).isEqualTo(AiSettingsEffect.CredentialSaved)
    }

    @Test
    fun `failed credential save does not report success`() {
        val credentialStore = FakeAiCredentialStore(writeResult = AiCredentialOperationResult.Failure)
        val testSubject = createTestSubject(credentialStore = credentialStore)

        testSubject.saveCredential("test-secret")

        assertThat(testSubject.state.value.credentialOperationFailed).isTrue()
        assertThat(testSubject.state.value.credentialStatus).isEqualTo(AiCredentialStatus.Missing)
    }

    @Test
    fun `successful credential delete updates status to missing`() {
        val credentialStore = FakeAiCredentialStore(status = AiCredentialStatus.Available)
        val testSubject = createTestSubject(credentialStore = credentialStore)

        testSubject.deleteCredential()

        assertThat(credentialStore.lastDeletedProviderId).isEqualTo(openAiProviderId)
        assertThat(testSubject.state.value.credentialStatus).isEqualTo(AiCredentialStatus.Missing)
    }

    @Test
    fun `failed credential delete reports an error`() {
        val credentialStore = FakeAiCredentialStore(
            status = AiCredentialStatus.Available,
            deleteResult = AiCredentialOperationResult.Failure,
        )
        val testSubject = createTestSubject(credentialStore = credentialStore)

        testSubject.deleteCredential()

        assertThat(testSubject.state.value.credentialOperationFailed).isTrue()
        assertThat(testSubject.state.value.credentialStatus).isEqualTo(AiCredentialStatus.Available)
    }

    @Test
    fun `connection test maps authentication failures`() {
        val testSubject = createTestSubject(
            requestExecutor = FakeAiRequestExecutor(result = AiResult.Failure(AiError.Authentication)),
        )

        testSubject.testConnection()

        assertThat(testSubject.state.value.connectionTestResult)
            .isEqualTo(AiConnectionTestResult.Failure(AiError.Authentication))
    }

    @Test
    fun `connection test preserves expected provider failures`() {
        val errors = listOf(
            AiError.RateLimited,
            AiError.Network,
            AiError.InvalidResponse,
        )

        errors.forEach { error ->
            val testSubject = createTestSubject(
                requestExecutor = FakeAiRequestExecutor(result = AiResult.Failure(error)),
            )

            testSubject.testConnection()

            assertThat(testSubject.state.value.connectionTestResult)
                .isEqualTo(AiConnectionTestResult.Failure(error))
        }
    }

    @Test
    fun `connection test maps a classification response to success`() {
        val testSubject = createTestSubject(
            requestExecutor = FakeAiRequestExecutor(result = classificationResult()),
        )

        testSubject.testConnection()

        assertThat(testSubject.state.value.connectionTestResult).isEqualTo(AiConnectionTestResult.Success)
    }

    @Test
    fun `second connection test is ignored while first test is running`() {
        val executor = FakeAiRequestExecutor(suspendResult = CompletableDeferred())
        val testSubject = createTestSubject(requestExecutor = executor)

        testSubject.testConnection()
        testSubject.testConnection()

        assertThat(executor.testConnectionCalls).isEqualTo(1)
        assertThat(testSubject.state.value.connectionTestInProgress).isTrue()
        executor.suspendResult?.complete(classificationResult())
        assertThat(testSubject.state.value.connectionTestInProgress).isFalse()
    }

    private fun createTestSubject(
        settings: AiSettings = AiSettings(),
        settingsRepository: FakeAiSettingsRepository = FakeAiSettingsRepository(settings),
        credentialStore: FakeAiCredentialStore = FakeAiCredentialStore(),
        requestExecutor: FakeAiRequestExecutor = FakeAiRequestExecutor(),
    ): AiSettingsViewModel {
        return AiSettingsViewModel(
            settingsRepository = settingsRepository,
            credentialStore = credentialStore,
            requestExecutor = requestExecutor,
            ioContext = dispatcher,
        )
    }
}

private class FakeAiSettingsRepository(initial: AiSettings = AiSettings()) : AiSettingsRepository {
    val current = MutableStateFlow(initial)

    override val settings: Flow<AiSettings> = current

    override suspend fun updateGlobal(enabled: Boolean, providerConfiguration: AiProviderConfiguration?) {
        current.value = current.value.copy(enabled = enabled, providerConfiguration = providerConfiguration)
    }

    override suspend fun updateAccountPolicy(accountId: String, policy: AiAccountPolicy) = Unit
}

private class FakeAiCredentialStore(
    var status: AiCredentialStatus = AiCredentialStatus.Missing,
    private val writeResult: AiCredentialOperationResult = AiCredentialOperationResult.Success,
    private val deleteResult: AiCredentialOperationResult = AiCredentialOperationResult.Success,
) : AiCredentialStore {
    var lastWrittenProviderId: AiProviderId? = null
    var lastWrittenCredential: AiCredential? = null
    var lastDeletedProviderId: AiProviderId? = null

    override suspend fun read(providerId: AiProviderId): AiCredential? = null

    override suspend fun status(providerId: AiProviderId): AiCredentialStatus = status

    override suspend fun write(providerId: AiProviderId, credential: AiCredential): AiCredentialOperationResult {
        lastWrittenProviderId = providerId
        lastWrittenCredential = credential
        if (writeResult == AiCredentialOperationResult.Success) {
            status = AiCredentialStatus.Available
        }
        return writeResult
    }

    override suspend fun delete(providerId: AiProviderId): AiCredentialOperationResult {
        lastDeletedProviderId = providerId
        if (deleteResult == AiCredentialOperationResult.Success) {
            status = AiCredentialStatus.Missing
        }
        return deleteResult
    }
}

private class FakeAiRequestExecutor(
    private val result: AiResult = classificationResult(),
    val suspendResult: CompletableDeferred<AiResult>? = null,
) : AiRequestExecutor {
    var testConnectionCalls = 0

    override suspend fun execute(
        accountId: String,
        request: net.thunderbird.feature.ai.api.AiRequest,
    ): AiResult = result

    override suspend fun testConnection(): AiResult {
        testConnectionCalls++
        return suspendResult?.await() ?: result
    }
}

private fun classificationResult(): AiResult = AiResult.Classification(
    output = net.thunderbird.feature.ai.api.AiClassificationResult(
        categories = emptySet(),
        metadata = AiResultMetadata(
            providerId = openAiProviderId,
            modelId = openAiConfiguration.modelId,
            completedAt = Clock.System.now(),
        ),
    ),
)

private val openAiProviderId = AiProviderId("openai")
private val openAiConfiguration = AiProviderConfiguration(openAiProviderId, AiModelId("gpt-5.6-luna"))
