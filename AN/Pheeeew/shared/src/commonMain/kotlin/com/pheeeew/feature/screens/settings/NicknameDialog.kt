package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.feature.screens.settings.SettingsTheme
import com.pheeeew.feature.screens.settings.components.NicknameDialogContent

@Composable
internal fun NicknameDialog(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    canDismiss: Boolean = true,
    isBusy: Boolean = false,
    inputEnabled: Boolean = true,
    title: String? = null,
    message: String? = null,
    confirmEnabled: Boolean = false,
    isLoadingIndicatorVisible: Boolean = false,
) {
    AppDialog(
        onDismissRequest = { if (canDismiss && !isBusy) onDismiss() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = canDismiss && !isBusy,
                dismissOnClickOutside = canDismiss && !isBusy,
            ),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NicknameDialogContent(
                nickname = nickname,
                onNicknameChange = onNicknameChange,
                onDismiss = onDismiss.takeIf { canDismiss },
                onConfirm = onConfirm,
                title = title,
                message = message,
                confirmEnabled = confirmEnabled,
                isLoadingIndicatorVisible = isLoadingIndicatorVisible,
                isBusy = isBusy,
                inputEnabled = inputEnabled,
            )
        }
    }
}

@Preview
@Composable
private fun NicknameDialogPreview() {
    SettingsTheme {
        NicknameDialogContent(
            nickname = "",
            onNicknameChange = {},
            onDismiss = {},
            onConfirm = {},
        )
    }
}
