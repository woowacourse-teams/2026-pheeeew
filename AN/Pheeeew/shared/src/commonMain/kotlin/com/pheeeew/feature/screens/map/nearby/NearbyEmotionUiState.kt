package com.pheeeew.feature.screens.map.nearby

import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel

data class NearbyEmotionUiState(
    val visible: Boolean = false,
    val items: List<NearbyEmotionItemUiModel> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val selectedId: Long? = null,
    val pendingIds: Set<Long> = emptySet(),
    val groupId: String = ALL_GROUPS,
    val groups: List<GroupSelectorGroupUiModel> = listOf(ALL_GROUP_OPTION),
    val groupsLoading: Boolean = false,
    val groupsError: Boolean = false,
    val groupSelectorVisible: Boolean = false,
    val pendingGroupId: String = ALL_GROUPS,
    val dialProgress: Float = 0f,
    val blockId: Long? = null,
    val message: String? = null,
    val revision: Long = 0,
    val audioLoadingId: Long? = null,
)

internal const val ALL_GROUPS = "all"
internal val ALL_GROUP_OPTION = GroupSelectorGroupUiModel(ALL_GROUPS, "전체", "전체", showStamp = false)
