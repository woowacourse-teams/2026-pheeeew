package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupJoinRequestDto(
    val inviteCode: String,
)

/** Only the server-issued identifier is required to complete navigation. */
@Serializable
data class GroupJoinResponseDto(
    val groupId: String,
)
