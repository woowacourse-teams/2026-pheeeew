package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupMembersResponseDto(
    val items: List<GroupMemberDto>,
    val hasNext: Boolean,
    val nextCursor: String?,
)

@Serializable
data class GroupMemberDto(
    val nickname: String?,
    val role: String,
    val joinedAt: String,
)
