package net.thunderbird.android.ai.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.ai.api.AiAccountPolicy
import net.thunderbird.feature.ai.api.AiCredential
import net.thunderbird.feature.ai.api.AiCredentialOperationResult
import net.thunderbird.feature.ai.api.AiCredentialStatus
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiDataAccessLevel
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProviderConfiguration
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiSettingsRepository

internal data class AiSettingsUiState(
    val aiEnabled: Boolean = false,
    val openAiConfigured: Boolean = false,
    val credentialStatus: AiCredentialStatus = AiCredentialStatus.Missing,
    val credentialOperationInProgress: Boolean = false,
    val credentialOperationFailed: Boolean = false,
    val connectionTestInProgress: Boolean = false,
    val connectionTestResult: AiConnectionTestResult? = null,
    val accounts: List<AiAccountUiState> = emptyList(),
)

internal data class AiAccountUiState(
    val accountId: String,
    val displayName: String,
    val enabled: Boolean,
    val dataAccessLevel: AiDataAccessLevel,
)

internal sealed interface AiConnectionTestResult {
    data object Success : AiConnectionTestResult
    data class Failure(val error: AiError) : AiConnectionTestResult
}

internal sealed interface AiSettingsEffect {
    data object CredentialSaved : AiSettingsEffect
}

internal class AiSettingsViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val credentialStore: AiCredentialStore,
    private val requestExecutor: AiRequestExecutor,
    private val accountManager: LegacyAccountDtoManager,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AiSettingsUiState())
    val state = mutableState.asStateFlow()

    private val effectsChannel = Channel<AiSettingsEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(accountManager.getAccountsFlow(), settingsRepository.settings) {
                    accounts,
                    settings,
                ->
                settings to accounts.map { account ->
                    val policy = settings.accountPolicy(account.uuid)
                    AiAccountUiState(
                        accountId = account.uuid,
                        displayName = account.displayName,
                        enabled = policy.enabled,
                        dataAccessLevel = policy.dataAccessLevel,
                    )
                }
            }.collect { (settings, accounts) ->
                mutableState.update {
                    it.copy(
                        aiEnabled = settings.enabled,
                        openAiConfigured = settings.providerConfiguration == openAiConfiguration,
                        accounts = accounts,
                    )
                }
            }
        }
        refreshCredentialStatus()
    }

    fun setAccountAiEnabled(accountId: String, enabled: Boolean) {
        updateAccountPolicy(accountId) { it.copy(enabled = enabled) }
    }

    fun setAccountDataAccessLevel(accountId: String, dataAccessLevel: AiDataAccessLevel) {
        updateAccountPolicy(accountId) { it.copy(dataAccessLevel = dataAccessLevel) }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val configuration = settings.providerConfiguration ?: openAiConfiguration
            settingsRepository.updateGlobal(enabled = enabled, providerConfiguration = configuration)
        }
    }

    fun saveCredential(secret: String) {
        if (secret.isBlank() || mutableState.value.credentialOperationInProgress) return

        viewModelScope.launch {
            mutableState.update { it.copy(credentialOperationInProgress = true, credentialOperationFailed = false) }
            ensureOpenAiConfiguration()
            val result = withContext(ioContext) {
                credentialStore.write(openAiProviderId, AiCredential(secret))
            }
            mutableState.update { it.copy(credentialOperationInProgress = false) }

            if (result == AiCredentialOperationResult.Success) {
                refreshCredentialStatus()
                effectsChannel.send(AiSettingsEffect.CredentialSaved)
            } else {
                mutableState.update { it.copy(credentialOperationFailed = true) }
            }
        }
    }

    fun deleteCredential() {
        if (mutableState.value.credentialOperationInProgress) return

        viewModelScope.launch {
            mutableState.update { it.copy(credentialOperationInProgress = true, credentialOperationFailed = false) }
            val result = withContext(ioContext) {
                credentialStore.delete(openAiProviderId)
            }
            mutableState.update { it.copy(credentialOperationInProgress = false) }

            if (result == AiCredentialOperationResult.Success) {
                refreshCredentialStatus()
            } else {
                mutableState.update { it.copy(credentialOperationFailed = true) }
            }
        }
    }

    fun testConnection() {
        if (mutableState.value.connectionTestInProgress) return

        viewModelScope.launch {
            mutableState.update { it.copy(connectionTestInProgress = true, connectionTestResult = null) }
            val result = requestExecutor.testConnection()
            mutableState.update {
                it.copy(
                    connectionTestInProgress = false,
                    connectionTestResult = when (result) {
                        is AiResult.Classification -> AiConnectionTestResult.Success
                        is AiResult.Failure -> AiConnectionTestResult.Failure(result.error)
                    },
                )
            }
        }
    }

    private fun refreshCredentialStatus() {
        viewModelScope.launch {
            val status = withContext(ioContext) {
                credentialStore.status(openAiProviderId)
            }
            mutableState.update { it.copy(credentialStatus = status) }
        }
    }

    private fun updateAccountPolicy(
        accountId: String,
        update: (AiAccountPolicy) -> AiAccountPolicy,
    ) {
        viewModelScope.launch {
            val currentPolicy = settingsRepository.settings.first().accountPolicy(accountId)
            settingsRepository.updateAccountPolicy(accountId, update(currentPolicy))
        }
    }

    private suspend fun ensureOpenAiConfiguration() {
        val settings = settingsRepository.settings.first()
        if (settings.providerConfiguration == null) {
            settingsRepository.updateGlobal(
                enabled = settings.enabled,
                providerConfiguration = openAiConfiguration,
            )
        }
    }

    private companion object {
        val openAiProviderId = AiProviderId("openai")
        val openAiConfiguration = AiProviderConfiguration(
            providerId = openAiProviderId,
            modelId = AiModelId("gpt-5.6-luna"),
        )
    }
}
