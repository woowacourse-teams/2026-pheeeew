package com.pheeeew.data.remote.group

import kotlinx.serialization.Serializable

@Serializable
data class GroupResponseDto(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val inviteCode: String,
    val role: String,
    val memberCount: Long,
    val stamp: GroupStampResponseDto,
)

@Serializable
data class GroupPreviewResponseDto(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val memberCount: Long,
    val stamp: GroupStampResponseDto,
)

@Serializable
data class GroupStampResponseDto(
    val text: String,
    val textColor: String,
    val backgroundColor: String,
    val frame: String,
)
