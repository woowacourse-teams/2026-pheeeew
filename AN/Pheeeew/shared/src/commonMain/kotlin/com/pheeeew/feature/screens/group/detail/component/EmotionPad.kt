package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.emotion.component.EmotionPad as SharedEmotionPad

@Composable
internal fun EmotionPad(
    counts: List<EmotionCountUiModel>,
    optimisticPressCounts: Map<EmotionKind, Long> = emptyMap(),
    enabled: Boolean,
    onEmotionTap: (EmotionKind) -> Boolean,
    fixtureFeedbackOnAcceptedPress: Boolean = false,
    feedbackOperationKey: () -> GroupOperationKey? = { null },
    onFeedbackShown: (GroupOperationKey) -> Unit = {},
    preserveFeedbackWhileDisabled: Boolean = false,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean? = null,
) {
    SharedEmotionPad(
        counts = counts,
        optimisticPressCounts = optimisticPressCounts,
        enabled = enabled,
        onEmotionTap = onEmotionTap,
        allowVisualFeedbackWhenRejected = fixtureFeedbackOnAcceptedPress,
        feedbackAcknowledgementForAcceptedPress = {
            feedbackOperationKey()?.let { key -> { onFeedbackShown(key) } }
        },
        preserveFeedbackWhileDisabled = preserveFeedbackWhileDisabled,
        modifier = modifier,
        reducedMotionOverride = reducedMotion,
    )
}

internal fun formatCount(value: Long): String =
    com.pheeeew.feature.emotion.component
        .formatCount(value)
