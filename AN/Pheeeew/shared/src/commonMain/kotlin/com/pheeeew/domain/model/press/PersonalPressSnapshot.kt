package com.pheeeew.domain.model.press

import com.pheeeew.domain.model.emotion.EmotionState

/** One KST calendar day's all-region counts for this device. */
data class MyDailyPressSnapshot(
    val pressDate: String,
    val counts: Map<EmotionState, Long>,
    val total: Long,
) {
    init {
        require(pressDate.isNotBlank())
        require(counts.keys == EmotionState.entries.toSet())
        require(counts.values.all { it >= 0L } && total >= 0L)
        require(counts.values.sum() == total)
    }
}

/** Today's personal totals returned by POST /emotions/presses; that response omits pressDate. */
data class MyDailyPressTotals(
    val counts: Map<EmotionState, Long>,
    val total: Long,
) {
    init {
        require(counts.keys == EmotionState.entries.toSet())
        require(counts.values.all { it >= 0L } && total >= 0L)
        require(counts.values.sum() == total)
    }
}

/** One KST calendar day's total presses across all devices and regions. */
data class AllDailyPressSnapshot(
    val pressDate: String,
    val total: Long,
) {
    init {
        require(pressDate.isNotBlank())
        require(total >= 0L)
    }
}
