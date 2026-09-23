package com.pheeeew.legacy.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColorScheme(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
    val accentVariant: Color,
    val outline: Color,
    val danger: Color,
    val onDanger: Color,
)

val LocalAppColorScheme =
    staticCompositionLocalOf {
        AppColorScheme(
            background = AppColors.Navy900,
            surface = AppColors.Navy800,
            onBackground = AppColors.Cream100,
            onSurfaceVariant = AppColors.Cream100.copy(alpha = 0.6f),
            accent = AppColors.Navy700,
            accentVariant = AppColors.Blue200,
            outline = AppColors.Cream100.copy(alpha = 0.25f),
            danger = AppColors.Red400,
            onDanger = AppColors.Cream100,
        )
    }
