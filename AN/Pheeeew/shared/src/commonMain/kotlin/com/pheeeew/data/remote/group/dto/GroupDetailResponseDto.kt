package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupDetailResponseDto(
    val groupId: String,
    val name: String,
    val description: String?,
    val inviteCode: String,
    val role: String,
    val memberCount: Long,
    val stamp: GroupStampResponseDto,
    val weeklyStampCount: Long,
    val weeklyStampRank: Int?,
    val weeklyEmotionPressCount: Long,
    val weeklyEmotionPressRank: Int?,
)
