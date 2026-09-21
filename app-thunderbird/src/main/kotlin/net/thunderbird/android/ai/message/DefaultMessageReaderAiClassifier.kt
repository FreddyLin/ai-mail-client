package net.thunderbird.android.ai.message

import net.thunderbird.feature.ai.api.AiClassificationCategory
import net.thunderbird.feature.ai.api.AiClassificationInput
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategory
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassifier
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiError

internal class DefaultMessageReaderAiClassifier(
    private val requestExecutor: AiRequestExecutor,
) : MessageReaderAiClassifier {
    override suspend fun classify(
        accountId: String,
        input: MessageReaderAiClassificationInput,
    ): MessageReaderAiClassificationResult {
        val result = requestExecutor.execute(
            accountId = accountId,
            request = AiRequest.Classification(
                input = AiClassificationInput(
                    sender = input.sender,
                    subject = input.subject,
                    preview = input.preview,
                ),
            ),
        )

        return when (result) {
            is AiResult.Classification -> MessageReaderAiClassificationResult.Success(
                categories = result.output.categories.map(::mapCategory).toSet(),
                confidence = result.output.confidence,
            )

            is AiResult.Failure -> MessageReaderAiClassificationResult.Failure(mapError(result.error))
        }
    }

    private fun mapCategory(category: AiClassificationCategory) = when (category) {
        AiClassificationCategory.IMPORTANT -> MessageReaderAiCategory.IMPORTANT
        AiClassificationCategory.ACTION -> MessageReaderAiCategory.ACTION
        AiClassificationCategory.INVOICE -> MessageReaderAiCategory.INVOICE
        AiClassificationCategory.ORDER -> MessageReaderAiCategory.ORDER
        AiClassificationCategory.NEWSLETTER -> MessageReaderAiCategory.NEWSLETTER
    }

    private fun mapError(error: AiError) = when (error) {
        AiError.Disabled -> MessageReaderAiError.DISABLED
        AiError.AccountNotAllowed -> MessageReaderAiError.ACCOUNT_NOT_ALLOWED
        AiError.ProviderNotConfigured -> MessageReaderAiError.PROVIDER_NOT_CONFIGURED
        AiError.Authentication -> MessageReaderAiError.AUTHENTICATION
        AiError.Network -> MessageReaderAiError.NETWORK
        AiError.RateLimited -> MessageReaderAiError.RATE_LIMITED
        AiError.InvalidResponse -> MessageReaderAiError.INVALID_RESPONSE
        is AiError.UnsupportedCapability -> MessageReaderAiError.UNSUPPORTED_CAPABILITY
        AiError.Cancelled -> MessageReaderAiError.CANCELLED
        AiError.Unknown -> MessageReaderAiError.UNKNOWN
    }
}
