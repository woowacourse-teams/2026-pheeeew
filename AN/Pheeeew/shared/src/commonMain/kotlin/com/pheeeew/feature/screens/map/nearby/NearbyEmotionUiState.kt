package com.pheeeew.feature.screens.map.nearby

import com.pheeeew.feature.screens.map.monitoring.ContentLoad

data class NearbyEmotionUiState(
    val visible: Boolean = false,
    val items: List<NearbyEmotionItemUiModel> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val selectedId: Long? = null,
    val pendingIds: Set<Long> = emptySet(),
    val blockId: Long? = null,
    val message: String? = null,
    val revision: Long = 0,
    val audioLoadingId: Long? = null,
    val contentLoad: ContentLoad? = null,
)
