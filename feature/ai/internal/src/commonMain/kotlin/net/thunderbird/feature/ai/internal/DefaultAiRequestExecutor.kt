package net.thunderbird.feature.ai.internal

import net.thunderbird.feature.ai.api.AiProviderRegistry
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestDecision
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiRequestPolicy
import net.thunderbird.feature.ai.api.AiResult

internal class DefaultAiRequestExecutor(
    private val requestPolicy: AiRequestPolicy,
    private val providerRegistry: AiProviderRegistry,
) : AiRequestExecutor {
    override suspend fun execute(accountId: String, request: AiRequest): AiResult {
        return when (val decision = requestPolicy.evaluate(accountId, request)) {
            is AiRequestDecision.Denied -> AiResult.Failure(decision.error)
            is AiRequestDecision.Allowed -> execute(decision)
        }
    }

    override suspend fun testConnection(): AiResult {
        return when (val decision = requestPolicy.evaluateConnectionTest()) {
            is AiRequestDecision.Denied -> AiResult.Failure(decision.error)
            is AiRequestDecision.Allowed -> execute(decision)
        }
    }

    private suspend fun execute(decision: AiRequestDecision.Allowed): AiResult = providerRegistry.execute(
        providerId = decision.providerConfiguration.providerId,
        request = decision.request,
    )
}
