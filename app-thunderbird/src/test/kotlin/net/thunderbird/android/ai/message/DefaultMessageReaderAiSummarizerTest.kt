package net.thunderbird.android.ai.message

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiRequestExecutor
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiResultMetadata
import net.thunderbird.feature.ai.api.AiSummarizationResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationError
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationResult

class DefaultMessageReaderAiSummarizerTest {
    @Test
    fun `uses the account id and locally available message fields`() = runTest {
        val executor = RecordingAiRequestExecutor()
        val testSubject = DefaultMessageReaderAiSummarizer(executor)

        val result = testSubject.summarize(
            accountId = "account-uuid",
            input = MessageReaderAiSummarizationInput(
                sender = "sender@example.com",
                subject = "September product news",
                preview = "Discover our latest product updates.",
                content = "The product update is available today.",
            ),
        )

        assertThat(executor.accountId).isEqualTo("account-uuid")
        assertThat((executor.request as AiRequest.Summarization).input).isEqualTo(
            net.thunderbird.feature.ai.api.AiSummarizationInput(
                sender = "sender@example.com",
                subject = "September product news",
                preview = "Discover our latest product updates.",
                content = "The product update is available today.",
            ),
        )
        assertThat(result).isEqualTo(
            MessageReaderAiSummarizationResult.Success("The product update is available today."),
        )
    }

    @Test
    fun `maps policy failures without bypassing the executor`() = runTest {
        val testSubject = DefaultMessageReaderAiSummarizer(
            RecordingAiRequestExecutor(result = AiResult.Failure(AiError.InsufficientDataAccess)),
        )

        val result = testSubject.summarize(
            accountId = "account-uuid",
            input = MessageReaderAiSummarizationInput(null, null, null, null),
        )

        assertThat(result).isEqualTo(
            MessageReaderAiSummarizationResult.Failure(
                MessageReaderAiSummarizationError.INSUFFICIENT_DATA_ACCESS,
            ),
        )
    }

    @Test
    fun `does not interpret a classification result as a summary`() = runTest {
        val testSubject = DefaultMessageReaderAiSummarizer(
            RecordingAiRequestExecutor(result = AiResult.Classification(output = error("not used"))),
        )

        val result = testSubject.summarize(
            accountId = "account-uuid",
            input = MessageReaderAiSummarizationInput(null, null, null, "content"),
        )

        assertThat(result).isEqualTo(
            MessageReaderAiSummarizationResult.Failure(MessageReaderAiSummarizationError.UNKNOWN),
        )
    }
}

private class RecordingAiRequestExecutor(
    private val result: AiResult = AiResult.Summarization(
        output = AiSummarizationResult(
            summary = "The product update is available today.",
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
