package com.pheeeew.domain.model.ranking

data class PressRanking(
    val weeksAgo: Int,
    val startAt: String,
    val endAt: String,
    val hasPrevious: Boolean,
    val items: List<PressRankingItem>,
    val me: PressRankingItem? = null,
)

data class PressRankingItem(
    val rank: Int,
    val nickname: String,
    val pressCount: Long,
)

data class DailyPressRanking(
    val date: String,
    val startAt: String,
    val endAt: String,
    val items: List<PressRankingItem>,
    val me: PressRankingItem?,
)
