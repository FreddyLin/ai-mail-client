package net.thunderbird.android.ai.message

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Clock
import net.thunderbird.feature.ai.api.AiClassificationCategory
import net.thunderbird.feature.ai.api.AiClassificationResult
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiResultMetadata
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategory
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationResult

class DefaultMessageReaderAiClassifierTest {
    @Test
    fun `uses the account id and locally available message fields`() = kotlinx.coroutines.test.runTest {
        val executor = RecordingAiRequestExecutor()
        val testSubject = DefaultMessageReaderAiClassifier(executor)

        val result = testSubject.classify(
            accountId = "account-uuid",
            input = MessageReaderAiClassificationInput(
                sender = "sender@example.com",
                subject = "September product news",
                preview = "Discover our latest product updates.",
            ),
        )

        assertThat(executor.accountId).isEqualTo("account-uuid")
        assertThat((executor.request as AiRequest.Classification).input).isEqualTo(
            net.thunderbird.feature.ai.api.AiClassificationInput(
                sender = "sender@example.com",
                subject = "September product news",
                preview = "Discover our latest product updates.",
            ),
        )
        assertThat(result).isEqualTo(
            MessageReaderAiClassificationResult.Success(
                categories = setOf(MessageReaderAiCategory.NEWSLETTER),
                confidence = 0.97,
            ),
        )
    }

    @Test
    fun `maps policy failure without bypassing the executor`() = kotlinx.coroutines.test.runTest {
        val testSubject = DefaultMessageReaderAiClassifier(
            RecordingAiRequestExecutor(result = AiResult.Failure(AiError.AccountNotAllowed)),
        )

        val result = testSubject.classify(
            accountId = "account-uuid",
            input = MessageReaderAiClassificationInput(null, null, null),
        )

        assertThat(result).isEqualTo(
            MessageReaderAiClassificationResult.Failure(
                net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiError.ACCOUNT_NOT_ALLOWED,
            ),
        )
    }
}

private class RecordingAiRequestExecutor(
    private val result: AiResult = AiResult.Classification(
        output = AiClassificationResult(
            categories = setOf(AiClassificationCategory.NEWSLETTER),
            confidence = 0.97,
            metadata = AiResultMetadata(
                providerId = AiProviderId("openai"),
                modelId = AiModelId("gpt-5.6-luna"),
                completedAt = Clock.System.now(),
            ),
        ),
    ),
) : AiRequestExecutor {
    var accountId: String? = null
    var request: AiRequest? = null

    override suspend fun execute(accountId: String, request: AiRequest): AiResult {
        this.accountId = accountId
        this.request = request
        return result
    }

    override suspend fun testConnection(): AiResult = error("Not used in this test")
}
