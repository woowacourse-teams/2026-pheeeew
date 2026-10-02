package com.pheeeew.domain.model.ranking

data class PressRanking(
    val weeksAgo: Int,
    val startAt: String,
    val endAt: String,
    val hasPrevious: Boolean,
    val items: List<PressRankingItem>,
)

data class PressRankingItem(
    val rank: Int,
    val groupId: String,
    val name: String,
    val score: Int,
    val mine: Boolean,
)
