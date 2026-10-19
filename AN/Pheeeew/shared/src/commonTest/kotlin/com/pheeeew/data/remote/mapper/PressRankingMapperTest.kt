package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.PressRankingItemResponseDto
import com.pheeeew.data.remote.dto.PressRankingResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressRankingMapperTest {
    @Test
    fun `공동 순위와 Long 프레스 수와 100위 밖 내 순위를 보존한다`() {
        val ranking =
            PressRankingMapper.toDomain(
                response().copy(
                    items =
                        listOf(
                            PressRankingItemResponseDto(2, "익명", 3_000_000_000L),
                            PressRankingItemResponseDto(2, "익명", 3_000_000_000L),
                        ),
                    me = PressRankingItemResponseDto(150, "한숨많은곰", 31),
                ),
            )
        assertEquals(listOf(2, 2), ranking.items.map { it.rank })
        assertEquals(3_000_000_000L, ranking.items.first().pressCount)
        assertEquals(150, ranking.me?.rank)
        assertEquals("한숨많은곰", ranking.me?.nickname)
        assertTrue(ranking.hasPrevious)
    }

    @Test
    fun `빈 결과와 미참여 내 순위를 보존하고 520주에서 이전 이동을 막는다`() {
        val ranking = PressRankingMapper.toDomain(response().copy(weeksAgo = 520))
        assertTrue(ranking.items.isEmpty())
        assertNull(ranking.me)
        assertFalse(ranking.hasPrevious)
    }

    @Test
    fun `잘못된 항목과 기간 계약을 거부한다`() {
        val invalid =
            listOf(
                response().copy(weeksAgo = 521),
                response().copy(startAt = "invalid"),
                response().copy(endAt = response().startAt),
                response().copy(items = listOf(PressRankingItemResponseDto(0, "닉네임", 1))),
                response().copy(items = listOf(PressRankingItemResponseDto(1, "", 1))),
                response().copy(me = PressRankingItemResponseDto(1, "닉네임", -1)),
            )
        invalid.forEach { dto -> assertFailsWith<PressRankingContractException> { PressRankingMapper.toDomain(dto) } }
    }

    private fun response() =
        PressRankingResponseDto(
            weeksAgo = 0,
            startAt = "2026-10-05T15:00:00Z",
            endAt = "2026-10-12T15:00:00Z",
            items = emptyList(),
            me = null,
        )
}
