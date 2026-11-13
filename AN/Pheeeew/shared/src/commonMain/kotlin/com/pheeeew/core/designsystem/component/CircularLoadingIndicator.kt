package com.pheeeew.core.designsystem.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.appInk

@Composable
fun CircularLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color? = null,
    strokeWidth: Dp = 4.dp,
) {
    val indicatorColor = color ?: MaterialTheme.colorScheme.appInk
    CircularProgressIndicator(
        modifier = modifier,
        color = indicatorColor,
        strokeWidth = strokeWidth,
    )
}
