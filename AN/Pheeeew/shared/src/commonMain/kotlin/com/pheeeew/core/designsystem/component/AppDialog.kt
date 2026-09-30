package com.pheeeew.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppFontScale

@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        AppFontScale(content)
    }
}
