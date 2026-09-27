package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupDetailResponseDto(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val inviteCode: String,
    val role: String,
    val memberCount: Long,
    val stamp: GroupStampResponseDto,
    val todayPresses: GroupPressCountResponseDto,
    val weeklyScore: Long,
    val weeklyRank: Int? = null,
)

@Serializable
data class GroupPressCountResponseDto(
    val counts: Map<String, Long>,
    val total: Long,
)
