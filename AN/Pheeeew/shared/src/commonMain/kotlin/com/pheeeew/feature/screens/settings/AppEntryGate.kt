package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.feature.screens.settings.components.SettingsColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_nickname_availability_failed
import pheeeew.shared.generated.resources.settings_nickname_change_unconfirmed
import pheeeew.shared.generated.resources.settings_nickname_device_prepare_failed
import pheeeew.shared.generated.resources.settings_nickname_invalid_error
import pheeeew.shared.generated.resources.settings_nickname_lookup_failed
import pheeeew.shared.generated.resources.settings_nickname_registration_failed
import pheeeew.shared.generated.resources.settings_nickname_registration_title
import pheeeew.shared.generated.resources.settings_nickname_required_title
import pheeeew.shared.generated.resources.settings_nickname_result_unconfirmed
import pheeeew.shared.generated.resources.settings_nickname_result_unset
import pheeeew.shared.generated.resources.settings_nickname_retry
import pheeeew.shared.generated.resources.settings_nickname_save_failed
import pheeeew.shared.generated.resources.settings_nickname_saved_unconfirmed
import pheeeew.shared.generated.resources.settings_nickname_taken

enum class NicknameUiMessage {
    DEVICE_PREPARATION_FAILED,
    NICKNAME_LOOKUP_FAILED,
    REGISTRATION_FAILED,
    DUPLICATE,
    INVALID,
    SAVE_FAILED,
    RESULT_UNCONFIRMED,
    CHANGE_UNCONFIRMED,
    SAVED_UNCONFIRMED,
    RESULT_UNSET,
    AVAILABILITY_FAILED,
}

@Composable
internal fun AppEntryGate(
    state: AppEntryUiState,
    onNicknameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state.phase) {
        AppEntryPhase.LOADING -> {
            AppDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        AppEntryPhase.REGISTRATION_REQUIRED, AppEntryPhase.NICKNAME_REQUIRED -> {
            val registration = state.phase == AppEntryPhase.REGISTRATION_REQUIRED
            NicknameDialog(
                nickname = state.nickname,
                onNicknameChange = onNicknameChanged,
                onDismiss = {},
                onConfirm = onSubmit,
                canDismiss = false,
                isBusy = state.isSubmitting,
                inputEnabled = !state.nicknameLocked && !state.isVerificationPending,
                title =
                    stringResource(
                        if (registration) Res.string.settings_nickname_registration_title else Res.string.settings_nickname_required_title,
                    ),
                message = state.message?.asText(),
                confirmEnabled = state.canConfirm,
                isLoadingIndicatorVisible = state.isLoadingIndicatorVisible,
            )
        }

        AppEntryPhase.FAILED -> {
            AppDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                Surface(color = Color.White, shape = RoundedCornerShape(22.dp)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            state.message?.asText()
                                ?: stringResource(Res.string.settings_nickname_device_prepare_failed),
                            color = SettingsColors.Ink,
                            fontSize = 14.sp,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.settings_nickname_retry)) }
                    }
                }
            }
        }

        AppEntryPhase.READY -> {
            Unit
        }
    }
}

@Composable
internal fun NicknameUiMessage.asText(): String =
    stringResource(
        when (this) {
            NicknameUiMessage.DEVICE_PREPARATION_FAILED -> Res.string.settings_nickname_device_prepare_failed
            NicknameUiMessage.NICKNAME_LOOKUP_FAILED -> Res.string.settings_nickname_lookup_failed
            NicknameUiMessage.REGISTRATION_FAILED -> Res.string.settings_nickname_registration_failed
            NicknameUiMessage.DUPLICATE -> Res.string.settings_nickname_taken
            NicknameUiMessage.INVALID -> Res.string.settings_nickname_invalid_error
            NicknameUiMessage.SAVE_FAILED -> Res.string.settings_nickname_save_failed
            NicknameUiMessage.RESULT_UNCONFIRMED -> Res.string.settings_nickname_result_unconfirmed
            NicknameUiMessage.CHANGE_UNCONFIRMED -> Res.string.settings_nickname_change_unconfirmed
            NicknameUiMessage.SAVED_UNCONFIRMED -> Res.string.settings_nickname_saved_unconfirmed
            NicknameUiMessage.RESULT_UNSET -> Res.string.settings_nickname_result_unset
            NicknameUiMessage.AVAILABILITY_FAILED -> Res.string.settings_nickname_availability_failed
        },
    )
