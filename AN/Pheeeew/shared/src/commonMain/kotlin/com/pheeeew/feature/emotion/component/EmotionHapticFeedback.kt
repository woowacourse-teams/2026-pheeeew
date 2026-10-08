package com.pheeeew.feature.emotion.component

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberEmotionHapticFeedback(): () -> Unit

internal inline fun dispatchEmotionTapFeedback(
    accepted: Boolean,
    allowVisualFeedbackWhenRejected: Boolean,
    hapticFeedback: () -> Unit,
    visualFeedback: () -> Unit,
) {
    if (accepted && !allowVisualFeedbackWhenRejected) hapticFeedback()
    if (accepted || allowVisualFeedbackWhenRejected) visualFeedback()
}
