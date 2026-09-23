package com.pheeeew.legacy.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import kotlinx.coroutines.delay

private const val APP_SNACKBAR_DURATION_MILLIS = 3_000L
private val APP_SNACKBAR_WIDTH = 347.dp
private val APP_SNACKBAR_HEIGHT = 51.dp

@Composable
fun AppSnackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var lastMessage by remember { mutableStateOf(message) }

    LaunchedEffect(message) {
        if (message != null) {
            lastMessage = message
            delay(APP_SNACKBAR_DURATION_MILLIS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = APP_SNACKBAR_WIDTH)
                    .height(APP_SNACKBAR_HEIGHT)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Navy850,
            contentColor = AppColors.Cream100,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lastMessage.orEmpty(),
                    style = AppTheme.typography.menuItem,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppSnackbarPreview() {
    AppTheme {
        AppSnackbar(
            message = "내가 작성한 한숨은 차단할 수 없어요.",
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
