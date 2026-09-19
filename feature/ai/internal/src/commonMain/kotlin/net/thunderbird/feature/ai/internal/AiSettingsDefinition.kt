package net.thunderbird.feature.ai.internal

import net.thunderbird.core.configstore.ConfigDefinition
import net.thunderbird.core.configstore.ConfigId
import net.thunderbird.core.configstore.ConfigKey
import net.thunderbird.core.configstore.ConfigMigration
import net.thunderbird.feature.ai.api.AiSettings

internal class AiSettingsDefinition(
    override val id: ConfigId,
) : ConfigDefinition<AiSettings> {
    override val version: Int = 1
    override val mapper = AiSettingsMapper()
    override val defaultValue = AiSettings()
    override val keys: List<ConfigKey<*>> = listOf(
        AiSettingsKeys.ENABLED,
        AiSettingsKeys.PROVIDER_ID,
        AiSettingsKeys.MODEL_ID,
        AiSettingsKeys.ACCOUNT_POLICIES,
    )
    override val migration: ConfigMigration = NoOpAiSettingsMigration()
}
