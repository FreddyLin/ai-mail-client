package net.thunderbird.feature.ai.internal

import kotlinx.coroutines.flow.first
import net.thunderbird.feature.ai.api.AiDataAccessLevel
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiProviderRegistry
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestDecision
import net.thunderbird.feature.ai.api.AiRequestPolicy
import net.thunderbird.feature.ai.api.AiSettingsRepository

internal class DefaultAiRequestPolicy(
    private val settingsRepository: AiSettingsRepository,
    private val providerRegistry: AiProviderRegistry,
) : AiRequestPolicy {
    override suspend fun evaluate(accountId: String, request: AiRequest): AiRequestDecision {
        val settings = settingsRepository.settings.first()
        if (!settings.enabled) {
            return AiRequestDecision.Denied(AiError.Disabled)
        }

        val accountPolicy = settings.accountPolicy(accountId)
        if (!accountPolicy.enabled) {
            return AiRequestDecision.Denied(AiError.AccountNotAllowed)
        }

        val providerConfiguration = settings.providerConfiguration
            ?: return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        if (!providerRegistry.availability.enabled) {
            return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        }
        val provider = providerRegistry.providerFor(providerConfiguration.providerId, request.capability)
            ?: return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        if (!provider.supports(request.capability)) {
            return AiRequestDecision.Denied(AiError.UnsupportedCapability(request.capability))
        }

        return AiRequestDecision.Allowed(
            request = request.limitData(accountPolicy.dataAccessLevel),
            providerConfiguration = providerConfiguration,
        )
    }

    private fun AiRequest.limitData(dataAccessLevel: AiDataAccessLevel): AiRequest = when (this) {
        is AiRequest.Classification -> copy(
            input = when (dataAccessLevel) {
                AiDataAccessLevel.METADATA_ONLY -> input.copy(
                    preview = null,
                    fullMessage = null,
                )

                AiDataAccessLevel.PREVIEW -> input.copy(
                    preview = input.preview?.take(MAX_PREVIEW_LENGTH),
                    fullMessage = null,
                )

                AiDataAccessLevel.FULL_MESSAGE -> input
            },
        )
    }

    private companion object {
        const val MAX_PREVIEW_LENGTH = 2_000
    }
}
