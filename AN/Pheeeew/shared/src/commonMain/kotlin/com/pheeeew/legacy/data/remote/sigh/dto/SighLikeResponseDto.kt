package com.pheeeew.legacy.data.remote.sigh.dto

import com.pheeeew.legacy.domain.model.sigh.SighLikeState
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
