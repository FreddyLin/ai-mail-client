package net.thunderbird.feature.ai.api

sealed interface AiRequestDecision {
    data class Allowed(
        val request: AiRequest,
        val providerConfiguration: AiProviderConfiguration,
    ) : AiRequestDecision

    data class Denied(val error: AiError) : AiRequestDecision
}

interface AiRequestPolicy {
    suspend fun evaluate(accountId: String, request: AiRequest): AiRequestDecision
}

interface AiRequestExecutor {
    suspend fun execute(accountId: String, request: AiRequest): AiResult
}
