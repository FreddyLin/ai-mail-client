package net.thunderbird.feature.ai.internal

import net.thunderbird.core.configstore.ConfigKey

internal object AiSettingsKeys {
    val ENABLED = ConfigKey.BooleanKey("enabled")
    val PROVIDER_ID = ConfigKey.StringKey("provider_id")
    val MODEL_ID = ConfigKey.StringKey("model_id")
    val ACCOUNT_POLICIES = ConfigKey.StringKey("account_policies")
}
