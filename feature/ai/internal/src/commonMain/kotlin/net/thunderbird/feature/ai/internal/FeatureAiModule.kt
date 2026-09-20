package net.thunderbird.feature.ai.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.thunderbird.core.configstore.ConfigId
import net.thunderbird.core.configstore.backend.ConfigBackendProvider
import net.thunderbird.feature.ai.api.AiProviderRegistry
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiRequestPolicy
import net.thunderbird.feature.ai.api.AiSettingsRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal expect val platformFeatureAiCredentialStoreModule: Module

val featureAiModule = module {
    includes(platformFeatureAiCredentialStoreModule)
    single<AiProviderRegistry> { DefaultAiProviderRegistry(providers = getAll()) }
    single<CoroutineScope>(named("AiConfigStoreScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<AiSettingsRepository> {
        DefaultAiSettingsRepository(
            id = ConfigId(backend = "ai", feature = "settings"),
            provider = get<ConfigBackendProvider>(),
            scope = get(named("AiConfigStoreScope")),
        )
    }
    single<AiRequestPolicy> {
        DefaultAiRequestPolicy(
            settingsRepository = get(),
            providerRegistry = get(),
        )
    }
    single<AiRequestExecutor> {
        DefaultAiRequestExecutor(
            requestPolicy = get(),
            providerRegistry = get(),
        )
    }
}
