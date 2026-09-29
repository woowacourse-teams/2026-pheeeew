package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupStampItemResponseDto(
    val groupId: String,
    val name: String,
    val stamp: GroupStampResponseDto,
)
