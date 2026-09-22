package com.fsck.k9.ui.messageview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextHeadlineSmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme

internal data class LinusMessageHeaderUiModel(
    val senderDisplayName: String?,
    val senderAddress: String?,
    val recipientNames: List<String>,
    val recipientCount: Int,
    val dateText: String,
    val subject: String,
    val avatarInitial: String,
    val isStarred: Boolean,
)

@Composable
internal fun LinusMessageHeader(
    model: LinusMessageHeaderUiModel,
    onRecipientsClick: () -> Unit,
    onStarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = BoltTheme.spacings.half,
            ),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        ) {
            TextHeadlineSmall(
                modifier = Modifier.weight(1f),
                text = model.subject.ifBlank { stringResource(com.fsck.k9.ui.R.string.general_no_subject) },
                color = MaterialTheme.colorScheme.onSurface,
            )

            ButtonIcon(
                onClick = onStarClick,
                imageVector = if (model.isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                colors = ButtonIconDefaults.buttonIconColors(
                    contentColor = if (model.isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                contentDescription = stringResource(
                    if (model.isStarred) {
                        com.fsck.k9.ui.R.string.linus_mail_header_starred
                    } else {
                        com.fsck.k9.ui.R.string.linus_mail_header_not_starred
                    },
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TextTitleMedium(
                        text = model.avatarInitial,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                TextTitleMedium(
                    text = model.senderDisplayName
                        ?: model.senderAddress
                        ?: stringResource(com.fsck.k9.ui.R.string.general_no_sender),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                model.senderAddress?.takeIf { it.isNotBlank() }?.let { address ->
                    TextBodySmall(
                        text = address,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .clickable(onClick = onRecipientsClick)
                        .padding(top = BoltTheme.spacings.half),
                    horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
                ) {
                    TextBodySmall(
                        text = recipientText(model),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (model.dateText.isNotBlank()) {
                        TextBodySmall(
                            text = model.dateText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun recipientText(model: LinusMessageHeaderUiModel): String {
    val names = model.recipientNames.joinToString(", ")
    val additionalCount = model.recipientCount - model.recipientNames.size
    return if (additionalCount > 0) {
        stringResource(com.fsck.k9.ui.R.string.linus_mail_header_recipients_more, names, additionalCount)
    } else {
        names
    }
}
