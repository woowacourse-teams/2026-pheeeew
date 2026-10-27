package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PressRankingResponseDto(
    val weeksAgo: Int,
    val startAt: String,
    val endAt: String,
    val items: List<PressRankingItemResponseDto>,
    val me: PressRankingItemResponseDto?,
    val state: String? = null,
)

@Serializable
data class DailyPressRankingResponseDto(
    val date: String,
    val startAt: String,
    val endAt: String,
    val items: List<PressRankingItemResponseDto>,
    val me: PressRankingItemResponseDto?,
)

@Serializable
data class PressRankingItemResponseDto(
    val rank: Int,
    val nickname: String,
    val pressCount: Long,
)
