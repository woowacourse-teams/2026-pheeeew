package com.pheeeew.data.remote.group.mapper

import com.pheeeew.data.remote.group.dto.GroupDetailResponseDto
import com.pheeeew.data.remote.group.dto.GroupResponseDto
import com.pheeeew.domain.model.group.GroupDetail

object GroupDetailResponseMapper {
    fun toDomain(dto: GroupDetailResponseDto): GroupDetail {
        validateMetric("weeklyStampCount", dto.weeklyStampCount, dto.weeklyStampRank)
        validateMetric("weeklyEmotionPressCount", dto.weeklyEmotionPressCount, dto.weeklyEmotionPressRank)
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
            weeklyStampCount = dto.weeklyStampCount,
            weeklyStampRank = dto.weeklyStampRank,
            weeklyEmotionPressCount = dto.weeklyEmotionPressCount,
            weeklyEmotionPressRank = dto.weeklyEmotionPressRank,
        )
    }

    private fun validateMetric(
        countField: String,
        count: Long,
        rank: Int?,
    ) {
        if (count < 0L) throw GroupContractException(countField)
        val rankField = countField.replace("Count", "Rank")
        if (rank != null && rank <= 0) throw GroupContractException(rankField)
        if (count == 0L && rank != null) throw GroupContractException(rankField)
    }
}
