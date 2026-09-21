package net.thunderbird.feature.ai.internal

import kotlinx.coroutines.flow.first
import net.thunderbird.feature.ai.api.AiClassificationInput
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
        val provider = providerRegistry.providerFor(providerConfiguration.providerId)
            ?: return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        if (!provider.supports(request.capability)) {
            return AiRequestDecision.Denied(AiError.UnsupportedCapability(request.capability))
        }

        val limitedRequest = request.limitData(accountPolicy.dataAccessLevel)
            ?: return AiRequestDecision.Denied(AiError.InsufficientDataAccess)

        return AiRequestDecision.Allowed(
            request = limitedRequest,
            providerConfiguration = providerConfiguration,
        )
    }

    override suspend fun evaluateConnectionTest(): AiRequestDecision {
        val settings = settingsRepository.settings.first()
        if (!settings.enabled) {
            return AiRequestDecision.Denied(AiError.Disabled)
        }

        val providerConfiguration = settings.providerConfiguration
            ?.takeIf { it.modelId.value.isNotBlank() }
            ?: return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        if (!providerRegistry.availability.enabled) {
            return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        }
        val provider = providerRegistry.providerFor(providerConfiguration.providerId)
            ?: return AiRequestDecision.Denied(AiError.ProviderNotConfigured)
        if (!provider.supports(CONNECTION_TEST_REQUEST.capability)) {
            return AiRequestDecision.Denied(AiError.UnsupportedCapability(CONNECTION_TEST_REQUEST.capability))
        }

        return AiRequestDecision.Allowed(
            request = CONNECTION_TEST_REQUEST,
            providerConfiguration = providerConfiguration,
        )
    }

    private fun AiRequest.limitData(dataAccessLevel: AiDataAccessLevel): AiRequest? = when (this) {
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

        is AiRequest.Summarization -> when (dataAccessLevel) {
            AiDataAccessLevel.METADATA_ONLY -> null
            AiDataAccessLevel.PREVIEW -> input.preview
                ?.takeAtMost(MAX_PREVIEW_LENGTH)
                ?.takeIf { it.isNotBlank() }
                ?.let { preview ->
                    copy(
                        input = input.copy(preview = preview, content = null),
                    )
                }

            AiDataAccessLevel.FULL_MESSAGE -> input.content
                ?.takeAtMost(MAX_SUMMARIZATION_CONTENT_LENGTH)
                ?.takeIf { it.isNotBlank() }
                ?.let { content ->
                    copy(
                        input = input.copy(content = content),
                    )
                }
        }
    }

    private fun String.takeAtMost(maxLength: Int): String {
        if (length <= maxLength) return this

        var endIndex = maxLength
        if (endIndex > 0 && endIndex < length &&
            this[endIndex - 1].isHighSurrogate() && this[endIndex].isLowSurrogate()
        ) {
            endIndex--
        }
        return substring(0, endIndex)
    }

    private companion object {
        const val MAX_PREVIEW_LENGTH = 2_000
        const val MAX_SUMMARIZATION_CONTENT_LENGTH = 32_000
        val CONNECTION_TEST_REQUEST = AiRequest.Classification(
            input = AiClassificationInput(
                sender = "newsletter@example.com",
                subject = "September product news",
                preview = "Discover our latest product updates and new features.",
            ),
        )
    }
}
