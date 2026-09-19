package net.thunderbird.feature.ai.api

import kotlinx.coroutines.flow.Flow

enum class AiDataAccessLevel {
    METADATA_ONLY,
    PREVIEW,
    FULL_MESSAGE,
}

data class AiProviderConfiguration(
    val providerId: AiProviderId,
    val modelId: AiModelId,
)

data class AiAccountPolicy(
    val enabled: Boolean = false,
    val dataAccessLevel: AiDataAccessLevel = AiDataAccessLevel.METADATA_ONLY,
)

data class AiSettings(
    val enabled: Boolean = false,
    val providerConfiguration: AiProviderConfiguration? = null,
    val accountPolicies: Map<String, AiAccountPolicy> = emptyMap(),
) {
    fun accountPolicy(accountId: String): AiAccountPolicy = accountPolicies[accountId] ?: AiAccountPolicy()
}

interface AiSettingsRepository {
    val settings: Flow<AiSettings>

    suspend fun updateGlobal(
        enabled: Boolean,
        providerConfiguration: AiProviderConfiguration?,
    )

    suspend fun updateAccountPolicy(accountId: String, policy: AiAccountPolicy)
}
