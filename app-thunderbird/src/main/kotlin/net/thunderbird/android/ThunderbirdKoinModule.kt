package net.thunderbird.android

import app.k9mail.feature.widget.shortcut.LauncherShortcutActivity
import com.fsck.k9.AppConfig
import com.fsck.k9.DefaultAppConfig
import com.fsck.k9.activity.MessageCompose
import net.thunderbird.android.ai.message.DefaultMessageReaderAiClassifier
import net.thunderbird.android.ai.settings.AiSettingsViewModel
import net.thunderbird.android.auth.TbOAuthConfigurationFactory
import net.thunderbird.android.dev.developmentModuleAdditions
import net.thunderbird.android.feature.featureModule
import net.thunderbird.android.featureflag.thunderbirdFeatureFlagModule
import net.thunderbird.android.provider.providerModule
import net.thunderbird.android.widget.provider.MessageListWidgetProvider
import net.thunderbird.android.widget.provider.UnreadWidgetProvider
import net.thunderbird.android.widget.widgetModule
import net.thunderbird.app.common.appCommonModule
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory
import net.thunderbird.feature.ai.internal.featureAiModule
import net.thunderbird.feature.ai.provider.openai.openAiProviderModule
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassifier
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    includes(thunderbirdFeatureFlagModule)
    includes(appCommonModule)
    includes(featureAiModule)
    includes(openAiProviderModule)

    includes(widgetModule)
    includes(featureModule)
    includes(providerModule)

    single(named("ClientInfoAppName")) { BuildConfig.CLIENT_INFO_APP_NAME }
    single(named("ClientInfoAppVersion")) { BuildConfig.VERSION_NAME }
    single<AppConfig> { appConfig }
    single<OAuthConfigurationFactory> { TbOAuthConfigurationFactory() }
    viewModel {
        AiSettingsViewModel(
            settingsRepository = get(),
            credentialStore = get(),
            requestExecutor = get(),
            accountManager = get(),
        )
    }
    single<MessageReaderAiClassifier> { DefaultMessageReaderAiClassifier(requestExecutor = get()) }

    developmentModuleAdditions()
}

val appConfig = DefaultAppConfig(
    componentsToDisable = listOf(
        MessageCompose::class.java,
        LauncherShortcutActivity::class.java,
        UnreadWidgetProvider::class.java,
        MessageListWidgetProvider::class.java,
    ),
)
