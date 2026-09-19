package net.thunderbird.feature.mail.message.list.internal.ui.component.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.thunderbird.components.ui.bolt.molecule.PullToRefreshBox
import net.thunderbird.feature.mail.message.list.internal.ui.component.template.MessageList
import net.thunderbird.feature.mail.message.list.internal.ui.component.organism.SmartCategoryFilterRow
import net.thunderbird.feature.mail.message.list.ui.MessageListPresentation
import net.thunderbird.feature.mail.message.list.ui.component.MessageListScope
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState
import net.thunderbird.feature.notification.api.content.InAppNotification
import net.thunderbird.feature.notification.api.ui.InAppNotificationScaffold

@Composable
internal fun MessageListScope.MessageListPage(
    inAppNotificationEventFilter: (InAppNotification) -> Boolean,
    state: MessageListState,
    dispatchEvent: (MessageListEvent) -> Unit,
    modifier: Modifier = Modifier,
    presentation: MessageListPresentation = MessageListPresentation.Default,
) {
    InAppNotificationScaffold(
        eventFilter = inAppNotificationEventFilter,
        modifier = modifier,
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = (state as? MessageListState.LoadingMessages)?.isPullToRefresh == true,
            onRefresh = { dispatchEvent(MessageListEvent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (presentation == MessageListPresentation.LinusMail) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                    SmartCategoryFilterRow(state = state, dispatchEvent = dispatchEvent)
                    MessageList(
                        state = state,
                        dispatchEvent = dispatchEvent,
                        modifier = Modifier.weight(1f),
                        presentation = presentation,
                    )
                }
            } else {
                MessageList(
                    state = state,
                    dispatchEvent = dispatchEvent,
                    modifier = Modifier.fillMaxSize(),
                    presentation = presentation,
                )
            }
        }
    }
}
