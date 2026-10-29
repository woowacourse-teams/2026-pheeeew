package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupStampResponseDto(
    val text: String,
    val textColor: String,
    val backgroundColor: String,
    val frame: String,
)
