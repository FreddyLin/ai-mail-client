package net.thunderbird.feature.mail.message.composer.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageComposerAiWriterTest {
    @Test
    fun `writing operations are provider independent`() {
        assertEquals(
            listOf(
                MessageComposerAiOperation.REPLY,
                MessageComposerAiOperation.SHORTEN,
                MessageComposerAiOperation.PROFESSIONAL,
                MessageComposerAiOperation.FRIENDLY,
            ),
            MessageComposerAiOperation.entries,
        )
    }

    @Test
    fun `input keeps source content and draft text separate`() {
        val input = MessageComposerAiInput(
            operation = MessageComposerAiOperation.REPLY,
            subject = "Subject",
            sourceContent = "Incoming message",
            draftText = "Current draft",
        )

        assertEquals("Incoming message", input.sourceContent)
        assertEquals("Current draft", input.draftText)
    }

    @Test
    fun `result contains only a suggestion or an abstract error`() {
        assertEquals(
            MessageComposerAiResult.Success("Suggested text"),
            MessageComposerAiResult.Success("Suggested text"),
        )
        assertEquals(
            MessageComposerAiResult.Failure(MessageComposerAiError.NETWORK),
            MessageComposerAiResult.Failure(MessageComposerAiError.NETWORK),
        )
    }
}
