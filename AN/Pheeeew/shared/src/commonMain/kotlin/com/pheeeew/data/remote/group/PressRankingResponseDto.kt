package com.pheeeew.data.remote.group

import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import kotlinx.serialization.Serializable

@Serializable
data class PressRankingResponseDto(
    val weeksAgo: Int,
    val startAt: String,
    val endAt: String,
    val hasPrevious: Boolean,
    val items: List<PressRankingItemResponseDto>,
    val state: String? = null,
)

@Serializable
data class PressRankingItemResponseDto(
    val rank: Int,
    val groupId: String,
    val name: String,
    val score: Int,
    val mine: Boolean = false,
    val stamp: GroupStampResponseDto? = null,
)
