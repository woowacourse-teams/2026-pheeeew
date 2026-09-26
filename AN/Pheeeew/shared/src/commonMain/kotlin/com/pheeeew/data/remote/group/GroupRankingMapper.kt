package com.pheeeew.data.remote.group

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.model.ranking.GroupRanking
import com.pheeeew.domain.model.ranking.GroupRankingItem
import kotlin.time.Instant

class GroupRankingContractException(
    val field: String,
) : IllegalArgumentException("그룹 랭킹 응답 계약을 확인할 수 없습니다: $field")

object GroupRankingMapper {
    fun toDomain(dto: GroupRankingResponseDto): GroupRanking {
        if (dto.weeksAgo < 0) throw GroupRankingContractException("weeksAgo")
        if (runCatching { Instant.parse(dto.startAt) }.isFailure) throw GroupRankingContractException("startAt")
        if (runCatching { Instant.parse(dto.endAt) }.isFailure) throw GroupRankingContractException("endAt")
        return GroupRanking(
            weeksAgo = dto.weeksAgo,
            startAt = dto.startAt,
            endAt = dto.endAt,
            hasPrevious = dto.hasPrevious,
            items = dto.items.map(::toDomain),
        )
    }

    private fun toDomain(dto: GroupRankingItemResponseDto): GroupRankingItem =
        GroupRankingItem(
            rank = dto.rank,
            groupId = GroupId.parse(dto.groupId) ?: throw GroupRankingContractException("items.groupId"),
            name = dto.name.takeIf(String::isNotBlank) ?: throw GroupRankingContractException("items.name"),
            stamp = dto.stamp.toDomain(),
            score = dto.score,
        )

    private fun GroupStampResponseDto.toDomain(): GroupStamp =
        GroupStamp(
            text = text,
            textColor =
                StampColor.parseServerValue(
                    textColor,
                ) ?: throw GroupRankingContractException("stamp.textColor"),
            backgroundColor =
                StampColor.parseServerValue(backgroundColor)
                    ?: throw GroupRankingContractException("stamp.backgroundColor"),
            frame =
                GroupStampFrame.entries.firstOrNull { it.name == frame }
                    ?: throw GroupRankingContractException("stamp.frame"),
        )
}
