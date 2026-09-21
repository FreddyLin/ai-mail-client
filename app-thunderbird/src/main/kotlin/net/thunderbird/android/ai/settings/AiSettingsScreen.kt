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
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.organism.TopAppBarWithBackButton
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.ai.api.AiCredentialStatus
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
            onCredentialClick = { showCredentialDialog = true },
            onDeleteCredentialClick = { showDeleteDialog = true },
            onTestConnectionClick = viewModel::testConnection,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun AiSettingsContent(
    state: AiSettingsUiState,
    onAiEnabledChange: (Boolean) -> Unit,
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
    }
}

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
