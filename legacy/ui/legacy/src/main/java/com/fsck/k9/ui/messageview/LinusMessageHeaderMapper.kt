package com.fsck.k9.ui.messageview

import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.ui.helper.RelativeDateTimeFormatter
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListDateTimeFormat

internal fun createLinusMessageHeaderUiModel(
    message: LocalMessage,
    account: LegacyAccountDto,
    subject: String,
    messageHelper: MessageHelper,
    recipientFormatter: MessageViewRecipientFormatter,
    relativeDateTimeFormatter: RelativeDateTimeFormatter,
): LinusMessageHeaderUiModel {
    val sender = message.from?.firstOrNull()
    val senderDisplayName = sender?.let { messageHelper.getSenderDisplayName(it).toString() }
    val senderAddress = sender?.address
    val recipients = DisplayRecipientsExtractor(
        recipientFormatter = recipientFormatter,
        maxNumberOfDisplayRecipients = 3,
    ).extractDisplayRecipients(message, account)
    val dateText = message.sentDate?.let {
        relativeDateTimeFormatter.formatDate(it.time, MessageListDateTimeFormat.Contextual)
    }.orEmpty()
    val avatarSource = senderDisplayName?.takeIf { it.isNotBlank() } ?: senderAddress

    return LinusMessageHeaderUiModel(
        senderDisplayName = senderDisplayName,
        senderAddress = senderAddress,
        recipientNames = recipients.recipientNames.map(CharSequence::toString),
        recipientCount = recipients.numberOfRecipients,
        dateText = dateText,
        subject = subject,
        avatarInitial = avatarInitials(avatarSource),
        isStarred = message.isSet(Flag.FLAGGED),
    )
}

internal fun createLinusMessageHeaderUiModel(
    senderDisplayName: String?,
    senderAddress: String?,
    recipientNames: List<String>,
    recipientCount: Int,
    dateText: String,
    subject: String,
    isStarred: Boolean,
): LinusMessageHeaderUiModel {
    val avatarSource = senderDisplayName?.takeIf { it.isNotBlank() } ?: senderAddress

    return LinusMessageHeaderUiModel(
        senderDisplayName = senderDisplayName,
        senderAddress = senderAddress,
        recipientNames = recipientNames,
        recipientCount = recipientCount,
        dateText = dateText,
        subject = subject,
        avatarInitial = avatarInitials(avatarSource),
        isStarred = isStarred,
    )
}

private fun avatarInitials(source: String?): String {
    val words = source
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.filter(String::isNotBlank)
        .orEmpty()

    return when {
        words.size >= 2 -> words.take(2).joinToString(separator = "") { it.first().uppercase() }
        words.singleOrNull() != null -> words.single().first().uppercase()
        else -> "?"
    }
}
