package com.pheeeew.data.remote.sigh.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighMapResponseDto(
    val type: String,
    val truncated: Boolean,
    val features: List<SighFeatureDto<SighV1PropertiesDto>>,
)
