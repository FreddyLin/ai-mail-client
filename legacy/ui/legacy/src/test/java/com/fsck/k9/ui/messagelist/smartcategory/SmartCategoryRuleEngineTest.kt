package com.fsck.k9.ui.messagelist.smartcategory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

class SmartCategoryRuleEngineTest {
    private val testSubject = SmartCategoryRuleEngine()

    @Test
    fun `mailing list sender is classified as newsletter`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "newsletter@example.com",
                subject = "Weekly updates",
                preview = "Read this week's news",
                hasAttachments = false,
            ),
        )

        assertEquals(setOf(SmartCategory.NEWSLETTER), result)
    }

    @Test
    fun `personal mail is not classified as newsletter`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "alice@example.com",
                subject = "Newsletter project discussion",
                preview = "Can we discuss this tomorrow?",
                hasAttachments = false,
            ),
        )

        assertTrue(SmartCategory.NEWSLETTER !in result)
    }

    @Test
    fun `billing subject with attachment is classified as invoice`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "billing@example.com",
                subject = "Invoice 2026-1234",
                preview = "Your monthly statement",
                hasAttachments = true,
            ),
        )

        assertTrue(SmartCategory.INVOICE in result)
    }

    @Test
    fun `incidental invoice word in personal mail is not enough`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "alice@example.com",
                subject = "Re: invoice project discussion",
                preview = "The invoice topic came up in our meeting",
                hasAttachments = false,
            ),
        )

        assertTrue(SmartCategory.INVOICE !in result)
    }

    @Test
    fun `order confirmation with shop sender is classified as order`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "orders@example-shop.com",
                subject = "Order confirmation #1234",
                preview = "Thank you for your order",
                hasAttachments = false,
            ),
        )

        assertTrue(SmartCategory.ORDER in result)
    }

    @Test
    fun `german forwarded shipping order with number is classified as order`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "info@foletti.example",
                subject = "WG: Foletti Computer: Ihre Bestellung 598530 ist versendet mit Post Brief",
                preview = "Ihre Bestellung wurde versendet",
                hasAttachments = false,
            ),
        )

        assertTrue(SmartCategory.ORDER in result)
    }

    @Test
    fun `ordinary mail is not classified as order`() {
        val result = testSubject.classify(
            SmartCategoryMessage(
                senderAddress = "alice@example.com",
                subject = "Order confirmation discussion",
                preview = "Let's review the wording",
                hasAttachments = false,
            ),
        )

        assertTrue(SmartCategory.ORDER !in result)
    }
}
