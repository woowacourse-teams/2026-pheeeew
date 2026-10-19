package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.GroupExploreResponseDto
import com.pheeeew.data.remote.mapper.GroupResponseMapper.toDomain
import com.pheeeew.domain.model.group.GroupExploreItem
import com.pheeeew.domain.model.group.GroupExplorePage
import com.pheeeew.domain.model.group.GroupJoinType

object GroupExploreResponseMapper {
    fun toDomain(dto: GroupExploreResponseDto): GroupExplorePage {
        if (dto.hasNext) {
            if (dto.nextCursor.isNullOrBlank()) throw GroupContractException("nextCursor")
            if (dto.items.isEmpty()) throw GroupContractException("items")
        } else if (dto.nextCursor != null) {
            throw GroupContractException("nextCursor")
        }
        return GroupExplorePage(
            items =
                dto.items.map { item ->
                    GroupExploreItem(
                        id = GroupResponseMapper.parseId(item.groupId),
                        name = GroupResponseMapper.parseName(item.name),
                        description = item.description,
                        stamp = item.stamp.toDomain(),
                        memberCount = GroupResponseMapper.parseMemberCount(item.memberCount),
                        joinType =
                            GroupJoinType.entries.firstOrNull { it.name == item.joinType }
                                ?: throw GroupContractException("joinType"),
                        joined = item.joined,
                    )
                },
            nextCursor = dto.nextCursor,
        )
    }
}
