package com.pheeeew.data.remote.group.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupExploreResponseDto(
    val items: List<GroupExploreItemDto>,
    val hasNext: Boolean,
    val nextCursor: String?,
)

@Serializable
data class GroupExploreItemDto(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val stamp: GroupStampResponseDto,
    val memberCount: Long,
    val joinType: String,
    val joined: Boolean,
)
