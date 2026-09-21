package net.thunderbird.feature.ai.api

import kotlinx.datetime.Instant

@JvmInline
value class AiProviderId(val value: String)

@JvmInline
value class AiModelId(val value: String)

enum class AiCapability {
    CLASSIFICATION,
    SUMMARIZATION,
    WRITING,
    TRANSLATION,
    EXTRACTION,
    SEARCH,
}

enum class AiClassificationCategory {
    IMPORTANT,
    ACTION,
    INVOICE,
    ORDER,
    NEWSLETTER,
}

data class AiProviderAvailability(
    val enabled: Boolean,
    val providerId: AiProviderId?,
    val capabilities: Set<AiCapability>,
) {
    fun supports(capability: AiCapability): Boolean = enabled && capability in capabilities
}

interface AiProviderRegistry {
    val availability: AiProviderAvailability

    fun providerFor(capability: AiCapability): AiProvider?

    fun providerFor(providerId: AiProviderId): AiProvider?

    fun providerFor(providerId: AiProviderId, capability: AiCapability): AiProvider? =
        providerFor(providerId)?.takeIf { it.supports(capability) }

    suspend fun execute(request: AiRequest): AiResult {
        val provider = providerFor(request.capability)
            ?: return AiResult.Failure(AiError.ProviderNotConfigured)

        if (!provider.supports(request.capability)) {
            return AiResult.Failure(AiError.UnsupportedCapability(request.capability))
        }

        return provider.execute(request)
    }

    suspend fun execute(providerId: AiProviderId, request: AiRequest): AiResult {
        val provider = providerFor(providerId, request.capability)
            ?: return AiResult.Failure(AiError.ProviderNotConfigured)

        if (!provider.supports(request.capability)) {
            return AiResult.Failure(AiError.UnsupportedCapability(request.capability))
        }

        return provider.execute(request)
    }
}

interface AiProvider {
    val id: AiProviderId
    val modelId: AiModelId?
    val capabilities: Set<AiCapability>

    suspend fun execute(request: AiRequest): AiResult

    fun supports(capability: AiCapability): Boolean = capability in capabilities
}

sealed interface AiRequest {
    val capability: AiCapability

    data class Classification(
        val input: AiClassificationInput,
    ) : AiRequest {
        override val capability: AiCapability = AiCapability.CLASSIFICATION
    }

    data class Summarization(
        val input: AiSummarizationInput,
    ) : AiRequest {
        override val capability: AiCapability = AiCapability.SUMMARIZATION
    }
}

data class AiClassificationInput(
    val sender: String? = null,
    val subject: String? = null,
    val preview: String? = null,
    val fullMessage: String? = null,
    val existingCategories: Set<AiClassificationCategory> = emptySet(),
)

data class AiSummarizationInput(
    val sender: String? = null,
    val subject: String? = null,
    val preview: String? = null,
    val content: String? = null,
)

sealed interface AiResult {
    data class Classification(
        val output: AiClassificationResult,
    ) : AiResult

    data class Summarization(
        val output: AiSummarizationResult,
    ) : AiResult

    data class Failure(
        val error: AiError,
    ) : AiResult
}

data class AiClassificationResult(
    val categories: Set<AiClassificationCategory>,
    val confidence: Double? = null,
    val metadata: AiResultMetadata,
)

data class AiSummarizationResult(
    val summary: String,
    val metadata: AiResultMetadata,
)

data class AiResultMetadata(
    val providerId: AiProviderId,
    val modelId: AiModelId?,
    val completedAt: Instant,
)

sealed interface AiError {
    data object Disabled : AiError
    data object AccountNotAllowed : AiError
    data object ProviderNotConfigured : AiError
    data object Authentication : AiError
    data object Network : AiError
    data object RateLimited : AiError
    data object InvalidResponse : AiError
    data object InsufficientDataAccess : AiError
    data class UnsupportedCapability(val capability: AiCapability) : AiError
    data object Cancelled : AiError
    data object Unknown : AiError
}
