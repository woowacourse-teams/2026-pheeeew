package com.pheeeew.data.remote.group

import kotlinx.serialization.Serializable

@Serializable
data class GroupRankingResponseDto(
    val weeksAgo: Int,
    val startAt: String,
    val endAt: String,
    val hasPrevious: Boolean,
    val items: List<GroupRankingItemResponseDto>,
)

@Serializable
data class GroupRankingItemResponseDto(
    val rank: Int,
    val groupId: String,
    val name: String,
    val stamp: GroupStampResponseDto,
    val score: Int,
)
