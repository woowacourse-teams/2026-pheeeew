package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.feature.screens.group.detail.GroupDetailNoticeKind

@Composable
internal fun GroupDetailNoticeSnackbar(
    message: String,
    kind: GroupDetailNoticeKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        message = message,
        isError = kind != GroupDetailNoticeKind.CopySucceeded,
        onDismiss = onDismiss,
        onClick = onDismiss,
        durationMillis = null,
        maxLines = 3,
        modifier = modifier,
    )
}
