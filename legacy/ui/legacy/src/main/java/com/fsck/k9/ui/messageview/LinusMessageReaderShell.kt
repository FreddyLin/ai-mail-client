package com.fsck.k9.ui.messageview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.theme.BoltTheme

@Composable
internal fun LinusMessageReaderShell(
    headerModel: LinusMessageHeaderUiModel?,
    summaryState: MessageViewAiSummaryState,
    isMessageRead: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit,
    onOverflow: () -> Unit,
    onRecipientsClick: () -> Unit,
    onStarClick: () -> Unit,
    onSummaryRetry: () -> Unit,
    onSummaryCollapse: () -> Unit,
    onSummaryExpand: () -> Unit,
    onSummaryRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BoltTheme.colors.surface,
        contentColor = BoltTheme.colors.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LinusMessageReaderTopBar(
                    isMessageRead = isMessageRead,
                    onBack = onBack,
                    onDelete = onDelete,
                    onToggleRead = onToggleRead,
                    onOverflow = onOverflow,
                    modifier = readerContentModifier().align(Alignment.Center),
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(readerContentModifier()),
                ) {
                    headerModel?.let { model ->
                        LinusMessageHeader(
                            model = model,
                            onRecipientsClick = onRecipientsClick,
                            onStarClick = onStarClick,
                        )
                        MessageViewAiSummary(
                            state = summaryState,
                            onRetry = onSummaryRetry,
                            onCollapse = onSummaryCollapse,
                            onExpand = onSummaryExpand,
                            onRegenerate = onSummaryRegenerate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinusMessageReaderTopBar(
    isMessageRead: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = BoltTheme.spacings.half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        ButtonIcon(
            onClick = onBack,
            imageVector = Icons.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.linus_mail_reader_back),
        )
        Box(modifier = Modifier.weight(1f))
        ButtonIcon(
            onClick = onDelete,
            imageVector = Icons.Outlined.Delete,
            colors = ButtonIconDefaults.buttonIconColors(contentColor = BoltTheme.colors.error),
            contentDescription = stringResource(R.string.delete_action),
        )
        ButtonIcon(
            onClick = onToggleRead,
            imageVector = if (isMessageRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
            contentDescription = stringResource(
                if (isMessageRead) R.string.mark_as_unread_action else R.string.mark_as_read_action,
            ),
        )
        ButtonIcon(
            onClick = onOverflow,
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.linus_mail_reader_overflow_title),
        )
    }
}

@Composable
private fun readerContentModifier(): Modifier = Modifier
    .widthIn(max = 720.dp)
    .fillMaxWidth()
    .padding(horizontal = BoltTheme.spacings.triple)
