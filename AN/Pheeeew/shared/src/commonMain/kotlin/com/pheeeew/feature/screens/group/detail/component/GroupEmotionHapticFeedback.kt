package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberGroupEmotionHapticFeedback(): () -> Unit

internal inline fun dispatchGroupEmotionTapFeedback(
    accepted: Boolean,
    fixtureFeedbackOnAcceptedPress: Boolean,
    hapticFeedback: () -> Unit,
    visualFeedback: () -> Unit,
) {
    if (accepted && !fixtureFeedbackOnAcceptedPress) hapticFeedback()
    if (accepted || fixtureFeedbackOnAcceptedPress) visualFeedback()
}
