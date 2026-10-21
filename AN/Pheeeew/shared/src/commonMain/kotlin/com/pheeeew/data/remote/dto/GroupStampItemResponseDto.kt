package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupStampItemResponseDto(
    val groupId: String,
    val name: String,
    val stamp: GroupStampResponseDto,
)
