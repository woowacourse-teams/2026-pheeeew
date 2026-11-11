package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class PressEventAttemptRequestDto(
    val requestId: String,
    val counts: Map<String, Int>,
)

@Serializable
internal data class PressEventAttemptResponseDto(
    val attemptId: String,
    val total: Long,
    val bestScore: Long,
    val rank: Long,
    val rankedAt: String,
    val replayed: Boolean,
)

@Serializable
internal data class PressEventLeaderDto(
    val publicId: String,
    val nickname: String,
    val bestScore: Long,
)

@Serializable
internal data class PressEventRankingEntryDto(
    val rank: Long,
    val publicId: String,
    val nickname: String,
    val bestScore: Long,
    val mine: Boolean,
)

@Serializable
internal data class PressEventRankingsDto(
    val items: List<PressEventRankingEntryDto>,
    val hasNext: Boolean,
    val nextCursor: String?,
)

@Serializable
internal data class PressEventMyRankDto(
    val bestScore: Long,
    val rank: Long,
)

@Serializable
internal data class PressEventRankingResponseDto(
    val leader: PressEventLeaderDto?,
    val rankings: PressEventRankingsDto,
    val me: PressEventMyRankDto?,
    val rankedAt: String,
)
