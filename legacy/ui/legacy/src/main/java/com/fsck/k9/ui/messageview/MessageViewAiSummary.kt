package com.fsck.k9.ui.messageview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilledTonal
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleSmall
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationError

internal const val AI_SUMMARY_PREVIEW_MAX_LENGTH = 2_000

internal fun createAiSummaryPreview(localPreview: String?, readerContent: String?): String? =
    localPreview
        ?.takeIf { it.isNotBlank() }
        ?.takeAtMost(AI_SUMMARY_PREVIEW_MAX_LENGTH)
        ?: readerContent
            ?.takeIf { it.isNotBlank() }
            ?.takeAtMost(AI_SUMMARY_PREVIEW_MAX_LENGTH)

internal sealed interface MessageViewAiSummaryState {
    data object Idle : MessageViewAiSummaryState
    data class Loading(val previousSummary: String? = null) : MessageViewAiSummaryState
    data class Success(val summary: String, val isExpanded: Boolean = true) : MessageViewAiSummaryState
    data class Error(
        val error: MessageReaderAiSummarizationError,
        val previousSummary: String? = null,
    ) : MessageViewAiSummaryState
}

@Composable
internal fun MessageViewAiSummary(
    state: MessageViewAiSummaryState,
    onRetry: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is MessageViewAiSummaryState.Idle) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    vertical = BoltTheme.spacings.half,
                ),
            horizontalArrangement = Arrangement.End,
        ) {
            ButtonFilledTonal(
                text = "✦ ${stringResource(R.string.ai_summarization_start)}",
                onClick = onRetry,
            )
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = BoltTheme.spacings.half,
            ),
        shape = BoltTheme.shapes.extraLarge,
        color = BoltTheme.colors.infoContainer,
        contentColor = BoltTheme.colors.onInfoContainer,
    ) {
        Column(
            modifier = Modifier.padding(BoltTheme.spacings.double),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        ) {
            when (state) {
                MessageViewAiSummaryState.Idle -> Unit
                is MessageViewAiSummaryState.Loading -> {
                    SummaryHeader()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
                    ) {
                        CircularProgressIndicator()
                        TextBodyMedium(
                            text = stringResource(R.string.ai_summarization_loading),
                            color = BoltTheme.colors.onInfoContainer,
                        )
                    }
                    state.previousSummary?.let { summary ->
                        SummaryText(summary)
                    }
                }
                is MessageViewAiSummaryState.Success -> {
                    if (state.isExpanded) {
                        SummaryHeader()
                        SummaryText(state.summary)
                        SummaryActions(
                            onCollapse = onCollapse,
                            onRegenerate = onRegenerate,
                        )
                    } else {
                        CollapsedSummary(onExpand = onExpand)
                    }
                }
                is MessageViewAiSummaryState.Error -> {
                    SummaryHeader()
                    state.previousSummary?.let { summary ->
                        SummaryText(summary)
                    }
                    TextBodyMedium(
                        text = errorMessage(state.error),
                        color = BoltTheme.colors.onInfoContainer,
                    )
                    ButtonText(
                        text = stringResource(R.string.ai_summarization_retry),
                        onClick = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader() {
    TextTitleSmall(
        text = stringResource(R.string.ai_summarization_title),
        color = BoltTheme.colors.onInfoContainer,
    )
}

@Composable
private fun SummaryText(summary: String) {
    TextBodyMedium(
        text = summary,
        color = BoltTheme.colors.onInfoContainer,
    )
}

@Composable
private fun SummaryActions(
    onCollapse: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonText(
            text = stringResource(R.string.ai_summarization_collapse),
            onClick = onCollapse,
        )
        ButtonText(
            text = stringResource(R.string.ai_summarization_regenerate),
            onClick = onRegenerate,
        )
    }
}

@Composable
private fun CollapsedSummary(onExpand: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextTitleSmall(
            text = stringResource(R.string.ai_summarization_title),
            color = BoltTheme.colors.onInfoContainer,
        )
        ButtonText(
            text = stringResource(R.string.ai_summarization_expand),
            onClick = onExpand,
        )
    }
}

@Composable
private fun errorMessage(error: MessageReaderAiSummarizationError): String = when (error) {
    MessageReaderAiSummarizationError.DISABLED -> stringResource(R.string.ai_classification_error_disabled)
    MessageReaderAiSummarizationError.ACCOUNT_NOT_ALLOWED -> {
        stringResource(R.string.ai_classification_error_account_not_allowed)
    }
    MessageReaderAiSummarizationError.INSUFFICIENT_DATA_ACCESS -> {
        stringResource(R.string.ai_summarization_error_insufficient_data_access)
    }
    MessageReaderAiSummarizationError.PROVIDER_NOT_CONFIGURED -> {
        stringResource(R.string.ai_classification_error_provider_not_configured)
    }
    MessageReaderAiSummarizationError.AUTHENTICATION -> {
        stringResource(R.string.ai_classification_error_authentication)
    }
    MessageReaderAiSummarizationError.NETWORK -> stringResource(R.string.ai_classification_error_network)
    MessageReaderAiSummarizationError.RATE_LIMITED -> {
        stringResource(R.string.ai_classification_error_rate_limited)
    }
    MessageReaderAiSummarizationError.INVALID_RESPONSE -> {
        stringResource(R.string.ai_classification_error_invalid_response)
    }
    MessageReaderAiSummarizationError.UNSUPPORTED_CAPABILITY -> {
        stringResource(R.string.ai_classification_error_unsupported_capability)
    }
    MessageReaderAiSummarizationError.CANCELLED,
    MessageReaderAiSummarizationError.UNKNOWN,
    -> stringResource(R.string.ai_summarization_error_unknown)
}

private fun String.takeAtMost(maxLength: Int): String {
    if (length <= maxLength) return this

    var endIndex = maxLength
    if (endIndex > 0 && endIndex < length &&
        this[endIndex - 1].isHighSurrogate() && this[endIndex].isLowSurrogate()
    ) {
        endIndex--
    }
    return substring(0, endIndex)
}
