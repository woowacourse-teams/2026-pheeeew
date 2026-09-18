package com.pheeeew.data.remote.sigh.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighLikeRequestDto(
    val liked: Boolean,
)
