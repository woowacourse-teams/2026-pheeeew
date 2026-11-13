package com.pheeeew.feature.screens.press

/** Display data only. The caller owns participant selection and authoritative ordering. */
internal data class PressNearbyRankingUiState(
    val rows: List<PressNearbyRankRow> = emptyList(),
    val status: PressNearbyRankingStatus = PressNearbyRankingStatus.Ready,
    val participationPending: Boolean = false,
    val progress: Float? = null,
    val date: String? = null,
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
    val publicId: String? = null,
) {
    init {
        require(rank > 0 && count >= 0)
    }
}

internal enum class PressNearbyRankingStatus { Loading, Ready, Cached, Reconnecting, Unavailable }

internal fun normalizedNearbyProgress(value: Float?): Float? = value?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)

/** Rank and nickname may change; the server public ID is stable within its date. */
internal fun nearbyRankingItemKey(
    date: String?,
    row: PressNearbyRankRow,
    previewIndex: Int,
): String = "$date:${row.publicId ?: "preview-$previewIndex"}"
