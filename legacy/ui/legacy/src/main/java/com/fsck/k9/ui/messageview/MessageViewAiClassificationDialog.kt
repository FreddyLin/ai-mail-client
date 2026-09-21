package com.fsck.k9.ui.messageview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.Checkbox
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategory
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiError

@Composable
internal fun MessageViewAiClassificationDialog(
    result: MessageReaderAiClassificationResult,
    onDismiss: () -> Unit,
    onApply: (Set<MessageReaderAiCategory>) -> Unit,
) {
    when (result) {
        MessageReaderAiClassificationResult.Loading -> AlertDialog(
            title = stringResource(R.string.ai_classification_title),
            confirmText = stringResource(R.string.ai_classification_close),
            onConfirmClick = onDismiss,
            onDismissRequest = onDismiss,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator()
                TextBodyMedium(
                    text = stringResource(R.string.ai_classification_loading),
                    modifier = Modifier.padding(start = BoltTheme.spacings.default),
                )
            }
        }

        MessageReaderAiClassificationResult.Saving -> AlertDialog(
            title = stringResource(R.string.ai_classification_title),
            confirmText = stringResource(R.string.ai_classification_close),
            onConfirmClick = {},
            onDismissRequest = {},
            confirmButtonEnabled = false,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator()
                TextBodyMedium(
                    text = stringResource(R.string.ai_classification_saving),
                    modifier = Modifier.padding(start = BoltTheme.spacings.default),
                )
            }
        }

        is MessageReaderAiClassificationResult.Success -> {
            var selectedCategories by remember(result.categories) { mutableStateOf(result.categories) }
            AlertDialog(
                title = stringResource(R.string.ai_classification_title),
                confirmText = stringResource(R.string.ai_classification_apply),
                dismissText = stringResource(R.string.ai_classification_cancel),
                onConfirmClick = { onApply(selectedCategories) },
                onDismissClick = onDismiss,
                onDismissRequest = onDismiss,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = BoltTheme.spacings.default),
                    verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
                ) {
                    result.categories.forEach { category ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = category in selectedCategories,
                                onCheckedChange = { checked ->
                                    selectedCategories = if (checked) {
                                        selectedCategories + category
                                    } else {
                                        selectedCategories - category
                                    }
                                },
                            )
                            TextBodyLarge(
                                text = stringResource(categoryLabel(category)),
                                modifier = Modifier.weight(1f),
                            )
                            result.confidence?.let { confidence ->
                                TextBodyMedium(text = "${(confidence * 100).toInt()} %")
                            }
                        }
                    }
                }
            }
        }

        is MessageReaderAiClassificationResult.Failure -> AlertDialog(
            title = stringResource(R.string.ai_classification_title),
            text = stringResource(errorMessage(result.error)),
            confirmText = stringResource(R.string.ai_classification_close),
            onConfirmClick = onDismiss,
            onDismissRequest = onDismiss,
        )
    }
}

private fun categoryLabel(category: MessageReaderAiCategory): Int = when (category) {
    MessageReaderAiCategory.IMPORTANT -> R.string.ai_classification_category_important
    MessageReaderAiCategory.ACTION -> R.string.ai_classification_category_action
    MessageReaderAiCategory.INVOICE -> R.string.ai_classification_category_invoice
    MessageReaderAiCategory.ORDER -> R.string.ai_classification_category_order
    MessageReaderAiCategory.NEWSLETTER -> R.string.ai_classification_category_newsletter
}

private fun errorMessage(error: MessageReaderAiError): Int = when (error) {
    MessageReaderAiError.DISABLED -> R.string.ai_classification_error_disabled
    MessageReaderAiError.ACCOUNT_NOT_ALLOWED -> R.string.ai_classification_error_account_not_allowed
    MessageReaderAiError.PROVIDER_NOT_CONFIGURED -> R.string.ai_classification_error_provider_not_configured
    MessageReaderAiError.AUTHENTICATION -> R.string.ai_classification_error_authentication
    MessageReaderAiError.NETWORK -> R.string.ai_classification_error_network
    MessageReaderAiError.RATE_LIMITED -> R.string.ai_classification_error_rate_limited
    MessageReaderAiError.INVALID_RESPONSE -> R.string.ai_classification_error_invalid_response
    MessageReaderAiError.UNSUPPORTED_CAPABILITY -> R.string.ai_classification_error_unsupported_capability
    MessageReaderAiError.CANCELLED -> R.string.ai_classification_error_cancelled
    MessageReaderAiError.UNKNOWN -> R.string.ai_classification_error_unknown
    MessageReaderAiError.ASSIGNMENT_FAILED -> R.string.ai_classification_error_assignment_failed
}
