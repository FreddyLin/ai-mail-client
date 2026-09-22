package com.fsck.k9.ui.messageview

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class LinusMessageHeaderMapperTest {
    @Test
    fun `maps sender address to avatar when display name is missing`() {
        val model = createLinusMessageHeaderUiModel(
            senderDisplayName = null,
            senderAddress = "max@example.com",
            recipientNames = listOf("me"),
            recipientCount = 1,
            dateText = "Heute, 20:43",
            subject = "Betreff",
            isStarred = false,
        )

        assertThat(model.senderDisplayName).isEqualTo(null)
        assertThat(model.senderAddress).isEqualTo("max@example.com")
        assertThat(model.avatarInitial).isEqualTo("M")
        assertThat(model.isStarred).isEqualTo(false)
    }

    @Test
    fun `keeps long subject and current message data in the ui model`() {
        val subject = "Ein sehr langer Betreff, der vollständig umbrechen können soll"
        val model = createLinusMessageHeaderUiModel(
            senderDisplayName = "Max Mustermann",
            senderAddress = "max@example.com",
            recipientNames = listOf("me"),
            recipientCount = 1,
            dateText = "Heute, 20:43",
            subject = subject,
            isStarred = true,
        )

        assertThat(model.subject).isEqualTo(subject)
        assertThat(model.senderDisplayName).isEqualTo("Max Mustermann")
        assertThat(model.avatarInitial).isEqualTo("MM")
        assertThat(model.isStarred).isEqualTo(true)
    }

    @Test
    fun `uses safe fallbacks when sender and subject are unavailable`() {
        val model = createLinusMessageHeaderUiModel(
            senderDisplayName = null,
            senderAddress = null,
            recipientNames = emptyList(),
            recipientCount = 0,
            dateText = "",
            subject = "",
            isStarred = false,
        )

        assertThat(model.avatarInitial).isEqualTo("?")
        assertThat(model.recipientNames).isEqualTo(emptyList())
        assertThat(model.recipientCount).isEqualTo(0)
        assertThat(model.dateText).isEqualTo("")
        assertThat(model.subject).isEqualTo("")
    }
}
