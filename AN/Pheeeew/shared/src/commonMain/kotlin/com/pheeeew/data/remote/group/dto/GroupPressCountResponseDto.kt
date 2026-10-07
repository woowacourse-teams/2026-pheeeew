package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupPressCountResponseDto(
    val counts: Map<String, Long>,
    val total: Long,
)
