package com.pheeeew.core.designsystem.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun CircularLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppColors.GroupInk,
    strokeWidth: Dp = 4.dp,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
    )
}
