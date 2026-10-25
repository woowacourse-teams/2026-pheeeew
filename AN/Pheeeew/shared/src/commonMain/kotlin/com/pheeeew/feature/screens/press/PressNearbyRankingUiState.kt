package com.pheeeew.feature.screens.press

/** Display data only. The caller owns participant selection and authoritative ordering. */
internal data class PressNearbyRankingUiState(
    val rows: List<PressNearbyRankRow> = emptyList(),
    val status: PressNearbyRankingStatus = PressNearbyRankingStatus.Ready,
    val participationPending: Boolean = false,
    val progress: Float? = null,
) {
    init {
        require(rows.size <= 3)
        require(rows.count { it.isMe } <= 1)
        require(!participationPending || (rows.size <= 2 && rows.none { it.isMe }))
    }
}

internal data class PressNearbyRankRow(
    val rank: Long,
    val nickname: String,
    val count: Long,
    val isMe: Boolean = false,
) {
    init {
        require(rank > 0 && count >= 0)
    }
}

internal enum class PressNearbyRankingStatus { Loading, Ready, Reconnecting }

internal fun normalizedNearbyProgress(value: Float?): Float? = value?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
