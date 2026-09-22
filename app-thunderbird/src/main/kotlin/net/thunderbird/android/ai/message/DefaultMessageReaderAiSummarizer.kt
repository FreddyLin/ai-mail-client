package net.thunderbird.android.ai.message

import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiSummarizationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationError
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizer

internal class DefaultMessageReaderAiSummarizer(
    private val requestExecutor: AiRequestExecutor,
) : MessageReaderAiSummarizer {
    override suspend fun summarize(
        accountId: String,
        input: MessageReaderAiSummarizationInput,
    ): MessageReaderAiSummarizationResult {
        val result = requestExecutor.execute(
            accountId = accountId,
            request = AiRequest.Summarization(
                input = AiSummarizationInput(
                    sender = input.sender,
                    subject = input.subject,
                    preview = input.preview,
                    content = input.content,
                ),
            ),
        )

        return when (result) {
            is AiResult.Summarization -> MessageReaderAiSummarizationResult.Success(result.output.summary)
            is AiResult.Classification -> MessageReaderAiSummarizationResult.Failure(
                MessageReaderAiSummarizationError.UNKNOWN,
            )

            is AiResult.Writing -> MessageReaderAiSummarizationResult.Failure(
                MessageReaderAiSummarizationError.UNKNOWN,
            )

            is AiResult.Failure -> MessageReaderAiSummarizationResult.Failure(mapError(result.error))
        }
    }

    private fun mapError(error: AiError) = when (error) {
        AiError.Disabled -> MessageReaderAiSummarizationError.DISABLED
        AiError.AccountNotAllowed -> MessageReaderAiSummarizationError.ACCOUNT_NOT_ALLOWED
        AiError.InsufficientDataAccess -> MessageReaderAiSummarizationError.INSUFFICIENT_DATA_ACCESS
        AiError.ProviderNotConfigured -> MessageReaderAiSummarizationError.PROVIDER_NOT_CONFIGURED
        AiError.Authentication -> MessageReaderAiSummarizationError.AUTHENTICATION
        AiError.Network -> MessageReaderAiSummarizationError.NETWORK
        AiError.RateLimited -> MessageReaderAiSummarizationError.RATE_LIMITED
        AiError.InvalidResponse -> MessageReaderAiSummarizationError.INVALID_RESPONSE
        is AiError.UnsupportedCapability -> MessageReaderAiSummarizationError.UNSUPPORTED_CAPABILITY
        AiError.Cancelled -> MessageReaderAiSummarizationError.CANCELLED
        AiError.Unknown -> MessageReaderAiSummarizationError.UNKNOWN
    }
}
