package net.thunderbird.feature.ai.internal

import net.thunderbird.feature.ai.api.AiCredentialStore
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformFeatureAiCredentialStoreModule: Module = module {
    single<AiCredentialStore> {
        AndroidKeystoreAiCredentialStore(context = androidApplication())
    }
}
