package com.pheeeew.domain.model.group

data class GroupExploreItem(
    val id: GroupId,
    val name: String,
    val description: String?,
    val stamp: GroupStamp,
    val memberCount: Long,
    val joinType: GroupJoinType,
    val joined: Boolean,
) {
    init {
        require(name.isNotEmpty())
        require(memberCount >= 0)
    }
}

data class GroupExplorePage(
    val items: List<GroupExploreItem>,
    val nextCursor: String?,
) {
    init {
        require(nextCursor == null || nextCursor.isNotBlank())
        require(items.isNotEmpty() || nextCursor == null)
    }
}
