package net.thunderbird.feature.ai.internal

import net.thunderbird.feature.ai.api.AiProviderRegistry
import org.koin.dsl.module

val featureAiModule = module {
    single<AiProviderRegistry> { UnconfiguredAiProviderRegistry() }
}
