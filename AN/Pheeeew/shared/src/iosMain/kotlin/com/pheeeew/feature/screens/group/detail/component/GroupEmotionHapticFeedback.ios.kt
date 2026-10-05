package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium

@Composable
internal actual fun rememberGroupEmotionHapticFeedback(): () -> Unit {
    val generator = remember { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleMedium) }
    SideEffect { generator.prepare() }
    return remember(generator) {
        {
            generator.impactOccurred()
            generator.prepare()
        }
    }
}
