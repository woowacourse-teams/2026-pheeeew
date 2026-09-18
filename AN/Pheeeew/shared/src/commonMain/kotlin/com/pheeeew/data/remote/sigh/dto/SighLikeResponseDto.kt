package com.pheeeew.data.remote.sigh.dto

import com.pheeeew.domain.model.sigh.SighLikeState
import kotlinx.serialization.Serializable

@Serializable
data class SighLikeResponseDto(
    val liked: Boolean,
    val likeCount: Long,
)

fun SighLikeResponseDto.toSighLikeState(): SighLikeState =
    SighLikeState(
        liked = liked,
        likeCount = likeCount,
    )
