package com.pheeeew.data.remote.group.mapper

import com.pheeeew.data.remote.group.dto.GroupDetailResponseDto
import com.pheeeew.data.remote.group.dto.GroupPressCountResponseDto
import com.pheeeew.data.remote.group.dto.GroupResponseDto
import com.pheeeew.domain.model.group.GroupDetail
import com.pheeeew.domain.model.group.GroupPressCounts
import com.pheeeew.domain.model.group.GroupPressState

object GroupDetailResponseMapper {
    fun toDomain(dto: GroupDetailResponseDto): GroupDetail {
        val group =
            GroupResponseMapper.toDomain(
                GroupResponseDto(
                    groupId = dto.groupId,
                    name = dto.name,
                    description = dto.description,
                    inviteCode = dto.inviteCode,
                    role = dto.role,
                    memberCount = dto.memberCount,
                    stamp = dto.stamp,
                ),
            )
        if (dto.inviteCode.isBlank()) throw GroupContractException("inviteCode")

        return GroupDetail(
            group = group,
            todayPresses = dto.todayPresses.toDomain(),
            weeklyScore = dto.weeklyScore,
            weeklyRank = dto.weeklyRank,
        )
    }

    private fun GroupPressCountResponseDto.toDomain(): GroupPressCounts {
        val requiredKeys = GroupPressState.entries.map { it.name }.toSet()
        if (counts.keys != requiredKeys) throw GroupContractException("todayPresses.counts")

        val parsedCounts =
            GroupPressState.entries.associateWith { state ->
                counts.getValue(state.name)
            }
        return try {
            GroupPressCounts(counts = parsedCounts, total = total)
        } catch (_: IllegalArgumentException) {
            throw GroupContractException("todayPresses")
        }
    }
}
