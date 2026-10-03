package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupPressRequestDto(
    val presses: List<GroupPressIncrementRequestDto>,
)

@Serializable
data class GroupPressIncrementRequestDto(
    val state: String,
    val count: Int,
)
