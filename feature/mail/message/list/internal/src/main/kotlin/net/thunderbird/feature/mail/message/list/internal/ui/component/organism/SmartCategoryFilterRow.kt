package net.thunderbird.feature.mail.message.list.internal.ui.component.organism

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilledTonal
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory
import net.thunderbird.feature.mail.message.list.R as ApiR

@Composable
internal fun SmartCategoryFilterRow(
    state: MessageListState,
    dispatchEvent: (MessageListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SmartCategory.entries.forEach { category ->
            val label = stringResource(category.labelRes())
            if (state.metadata.selectedSmartCategory == category) {
                ButtonFilledTonal(
                    text = label,
                    onClick = { dispatchEvent(MessageListEvent.SelectSmartCategory(category)) },
                )
            } else {
                ButtonText(
                    text = label,
                    onClick = { dispatchEvent(MessageListEvent.SelectSmartCategory(category)) },
                )
            }
        }
    }
}

@Composable
internal fun SmartCategoryEmptyState(
    category: SmartCategory,
    modifier: Modifier = Modifier,
) {
    TextBodyLarge(
        text = stringResource(ApiR.string.smart_category_empty, stringResource(category.labelRes())),
        modifier = modifier.padding(24.dp),
    )
}

private fun SmartCategory.labelRes(): Int = when (this) {
    SmartCategory.ALL -> ApiR.string.smart_category_all
    SmartCategory.IMPORTANT -> ApiR.string.smart_category_important
    SmartCategory.ACTION -> ApiR.string.smart_category_action
    SmartCategory.INVOICE -> ApiR.string.smart_category_invoice
    SmartCategory.ORDER -> ApiR.string.smart_category_order
    SmartCategory.NEWSLETTER -> ApiR.string.smart_category_newsletter
}
