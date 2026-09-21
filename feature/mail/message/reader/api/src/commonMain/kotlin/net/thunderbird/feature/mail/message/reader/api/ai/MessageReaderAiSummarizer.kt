package net.thunderbird.feature.mail.message.reader.api.ai

interface MessageReaderAiSummarizer {
    suspend fun summarize(
        accountId: String,
        input: MessageReaderAiSummarizationInput,
    ): MessageReaderAiSummarizationResult
}

data class MessageReaderAiSummarizationInput(
    val sender: String?,
    val subject: String?,
    val preview: String?,
    val content: String?,
)

sealed interface MessageReaderAiSummarizationResult {
    data class Success(val summary: String) : MessageReaderAiSummarizationResult

    data class Failure(val error: MessageReaderAiSummarizationError) : MessageReaderAiSummarizationResult
}

enum class MessageReaderAiSummarizationError {
    DISABLED,
    ACCOUNT_NOT_ALLOWED,
    INSUFFICIENT_DATA_ACCESS,
    PROVIDER_NOT_CONFIGURED,
    AUTHENTICATION,
    NETWORK,
    RATE_LIMITED,
    INVALID_RESPONSE,
    UNSUPPORTED_CAPABILITY,
    CANCELLED,
    UNKNOWN,
}
