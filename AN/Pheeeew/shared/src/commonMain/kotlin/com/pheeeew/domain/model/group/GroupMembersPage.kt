package com.pheeeew.domain.model.group

data class GroupMember(
    val nickname: String?,
    val role: GroupRole,
    val joinedAt: String,
)

data class GroupMembersPage(
    val items: List<GroupMember>,
    val nextCursor: String?,
)
