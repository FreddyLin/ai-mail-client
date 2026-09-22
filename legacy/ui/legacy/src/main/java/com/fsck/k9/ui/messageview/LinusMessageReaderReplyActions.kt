package com.fsck.k9.ui.messageview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilledTonal
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.theme.BoltTheme

internal data class LinusMessageReaderReplyActionsState(
    val isVisible: Boolean = false,
    val showReplyAll: Boolean = false,
)

@Composable
internal fun LinusMessageReaderReplyActions(
    state: LinusMessageReaderReplyActionsState,
    onReply: () -> Unit,
    onReplyAll: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = BoltTheme.spacings.triple, vertical = BoltTheme.spacings.default),
            horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        ) {
            ButtonFilledTonal(
                text = stringResource(R.string.reply_action),
                onClick = onReply,
            )
            if (state.showReplyAll) {
                ButtonOutlined(
                    text = stringResource(R.string.reply_all_action),
                    onClick = onReplyAll,
                )
            }
            ButtonOutlined(
                text = stringResource(R.string.forward_action),
                onClick = onForward,
            )
        }
    }
}
