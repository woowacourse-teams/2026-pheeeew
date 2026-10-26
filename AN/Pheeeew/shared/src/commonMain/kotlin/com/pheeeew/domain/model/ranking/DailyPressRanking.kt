package com.pheeeew.domain.model.ranking

/** A nationwide sum of all five emotions for one KST calendar day. */
data class DailyPressRanking(
    val date: String,
    val startAt: String,
    val endAt: String,
    val items: List<PressRankingItem>,
    val me: PressRankingItem?,
)
