package com.pheeeew.feature.screens.press

import com.pheeeew.data.press.race.PressRaceFrameKey
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.feature.emotion.model.EmotionKind

internal data class PressUiState(
    val nearbyRanking: PressNearbyRankingUiState = PressNearbyRankingUiState(status = PressNearbyRankingStatus.Loading),
    val activity: PressActivityUiState = PressActivityUiState(),
    val myToday: MyDailyPressSnapshot? = null,
    val allToday: AllDailyPressSnapshot? = null,
    val isLoadingMy: Boolean = true,
    val isLoadingAll: Boolean = true,
    val frameKey: PressRaceFrameKey? = null,
    val isSending: Boolean = false,
    val isInputEnabled: Boolean = true,
    val isOutcomeUnknown: Boolean = false,
    val pendingPressCount: Long = 0L,
    val optimisticPressCounts: Map<EmotionKind, Long> = emptyMap(),
    val optimisticAllPressCount: Long = 0L,
) {
    val optimisticMyTotalCount: Long
        get() = optimisticPressCounts.values.sum()

    val myEmotionCounts: Map<EmotionKind, Long>
        get() =
            myToday?.counts?.mapKeys { (emotion, _) -> emotion.toUiKind() }.orEmpty()
}
