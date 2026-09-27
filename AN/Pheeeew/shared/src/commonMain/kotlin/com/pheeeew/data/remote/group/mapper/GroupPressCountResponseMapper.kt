package com.pheeeew.data.remote.group.mapper

import com.pheeeew.data.remote.group.dto.GroupPressCountResponseDto
import com.pheeeew.domain.model.group.GroupPressCounts
import com.pheeeew.domain.model.group.GroupPressState

object GroupPressCountResponseMapper {
    fun toDomain(dto: GroupPressCountResponseDto): GroupPressCounts {
        val requiredKeys = GroupPressState.entries.map { it.name }.toSet()
        if (dto.counts.keys != requiredKeys) throw GroupContractException("counts")

        val parsedCounts = GroupPressState.entries.associateWith { state -> dto.counts.getValue(state.name) }
        return try {
            GroupPressCounts(counts = parsedCounts, total = dto.total)
        } catch (_: IllegalArgumentException) {
            throw GroupContractException("counts")
        }
    }
}
