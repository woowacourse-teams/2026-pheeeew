package com.pheeeew.data.remote.group

import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressRankingMapperTest {
    @Test
    fun `API 응답을 기간과 공동 순위를 포함해 매핑한다`() {
        val ranking =
            PressRankingMapper.toDomain(
                PressRankingResponseDto(
                    weeksAgo = 1,
                    startAt = "2026-09-21T00:00:00Z",
                    endAt = "2026-09-28T00:00:00Z",
                    hasPrevious = true,
                    items =
                        listOf(
                            PressRankingItemResponseDto(
                                2,
                                GROUP_ID,
                                "히유 클럽",
                                31,
                                mine = true,
                                stamp = GroupStampResponseDto("히유", "#17191A", "#9DEBD5", "CIRCLE"),
                            ),
                            PressRankingItemResponseDto(2, OTHER_GROUP_ID, "별터 모임", 31),
                        ),
                ),
            )

        assertEquals(1, ranking.weeksAgo)
        assertEquals("2026-09-21T00:00:00Z", ranking.startAt)
        assertEquals("2026-09-28T00:00:00Z", ranking.endAt)
        assertTrue(ranking.hasPrevious)
        assertEquals(2, ranking.items[0].rank)
        assertEquals(31, ranking.items[0].score)
        assertTrue(ranking.items[0].mine)
        assertEquals("히유", ranking.items[0].stamp?.text)
        assertEquals(
            0xFF9DEBD5,
            ranking.items[0]
                .stamp
                ?.backgroundColor
                ?.argb,
        )
        assertFalse(ranking.items[1].mine)
    }

    @Test
    fun `빈 결과를 정상적으로 매핑한다`() {
        val ranking =
            PressRankingMapper.toDomain(
                PressRankingResponseDto(
                    weeksAgo = 0,
                    startAt = "2026-09-28T00:00:00Z",
                    endAt = "2026-10-05T00:00:00Z",
                    hasPrevious = false,
                    items = emptyList(),
                ),
            )

        assertEquals(emptyList(), ranking.items)
        assertFalse(ranking.hasPrevious)
    }

    @Test
    fun `잘못된 항목 계약은 거부한다`() {
        assertFailsWith<PressRankingContractException> {
            PressRankingMapper.toDomain(
                PressRankingResponseDto(
                    weeksAgo = 0,
                    startAt = "2026-09-28T00:00:00Z",
                    endAt = "2026-10-05T00:00:00Z",
                    hasPrevious = false,
                    items = listOf(PressRankingItemResponseDto(1, "bad-id", "그룹", 3)),
                ),
            )
        }
    }

    private companion object {
        const val GROUP_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        const val OTHER_GROUP_ID = "73cbfafa-a5a8-4fc1-a29d-621a7e93be24"
    }
}
