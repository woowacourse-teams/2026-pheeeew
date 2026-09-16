package com.pheeeew.feature.map.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import kotlinx.coroutines.delay

private const val SNACKBAR_DURATION_MILLIS = 3_000L

@Composable
fun ErrorSnackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var lastMessage by remember { mutableStateOf(message) }
    LaunchedEffect(message) {
        if (message != null) {
            lastMessage = message
            delay(SNACKBAR_DURATION_MILLIS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        MapErrorBanner(
            message = lastMessage.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun ErrorSnackbarPreview() {
    AppTheme {
        ErrorSnackbar(message = "마이크 권한이 필요해요", onDismiss = {})
    }
}
