package com.pheeeew.data.remote.group.mapper

import com.pheeeew.data.remote.group.dto.GroupMembersResponseDto
import com.pheeeew.domain.model.group.GroupMember
import com.pheeeew.domain.model.group.GroupMembersPage
import com.pheeeew.domain.model.group.GroupRole
import kotlin.time.Instant

object GroupMembersResponseMapper {
    fun toDomain(dto: GroupMembersResponseDto): GroupMembersPage {
        if (dto.hasNext) {
            if (dto.nextCursor.isNullOrBlank()) throw GroupContractException("nextCursor")
            if (dto.items.isEmpty()) throw GroupContractException("items")
        } else if (dto.nextCursor != null) {
            throw GroupContractException("nextCursor")
        }
        return GroupMembersPage(
            items =
                dto.items.map { item ->
                    val role =
                        GroupRole.entries.firstOrNull { it.name == item.role && it != GroupRole.NONE }
                            ?: throw GroupContractException("role")
                    if (runCatching { Instant.parse(item.joinedAt) }.isFailure) throw GroupContractException("joinedAt")
                    GroupMember(item.nickname, role, item.joinedAt)
                },
            nextCursor = dto.nextCursor,
        )
    }
}
