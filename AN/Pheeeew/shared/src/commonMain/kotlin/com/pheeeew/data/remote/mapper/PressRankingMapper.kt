package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.DailyPressRankingResponseDto
import com.pheeeew.data.remote.dto.PressRankingItemResponseDto
import com.pheeeew.data.remote.dto.PressRankingResponseDto
import com.pheeeew.domain.model.ranking.DailyPressRanking
import com.pheeeew.domain.model.ranking.PressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import kotlin.time.Instant

class PressRankingContractException(
    val field: String,
) : IllegalArgumentException("프레스 랭킹 응답 계약을 확인할 수 없습니다: $field")

object PressRankingMapper {
    fun toDailyDomain(dto: DailyPressRankingResponseDto): DailyPressRanking {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(dto.date)) throw PressRankingContractException("date")
        val start =
            runCatching { Instant.parse(dto.startAt) }.getOrNull() ?: throw PressRankingContractException("startAt")
        val end = runCatching { Instant.parse(dto.endAt) }.getOrNull() ?: throw PressRankingContractException("endAt")
        if (start >= end) throw PressRankingContractException("endAt")
        return DailyPressRanking(
            date = dto.date,
            startAt = dto.startAt,
            endAt = dto.endAt,
            items = dto.items.map(::toDomain),
            me = dto.me?.let(::toDomain),
        )
    }

    fun toDomain(dto: PressRankingResponseDto): PressRanking {
        if (dto.weeksAgo !in 0..520) throw PressRankingContractException("weeksAgo")
        val start =
            runCatching { Instant.parse(dto.startAt) }.getOrNull() ?: throw PressRankingContractException("startAt")
        val end = runCatching { Instant.parse(dto.endAt) }.getOrNull() ?: throw PressRankingContractException("endAt")
        if (start >= end) throw PressRankingContractException("endAt")
        return PressRanking(
            weeksAgo = dto.weeksAgo,
            startAt = dto.startAt,
            endAt = dto.endAt,
            hasPrevious = dto.weeksAgo < 520,
            items = dto.items.map(::toDomain),
            me = dto.me?.let(::toDomain),
        )
    }

    private fun toDomain(dto: PressRankingItemResponseDto): PressRankingItem {
        if (dto.rank < 1) throw PressRankingContractException("items.rank")
        if (dto.nickname.isBlank()) throw PressRankingContractException("items.nickname")
        if (dto.pressCount < 0) throw PressRankingContractException("items.pressCount")
        return PressRankingItem(dto.rank, dto.nickname, dto.pressCount)
    }
}
