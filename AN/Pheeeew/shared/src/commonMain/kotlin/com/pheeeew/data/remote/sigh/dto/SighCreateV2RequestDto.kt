package com.pheeeew.data.remote.sigh.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighCreateV2RequestDto(
    val requestId: String,
    val latitude: Double,
    val longitude: Double,
    val memo: String? = null,
)
