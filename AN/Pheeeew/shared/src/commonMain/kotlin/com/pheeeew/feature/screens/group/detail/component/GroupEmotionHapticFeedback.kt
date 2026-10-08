package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable
import com.pheeeew.feature.emotion.component.dispatchEmotionTapFeedback
import com.pheeeew.feature.emotion.component.rememberEmotionHapticFeedback

@Composable
internal fun rememberGroupEmotionHapticFeedback(): () -> Unit = rememberEmotionHapticFeedback()

internal inline fun dispatchGroupEmotionTapFeedback(
    accepted: Boolean,
    fixtureFeedbackOnAcceptedPress: Boolean,
    hapticFeedback: () -> Unit,
    visualFeedback: () -> Unit,
) {
    dispatchEmotionTapFeedback(
        accepted = accepted,
        allowVisualFeedbackWhenRejected = fixtureFeedbackOnAcceptedPress,
        hapticFeedback = hapticFeedback,
        visualFeedback = visualFeedback,
    )
}
