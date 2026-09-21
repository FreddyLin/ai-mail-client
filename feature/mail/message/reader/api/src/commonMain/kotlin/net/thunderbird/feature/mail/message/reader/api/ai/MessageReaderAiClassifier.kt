package net.thunderbird.feature.mail.message.reader.api.ai

interface MessageReaderAiClassifier {
    suspend fun classify(
        accountId: String,
        input: MessageReaderAiClassificationInput,
    ): MessageReaderAiClassificationResult
}

interface MessageReaderAiCategoryAssigner {
    suspend fun assign(
        messageReference: String,
        categories: Set<MessageReaderAiCategory>,
    ): MessageReaderAiCategoryAssignmentResult
}

sealed interface MessageReaderAiCategoryAssignmentResult {
    data object Success : MessageReaderAiCategoryAssignmentResult
    data object Failure : MessageReaderAiCategoryAssignmentResult
}

data class MessageReaderAiClassificationInput(
    val sender: String?,
    val subject: String?,
    val preview: String?,
)

sealed interface MessageReaderAiClassificationResult {
    data object Loading : MessageReaderAiClassificationResult
    data object Saving : MessageReaderAiClassificationResult

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
    ASSIGNMENT_FAILED,
}
