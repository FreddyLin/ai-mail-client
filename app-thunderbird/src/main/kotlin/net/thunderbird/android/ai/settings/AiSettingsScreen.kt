package net.thunderbird.android.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.android.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.Switch
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleSmall
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlinedPassword
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlinedSelect
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.organism.TopAppBarWithBackButton
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.ai.api.AiCredentialStatus
import net.thunderbird.feature.ai.api.AiDataAccessLevel
import net.thunderbird.feature.ai.api.AiError

@Composable
internal fun AiSettingsScreen(
    viewModel: AiSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCredentialDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var credentialInput by remember { mutableStateOf("") }
    var pendingFullMessageSelection by remember { mutableStateOf<Pair<String, AiDataAccessLevel>?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is AiSettingsEffect.CredentialSaved) {
                credentialInput = ""
                showCredentialDialog = false
            }
        }
    }

    if (showCredentialDialog) {
        CredentialDialog(
            value = credentialInput,
            isSaving = state.credentialOperationInProgress,
            onValueChange = { credentialInput = it },
            onSave = { viewModel.saveCredential(credentialInput) },
            onDismiss = {
                credentialInput = ""
                showCredentialDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        DeleteCredentialDialog(
            isDeleting = state.credentialOperationInProgress,
            onDelete = {
                showDeleteDialog = false
                viewModel.deleteCredential()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarWithBackButton(
                title = stringResource(R.string.ai_settings_title),
                onBackClick = onBack,
            )
        },
    ) { paddingValues ->
        AiSettingsContent(
            state = state,
            onAiEnabledChange = viewModel::setAiEnabled,
            onAccountAiEnabledChange = viewModel::setAccountAiEnabled,
            onAccountDataAccessLevelChange = { accountId, level ->
                val currentLevel = state.accounts.firstOrNull { it.accountId == accountId }?.dataAccessLevel
                if (level == AiDataAccessLevel.FULL_MESSAGE && currentLevel != AiDataAccessLevel.FULL_MESSAGE) {
                    pendingFullMessageSelection = accountId to level
                } else {
                    viewModel.setAccountDataAccessLevel(accountId, level)
                }
            },
            onCredentialClick = { showCredentialDialog = true },
            onDeleteCredentialClick = { showDeleteDialog = true },
            onTestConnectionClick = viewModel::testConnection,
            modifier = Modifier.padding(paddingValues),
        )
    }

    pendingFullMessageSelection?.let { (accountId, level) ->
        AlertDialog(
            title = stringResource(R.string.ai_settings_full_message_warning_title),
            text = stringResource(R.string.ai_settings_full_message_warning_text),
            confirmText = stringResource(R.string.ai_settings_full_message_warning_confirm),
            dismissText = stringResource(R.string.ai_settings_cancel),
            onConfirmClick = {
                pendingFullMessageSelection = null
                viewModel.setAccountDataAccessLevel(accountId, level)
            },
            onDismissClick = { pendingFullMessageSelection = null },
            onDismissRequest = { pendingFullMessageSelection = null },
        )
    }
}

@Composable
private fun AiSettingsContent(
    state: AiSettingsUiState,
    onAiEnabledChange: (Boolean) -> Unit,
    onAccountAiEnabledChange: (String, Boolean) -> Unit,
    onAccountDataAccessLevelChange: (String, AiDataAccessLevel) -> Unit,
    onCredentialClick: () -> Unit,
    onDeleteCredentialClick: () -> Unit,
    onTestConnectionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(BoltTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.double),
    ) {
        item {
            AiEnabledItem(
                enabled = state.aiEnabled,
                onEnabledChange = onAiEnabledChange,
            )
        }
        item {
            SettingsSection(title = stringResource(R.string.ai_settings_provider)) {
                SettingsValue(
                    title = stringResource(R.string.ai_settings_provider),
                    value = stringResource(R.string.ai_settings_provider_openai),
                )
                DividerHorizontal()
                SettingsValue(
                    title = stringResource(R.string.ai_settings_model),
                    value = stringResource(R.string.ai_settings_model_luna),
                )
            }
        }
        item {
            CredentialSection(
                state = state,
                onCredentialClick = onCredentialClick,
                onDeleteCredentialClick = onDeleteCredentialClick,
            )
        }
        item {
            ConnectionSection(
                state = state,
                onTestConnectionClick = onTestConnectionClick,
            )
        }
        item {
            AccountsPrivacySection(
                accounts = state.accounts,
                onAiEnabledChange = onAccountAiEnabledChange,
                onDataAccessLevelChange = onAccountDataAccessLevelChange,
            )
        }
    }
}

@Composable
private fun AccountsPrivacySection(
    accounts: List<AiAccountUiState>,
    onAiEnabledChange: (String, Boolean) -> Unit,
    onDataAccessLevelChange: (String, AiDataAccessLevel) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.ai_settings_accounts_privacy)) {
        accounts.forEach { account ->
            AccountPrivacyItem(
                account = account,
                onAiEnabledChange = { onAiEnabledChange(account.accountId, it) },
                onDataAccessLevelChange = { onDataAccessLevelChange(account.accountId, it) },
            )
            DividerHorizontal()
        }
    }
}

@Composable
private fun AccountPrivacyItem(
    account: AiAccountUiState,
    onAiEnabledChange: (Boolean) -> Unit,
    onDataAccessLevelChange: (AiDataAccessLevel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default)) {
        val dataAccessLevelLabels = mapOf(
            AiDataAccessLevel.METADATA_ONLY to stringResource(R.string.ai_settings_data_access_metadata),
            AiDataAccessLevel.PREVIEW to stringResource(R.string.ai_settings_data_access_preview),
            AiDataAccessLevel.FULL_MESSAGE to stringResource(R.string.ai_settings_data_access_full_message),
        )
        TextTitleMedium(text = account.displayName)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextBodyLarge(text = stringResource(R.string.ai_settings_account_enabled))
            Switch(checked = account.enabled, onCheckedChange = onAiEnabledChange)
        }
        TextFieldOutlinedSelect(
            options = persistentListOf(*AiDataAccessLevel.entries.toTypedArray()),
            selectedOption = account.dataAccessLevel,
            onValueChange = onDataAccessLevelChange,
            label = stringResource(R.string.ai_settings_data_access),
            optionToStringTransformation = { dataAccessLevelLabels[it].orEmpty() },
        )
        TextBodySmall(text = dataAccessLevelDescription(account.dataAccessLevel))
    }
}

@Composable
private fun dataAccessLevelDescription(level: AiDataAccessLevel): String = stringResource(
    when (level) {
        AiDataAccessLevel.METADATA_ONLY -> R.string.ai_settings_data_access_metadata_description
        AiDataAccessLevel.PREVIEW -> R.string.ai_settings_data_access_preview_description
        AiDataAccessLevel.FULL_MESSAGE -> R.string.ai_settings_data_access_full_message_description
    },
)

@Composable
private fun AiEnabledItem(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChange(!enabled) }
            .padding(vertical = BoltTheme.spacings.half),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextTitleMedium(text = stringResource(R.string.ai_settings_enabled))
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Composable
private fun CredentialSection(
    state: AiSettingsUiState,
    onCredentialClick: () -> Unit,
    onDeleteCredentialClick: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.ai_settings_api_key)) {
        TextBodySmall(text = credentialStatusText(state.credentialStatus))
        if (state.credentialOperationFailed) {
            TextBodySmall(
                text = stringResource(R.string.ai_settings_credential_operation_failed),
                color = BoltTheme.colors.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default)) {
            ButtonOutlined(
                text = stringResource(
                    if (state.credentialStatus == AiCredentialStatus.Missing) {
                        R.string.ai_settings_api_key_enter
                    } else {
                        R.string.ai_settings_api_key_replace
                    },
                ),
                onClick = onCredentialClick,
                enabled = !state.credentialOperationInProgress,
            )
            if (state.credentialStatus != AiCredentialStatus.Missing) {
                ButtonText(
                    text = stringResource(R.string.ai_settings_api_key_delete),
                    onClick = onDeleteCredentialClick,
                    enabled = !state.credentialOperationInProgress,
                )
            }
        }
    }
}

@Composable
private fun ConnectionSection(
    state: AiSettingsUiState,
    onTestConnectionClick: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.ai_settings_connection)) {
        val isTestEnabled = state.aiEnabled &&
            state.openAiConfigured &&
            state.credentialStatus == AiCredentialStatus.Available &&
            !state.connectionTestInProgress
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonFilled(
                text = stringResource(R.string.ai_settings_test_connection),
                onClick = onTestConnectionClick,
                enabled = isTestEnabled,
            )
            if (state.connectionTestInProgress) {
                Spacer(modifier = Modifier.width(BoltTheme.spacings.default))
                CircularProgressIndicator()
            }
        }
        state.connectionTestResult?.let { result ->
            TextBodySmall(
                text = connectionResultText(result),
                color = if (result is AiConnectionTestResult.Success) {
                    BoltTheme.colors.success
                } else {
                    BoltTheme.colors.error
                },
            )
            if (result is AiConnectionTestResult.Success) {
                TextBodySmall(text = stringResource(R.string.ai_settings_connection_success_detail))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextTitleSmall(text = title)
        content()
    }
}

@Composable
private fun SettingsValue(
    title: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        modifier = Modifier.padding(vertical = BoltTheme.spacings.half),
    ) {
        TextBodyLarge(text = title)
        TextBodySmall(text = value)
    }
}

@Composable
private fun CredentialDialog(
    value: String,
    isSaving: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        title = stringResource(R.string.ai_settings_api_key_dialog_title),
        confirmText = stringResource(R.string.ai_settings_save),
        onConfirmClick = onSave,
        dismissText = stringResource(R.string.ai_settings_cancel),
        onDismissClick = onDismiss,
        onDismissRequest = onDismiss,
        confirmButtonEnabled = value.isNotBlank() && !isSaving,
    ) {
        TextFieldOutlinedPassword(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.ai_settings_api_key_dialog_label),
            isEnabled = !isSaving,
            isPasswordVisible = isPasswordVisible,
            onPasswordVisibilityToggleClicked = { isPasswordVisible = !isPasswordVisible },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeleteCredentialDialog(
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.ai_settings_delete_confirmation_title),
        text = stringResource(R.string.ai_settings_delete_confirmation_text),
        confirmText = stringResource(R.string.ai_settings_delete),
        onConfirmClick = onDelete,
        dismissText = stringResource(R.string.ai_settings_cancel),
        onDismissClick = onDismiss,
        onDismissRequest = onDismiss,
        confirmButtonEnabled = !isDeleting,
    )
}

@Composable
private fun credentialStatusText(status: AiCredentialStatus): String = stringResource(
    when (status) {
        AiCredentialStatus.Missing -> R.string.ai_settings_api_key_missing
        AiCredentialStatus.Available -> R.string.ai_settings_api_key_available
        AiCredentialStatus.Unavailable -> R.string.ai_settings_api_key_unavailable
    },
)

@Composable
private fun connectionResultText(result: AiConnectionTestResult): String = stringResource(
    when (result) {
        AiConnectionTestResult.Success -> R.string.ai_settings_connection_success

        is AiConnectionTestResult.Failure -> when (result.error) {
            AiError.Disabled -> R.string.ai_settings_error_disabled
            AiError.ProviderNotConfigured -> R.string.ai_settings_error_provider_not_configured
            AiError.Authentication -> R.string.ai_settings_error_authentication
            AiError.RateLimited -> R.string.ai_settings_error_rate_limited
            AiError.Network -> R.string.ai_settings_error_network
            AiError.InvalidResponse -> R.string.ai_settings_error_invalid_response
            is AiError.UnsupportedCapability -> R.string.ai_settings_error_unsupported_capability
            else -> R.string.ai_settings_error_connection_failed
        }
    },
)
