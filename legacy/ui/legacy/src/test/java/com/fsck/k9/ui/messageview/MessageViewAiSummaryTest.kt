package com.fsck.k9.ui.messageview

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class MessageViewAiSummaryTest {
    @Test
    fun `uses existing local preview when available`() {
        val result = createAiSummaryPreview("stored preview", "reader content")

        assertThat(result).isEqualTo("stored preview")
    }

    @Test
    fun `uses bounded reader content when local preview is empty`() {
        val readerContent = "x".repeat(AI_SUMMARY_PREVIEW_MAX_LENGTH + 100)

        val result = createAiSummaryPreview(" ", readerContent)

        assertThat(result?.length).isEqualTo(AI_SUMMARY_PREVIEW_MAX_LENGTH)
    }

    @Test
    fun `returns no preview when both local sources are empty`() {
        val result = createAiSummaryPreview(" ", "")

        assertThat(result).isEqualTo(null)
    }
}
