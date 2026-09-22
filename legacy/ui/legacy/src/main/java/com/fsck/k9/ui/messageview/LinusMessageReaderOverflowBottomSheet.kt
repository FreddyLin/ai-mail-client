package com.fsck.k9.ui.messageview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.organism.ModalBottomSheet
import net.thunderbird.components.ui.bolt.theme.BoltTheme

internal data class LinusMessageReaderOverflowAction(
    val id: Int,
    val labelResId: Int,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
)

internal data class LinusMessageReaderOverflowState(
    val primaryActions: List<LinusMessageReaderOverflowAction>,
    val secondaryActions: List<LinusMessageReaderOverflowAction>,
    val destructiveAction: LinusMessageReaderOverflowAction?,
)

@Composable
internal fun LinusMessageReaderOverflowBottomSheet(
    primaryActions: List<LinusMessageReaderOverflowAction>,
    secondaryActions: List<LinusMessageReaderOverflowAction>,
    destructiveAction: LinusMessageReaderOverflowAction?,
    onAction: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = BoltTheme.spacings.default),
        ) {
            TextTitleMedium(
                text = stringResource(com.fsck.k9.ui.R.string.linus_mail_reader_overflow_title),
                modifier = Modifier.padding(
                    horizontal = BoltTheme.spacings.double,
                    vertical = BoltTheme.spacings.default,
                ),
                color = BoltTheme.colors.onSurface,
            )

            primaryActions.forEach { action ->
                OverflowActionRow(action = action, onClick = { onAction(action.id) })
            }

            if (secondaryActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(BoltTheme.spacings.half))
                DividerHorizontal(
                    modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
                    color = BoltTheme.colors.outlineVariant,
                )
                Spacer(modifier = Modifier.height(BoltTheme.spacings.half))
                secondaryActions.forEach { action ->
                    OverflowActionRow(action = action, onClick = { onAction(action.id) })
                }
            }

            destructiveAction?.let { action ->
                Spacer(modifier = Modifier.height(BoltTheme.spacings.half))
                DividerHorizontal(
                    modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
                    color = BoltTheme.colors.outlineVariant,
                )
                Spacer(modifier = Modifier.height(BoltTheme.spacings.half))
                OverflowActionRow(action = action, onClick = { onAction(action.id) })
            }
        }
    }
}

@Composable
private fun OverflowActionRow(
    action: LinusMessageReaderOverflowAction,
    onClick: () -> Unit,
) {
    val contentColor = if (action.isDestructive) {
        BoltTheme.colors.error
    } else {
        BoltTheme.colors.onSurface
    }
    val iconColor = if (action.isDestructive) {
        BoltTheme.colors.error
    } else {
        BoltTheme.colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BoltTheme.sizes.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = BoltTheme.spacings.double),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(BoltTheme.sizes.icon),
        )
        Spacer(modifier = Modifier.size(BoltTheme.spacings.double))
        TextBodyLarge(
            text = stringResource(action.labelResId),
            color = contentColor,
        )
    }
}
