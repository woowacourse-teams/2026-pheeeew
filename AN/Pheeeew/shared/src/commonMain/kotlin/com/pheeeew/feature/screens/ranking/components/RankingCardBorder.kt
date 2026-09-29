package com.pheeeew.feature.screens.ranking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors

internal fun Modifier.rankingCardBorder(): Modifier {
    val shape = RoundedCornerShape(16.dp)
    return this
        .drawBehind {
            val shadowOffset = 3.dp.toPx()
            drawRoundRect(
                color = AppColors.GroupInk,
                topLeft = Offset(0f, shadowOffset),
                size = Size(size.width, size.height - shadowOffset),
                cornerRadius = CornerRadius(18.dp.toPx()),
            )
        }.padding(bottom = 3.dp)
        .background(AppColors.Background, shape)
        .border(AppBorders.Standard, AppColors.GroupInk, shape)
}
