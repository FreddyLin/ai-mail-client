package com.fsck.k9.ui.messageview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
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
    data object Loading : MessageViewAiSummaryState
    data class Success(val summary: String) : MessageViewAiSummaryState
    data class Error(val error: MessageReaderAiSummarizationError) : MessageViewAiSummaryState
}

@Composable
internal fun MessageViewAiSummary(
    state: MessageViewAiSummaryState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is MessageViewAiSummaryState.Idle) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                horizontal = BoltTheme.spacings.double,
                vertical = BoltTheme.spacings.default,
            ),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
    ) {
        TextTitleSmall(
            text = stringResource(R.string.ai_summarization_title),
            color = MaterialTheme.colorScheme.onSurface,
        )

        when (state) {
            MessageViewAiSummaryState.Idle -> Unit
            MessageViewAiSummaryState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
                ) {
                    CircularProgressIndicator()
                    TextBodyMedium(
                        text = stringResource(R.string.ai_summarization_loading),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            is MessageViewAiSummaryState.Success -> TextBodyMedium(
                text = state.summary,
                color = MaterialTheme.colorScheme.onSurface,
            )
            is MessageViewAiSummaryState.Error -> {
                TextBodyMedium(
                    text = errorMessage(state.error),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ButtonText(
                    text = stringResource(R.string.ai_summarization_retry),
                    onClick = onRetry,
                )
            }
        }
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
