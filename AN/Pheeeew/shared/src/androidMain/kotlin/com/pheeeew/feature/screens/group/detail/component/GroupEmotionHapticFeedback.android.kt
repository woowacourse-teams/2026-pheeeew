package com.pheeeew.feature.screens.group.detail.component

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberGroupEmotionHapticFeedback(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            val feedback =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            view.performHapticFeedback(feedback)
        }
    }
}
