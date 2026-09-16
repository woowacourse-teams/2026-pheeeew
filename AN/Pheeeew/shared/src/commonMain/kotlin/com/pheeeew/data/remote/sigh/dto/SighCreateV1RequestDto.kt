package com.pheeeew.data.remote.sigh.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighCreateV1RequestDto(
    val requestId: String,
    val latitude: Double,
    val longitude: Double,
)
