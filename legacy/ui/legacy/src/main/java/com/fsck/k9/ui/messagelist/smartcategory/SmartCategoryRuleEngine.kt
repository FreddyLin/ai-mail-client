package com.fsck.k9.ui.messagelist.smartcategory

import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

data class SmartCategoryMessage(
    val senderAddress: String?,
    val subject: String?,
    val preview: String,
    val hasAttachments: Boolean,
)

class SmartCategoryRuleEngine {
    fun classify(message: SmartCategoryMessage): Set<SmartCategory> = buildSet {
        if (isNewsletter(message)) add(SmartCategory.NEWSLETTER)
        if (isInvoice(message)) add(SmartCategory.INVOICE)
        if (isOrder(message)) add(SmartCategory.ORDER)
    }

    private fun isNewsletter(message: SmartCategoryMessage): Boolean {
        val sender = message.senderAddress.normalized()
        val subject = message.subject.normalized()
        val senderSignals = sender.containsAny("newsletter", "digest", "mailinglist")
        val subjectSignals = subject.containsAny("newsletter", "weekly digest", "monthly digest", "wochenrueckblick")
        val automatedSender = sender.containsAny("no-reply", "noreply", "updates", "news")
        return senderSignals || subjectSignals && automatedSender
    }

    private fun isInvoice(message: SmartCategoryMessage): Boolean {
        val sender = message.senderAddress.normalized()
        val subject = message.subject.normalized()
        val invoiceSubject = subject.containsAny("rechnung", "invoice", "rechnungsnummer", "invoice number")
        val billingSender = sender.containsAny("billing", "rechnung", "invoice", "abrechnung")
        return invoiceSubject && (billingSender || message.hasAttachments)
    }

    private fun isOrder(message: SmartCategoryMessage): Boolean {
        val sender = message.senderAddress.normalized()
        val subject = message.subject.normalized()
        val orderSubject = subject.containsAny(
            "bestellbestaetigung",
            "bestellbesta¨tigung",
            "order confirmation",
            "versandbestaetigung",
            "shipping confirmation",
        )
        val orderNumber = subject.contains("order #") || subject.contains("bestellung #")
        val shopSender = sender.containsAny("shop", "store", "orders", "bestellung", "versand")
        val hasOrderNumber = Regex("\\b\\d{3,}\\b").containsMatchIn(subject)
        val hasShippingSignal = subject.containsAny(
            "versendet",
            "versand",
            "verschickt",
            "shipped",
            "dispatched",
            "shipment",
        )
        val strongOrderSignal =
            subject.containsAny("bestellung", "order") && hasOrderNumber && hasShippingSignal
        return strongOrderSignal || (orderSubject || orderNumber) && (shopSender || message.hasAttachments)
    }

    private fun String?.normalized(): String = this.orEmpty()
        .lowercase()
        .replace("\u00e4", "ae")
        .replace("\u00f6", "oe")
        .replace("\u00fc", "ue")
        .replace("\u00df", "ss")
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)
}
