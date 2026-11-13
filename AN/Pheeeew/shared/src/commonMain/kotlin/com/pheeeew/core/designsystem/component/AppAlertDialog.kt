package com.pheeeew.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import com.pheeeew.core.designsystem.theme.AppFontScale

@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { AppFontScale(confirmButton) },
        dismissButton = dismissButton?.let { button -> { AppFontScale(button) } },
        title = title?.let { titleContent -> { AppFontScale(titleContent) } },
        text = text?.let { textContent -> { AppFontScale(textContent) } },
    )
}
