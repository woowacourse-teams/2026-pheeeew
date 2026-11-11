package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.PressEventAttemptResponseDto
import com.pheeeew.data.remote.dto.PressEventRankingResponseDto
import com.pheeeew.domain.model.pressevent.PressEventAttempt
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.domain.model.pressevent.PressEventLeader
import com.pheeeew.domain.model.pressevent.PressEventMyRank
import com.pheeeew.domain.model.pressevent.PressEventRankingEntry
import com.pheeeew.domain.model.pressevent.PressEventRankingPage
import kotlin.time.Instant

internal object PressEventMapper {
    fun attempt(
        dto: PressEventAttemptResponseDto,
        request: PressEventAttempt,
    ): PressEventAttemptResult {
        require(dto.attemptId == request.requestId && dto.total == request.total)
        score(dto.bestScore)
        require(dto.bestScore >= dto.total && dto.rank > 0)
        Instant.parse(dto.rankedAt)
        return PressEventAttemptResult(dto.attemptId, dto.total, dto.bestScore, dto.rank, dto.rankedAt, dto.replayed)
    }

    fun rankings(dto: PressEventRankingResponseDto): PressEventRankingPage {
        Instant.parse(dto.rankedAt)
        require(if (dto.rankings.hasNext) !dto.rankings.nextCursor.isNullOrBlank() else dto.rankings.nextCursor == null)
        val leader =
            dto.leader?.let {
                participant(it.publicId, it.nickname, it.bestScore)
                PressEventLeader(it.publicId, it.nickname, it.bestScore)
            }
        val items =
            dto.rankings.items.map {
                participant(it.publicId, it.nickname, it.bestScore)
                require(it.rank > 0)
                PressEventRankingEntry(it.rank, it.publicId, it.nickname, it.bestScore, it.mine)
            }
        require(items.map { it.publicId }.distinct().size == items.size)
        val me =
            dto.me?.let {
                score(it.bestScore)
                require(it.rank > 0)
                PressEventMyRank(it.bestScore, it.rank)
            }
        return PressEventRankingPage(leader, items, dto.rankings.hasNext, dto.rankings.nextCursor, me, dto.rankedAt)
    }

    private fun score(value: Long) {
        require(value >= 0L)
    }

    private fun participant(
        id: String,
        nickname: String,
        count: Long,
    ) {
        require(id.isNotBlank() && nickname.isNotBlank())
        score(count)
    }
}
