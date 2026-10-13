package com.pheeeew.feature.screens.group.members

data class GroupMembersUiState(
    val content: GroupMembersContent = GroupMembersContent.Loading,
)

sealed interface GroupMembersContent {
    data object Loading : GroupMembersContent

    data object Empty : GroupMembersContent

    data object Failed : GroupMembersContent

    data object Forbidden : GroupMembersContent

    data object NotFound : GroupMembersContent

    data class Ready(
        val items: List<GroupMemberUiModel>,
        val nextCursor: String?,
        val generation: Long,
        val isLoadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
    ) : GroupMembersContent {
        init {
            require(items.isNotEmpty()) { "Ready 상태에는 멤버가 한 명 이상 있어야 합니다." }
        }

        val hasNextPage: Boolean
            get() = nextCursor != null
    }
}
