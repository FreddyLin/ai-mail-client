package net.thunderbird.feature.ai.provider.openai

import java.util.concurrent.TimeUnit
import net.thunderbird.feature.ai.api.AiProvider
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

val openAiProviderModule = module {
    single<OkHttpClient>(named("OpenAiHttpClient")) {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    single<AiProvider> {
        OpenAiProvider(
            httpClient = get(named("OpenAiHttpClient")),
            credentialStore = get(),
            settingsRepository = get(),
        )
    }
}
