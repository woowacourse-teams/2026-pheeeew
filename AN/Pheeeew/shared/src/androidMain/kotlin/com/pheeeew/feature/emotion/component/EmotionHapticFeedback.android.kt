package com.pheeeew.feature.emotion.component

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberNativeEmotionHapticFeedback(): () -> Unit {
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

@Composable
internal actual fun rememberEmotionHapticContext(): Any? = LocalContext.current.applicationContext

@Composable
internal actual fun rememberEmotionHapticPlaybackAllowed(): () -> Boolean {
    val view = LocalView.current
    return remember(view) {
        {
            view.isHapticFeedbackEnabled &&
                Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        }
    }
}
