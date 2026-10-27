package com.pheeeew.feature.screens.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.feature.emotion.model.EmotionKind

internal fun EmotionState.toUiKind(): EmotionKind =
    when (this) {
        EmotionState.FRUSTRATED -> EmotionKind.Blocked
        EmotionState.IRRITATED -> EmotionKind.Annoyed
        EmotionState.EXHAUSTED -> EmotionKind.Tired
        EmotionState.DISCOURAGED -> EmotionKind.Defeated
        EmotionState.ANGRY -> EmotionKind.Angry
    }

internal fun PressSessionState.toUiState(): PressUiState =
    PressUiState(
        myToday = myToday,
        allToday = allToday,
        isLoadingMy = isLoadingMy,
        isLoadingAll = isLoadingAll,
        isSending = isSending,
        isInputEnabled = isInputEnabled,
        isOutcomeUnknown = isOutcomeUnknown,
        pendingPressCount = pendingPressCount,
        optimisticPressCounts = optimisticCounts.mapKeys { (emotion, _) -> emotion.toUiKind() },
        optimisticAllPressCount = optimisticAllPressCount,
    )

internal fun EmotionKind.toDomainState(): EmotionState =
    when (this) {
        EmotionKind.Blocked -> EmotionState.FRUSTRATED
        EmotionKind.Annoyed -> EmotionState.IRRITATED
        EmotionKind.Tired -> EmotionState.EXHAUSTED
        EmotionKind.Defeated -> EmotionState.DISCOURAGED
        EmotionKind.Angry -> EmotionState.ANGRY
    }
