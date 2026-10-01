package com.pheeeew.data.remote.group

import com.pheeeew.domain.model.ranking.PressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import kotlin.time.Instant

class PressRankingContractException(
    val field: String,
) : IllegalArgumentException("프레스 랭킹 응답 계약을 확인할 수 없습니다: $field")

object PressRankingMapper {
    fun toDomain(dto: PressRankingResponseDto): PressRanking {
        if (dto.weeksAgo < 0) throw PressRankingContractException("weeksAgo")
        if (runCatching { Instant.parse(dto.startAt) }.isFailure) throw PressRankingContractException("startAt")
        if (runCatching { Instant.parse(dto.endAt) }.isFailure) throw PressRankingContractException("endAt")
        return PressRanking(
            weeksAgo = dto.weeksAgo,
            startAt = dto.startAt,
            endAt = dto.endAt,
            hasPrevious = dto.hasPrevious,
            items = dto.items.map(::toDomain),
        )
    }

    private fun toDomain(dto: PressRankingItemResponseDto): PressRankingItem {
        if (dto.rank < 1) throw PressRankingContractException("items.rank")
        if (!UUID_PATTERN.matches(dto.groupId)) throw PressRankingContractException("items.groupId")
        if (dto.name.isBlank()) throw PressRankingContractException("items.name")
        if (dto.score < 0) throw PressRankingContractException("items.score")
        return PressRankingItem(dto.rank, dto.groupId, dto.name, dto.score, dto.mine)
    }

    private val UUID_PATTERN =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
