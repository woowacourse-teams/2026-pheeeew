package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.feature.screens.settings.SettingsTheme
import com.pheeeew.feature.screens.settings.components.NicknameDialogContent

@Composable
internal fun NicknameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var nickname by remember { mutableStateOf("") }

    AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NicknameDialogContent(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                onDismiss = onDismiss,
                onConfirm = { onConfirm(nickname) },
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
