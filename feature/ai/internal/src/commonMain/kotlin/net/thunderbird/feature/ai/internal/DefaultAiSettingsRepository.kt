package net.thunderbird.feature.ai.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.thunderbird.core.configstore.BaseConfigStore
import net.thunderbird.core.configstore.ConfigId
import net.thunderbird.core.configstore.backend.ConfigBackendProvider
import net.thunderbird.feature.ai.api.AiAccountPolicy
import net.thunderbird.feature.ai.api.AiProviderConfiguration
import net.thunderbird.feature.ai.api.AiSettings
import net.thunderbird.feature.ai.api.AiSettingsRepository

internal class DefaultAiSettingsRepository(
    id: ConfigId,
    provider: ConfigBackendProvider,
    scope: CoroutineScope,
) : BaseConfigStore<AiSettings>(
    provider = provider,
    definition = AiSettingsDefinition(id),
), AiSettingsRepository {
    override val settings: StateFlow<AiSettings> = config.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AiSettings(),
    )

    override suspend fun updateGlobal(
        enabled: Boolean,
        providerConfiguration: AiProviderConfiguration?,
    ) {
        update { current ->
            (current ?: AiSettings()).copy(
                enabled = enabled,
                providerConfiguration = providerConfiguration,
            )
        }
    }

    override suspend fun updateAccountPolicy(accountId: String, policy: AiAccountPolicy) {
        update { current ->
            val settings = current ?: AiSettings()
            settings.copy(accountPolicies = settings.accountPolicies + (accountId to policy))
        }
    }
}
