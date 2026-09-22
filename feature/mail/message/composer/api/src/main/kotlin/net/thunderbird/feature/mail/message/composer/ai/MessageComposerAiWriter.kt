package net.thunderbird.feature.mail.message.composer.ai

interface MessageComposerAiWriter {
    suspend fun write(
        accountId: String,
        input: MessageComposerAiInput,
    ): MessageComposerAiResult
}

data class MessageComposerAiInput(
    val operation: MessageComposerAiOperation,
    val subject: String?,
    val sourceContent: String?,
    val draftText: String?,
)

enum class MessageComposerAiOperation {
    REPLY,
    SHORTEN,
    PROFESSIONAL,
    FRIENDLY,
}

sealed interface MessageComposerAiResult {
    data class Success(val suggestedText: String) : MessageComposerAiResult

    data class Failure(val error: MessageComposerAiError) : MessageComposerAiResult
}

enum class MessageComposerAiError {
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
