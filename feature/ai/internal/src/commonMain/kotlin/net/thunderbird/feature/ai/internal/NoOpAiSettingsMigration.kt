package net.thunderbird.feature.ai.internal

import net.thunderbird.core.configstore.Config
import net.thunderbird.core.configstore.ConfigMigration
import net.thunderbird.core.configstore.ConfigMigrationResult

internal class NoOpAiSettingsMigration : ConfigMigration {
    override suspend fun migrate(
        currentVersion: Int,
        newVersion: Int,
        current: Config,
    ): ConfigMigrationResult = ConfigMigrationResult.NoOp
}
