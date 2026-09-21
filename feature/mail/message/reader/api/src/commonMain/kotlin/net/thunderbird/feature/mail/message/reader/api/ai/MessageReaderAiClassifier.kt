package net.thunderbird.feature.mail.message.reader.api.ai

interface MessageReaderAiClassifier {
    suspend fun classify(
        accountId: String,
        input: MessageReaderAiClassificationInput,
    ): MessageReaderAiClassificationResult
}

data class MessageReaderAiClassificationInput(
    val sender: String?,
    val subject: String?,
    val preview: String?,
)

sealed interface MessageReaderAiClassificationResult {
    data object Loading : MessageReaderAiClassificationResult

    data class Success(
        val categories: Set<MessageReaderAiCategory>,
        val confidence: Double?,
    ) : MessageReaderAiClassificationResult

    data class Failure(
        val error: MessageReaderAiError,
    ) : MessageReaderAiClassificationResult
}

enum class MessageReaderAiCategory {
    IMPORTANT,
    ACTION,
    INVOICE,
    ORDER,
    NEWSLETTER,
}

enum class MessageReaderAiError {
    DISABLED,
    ACCOUNT_NOT_ALLOWED,
    PROVIDER_NOT_CONFIGURED,
    AUTHENTICATION,
    NETWORK,
    RATE_LIMITED,
    INVALID_RESPONSE,
    UNSUPPORTED_CAPABILITY,
    CANCELLED,
    UNKNOWN,
}
