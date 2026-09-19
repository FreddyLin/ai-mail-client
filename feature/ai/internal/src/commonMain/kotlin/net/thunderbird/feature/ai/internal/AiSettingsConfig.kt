package net.thunderbird.feature.ai.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.thunderbird.core.configstore.Config
import net.thunderbird.core.configstore.ConfigMapper
import net.thunderbird.feature.ai.api.AiAccountPolicy
import net.thunderbird.feature.ai.api.AiDataAccessLevel
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProviderConfiguration
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiSettings

@Serializable
private data class StoredAccountPolicy(
    val accountId: String,
    val enabled: Boolean,
    val dataAccessLevel: String,
)

internal class AiSettingsMapper : ConfigMapper<AiSettings> {
    private val json = Json { ignoreUnknownKeys = true }

    override fun toConfig(obj: AiSettings): Config = Config().apply {
        this[AiSettingsKeys.ENABLED] = obj.enabled
        obj.providerConfiguration?.providerId?.value?.let { providerId ->
            this[AiSettingsKeys.PROVIDER_ID] = providerId
        }
        obj.providerConfiguration?.modelId?.value?.let { modelId ->
            this[AiSettingsKeys.MODEL_ID] = modelId
        }
        this[AiSettingsKeys.ACCOUNT_POLICIES] = json.encodeToString(
            obj.accountPolicies.map { (accountId, policy) ->
                StoredAccountPolicy(
                    accountId = accountId,
                    enabled = policy.enabled,
                    dataAccessLevel = policy.dataAccessLevel.name,
                )
            },
        )
    }

    override fun fromConfig(config: Config): AiSettings {
        val providerId = config[AiSettingsKeys.PROVIDER_ID]
        val modelId = config[AiSettingsKeys.MODEL_ID]
        val providerConfiguration = if (providerId != null && modelId != null) {
            AiProviderConfiguration(
                providerId = AiProviderId(providerId),
                modelId = AiModelId(modelId),
            )
        } else {
            null
        }

        val accountPolicies = config[AiSettingsKeys.ACCOUNT_POLICIES]
            ?.let { encoded -> runCatching { json.decodeFromString<List<StoredAccountPolicy>>(encoded) }.getOrNull() }
            .orEmpty()
            .associate { stored ->
                stored.accountId to AiAccountPolicy(
                    enabled = stored.enabled,
                    dataAccessLevel = stored.dataAccessLevel
                        .let { runCatching { AiDataAccessLevel.valueOf(it) }.getOrDefault(AiDataAccessLevel.METADATA_ONLY) },
                )
            }

        return AiSettings(
            enabled = config[AiSettingsKeys.ENABLED] ?: false,
            providerConfiguration = providerConfiguration,
            accountPolicies = accountPolicies,
        )
    }
}
