@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.feature.map.sighlist

import com.pheeeew.legacy.domain.model.geo.Coordinate
import com.pheeeew.legacy.domain.model.sigh.Sigh
import com.pheeeew.legacy.feature.map.star.StarAgeStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SighListItemUiModelTest {
    private val coordinate = Coordinate(latitude = 37.5, longitude = 127.0)
    private val now = Instant.parse("2026-09-20T15:00:00Z")

    @Test
    fun `생성 날짜가 같으면 ID가 달라도 같은 별 단계다`() {
        val first = createSigh(id = 1L, createdAt = Instant.parse("2026-09-18T03:00:00Z"))
        val second = createSigh(id = 2L, createdAt = first.createdAt)

        assertEquals(first.toSighListItemUiModel(now).starStage, second.toSighListItemUiModel(now).starStage)
        assertEquals(StarAgeStage.Fresh, first.toSighListItemUiModel(now).starStage)
    }

    @Test
    fun `같은 ID라도 현재 시각이 경계를 지나면 별 단계가 바뀐다`() {
        val sigh = createSigh(id = 1L, createdAt = Instant.parse("2026-09-16T14:59:00Z"))

        assertEquals(
            StarAgeStage.Fresh,
            sigh.toSighListItemUiModel(Instant.parse("2026-09-20T14:59:59Z")).starStage,
        )
        assertEquals(
            StarAgeStage.Warm,
            sigh.toSighListItemUiModel(Instant.parse("2026-09-20T15:00:00Z")).starStage,
        )
    }

    @Test
    fun `ID를 변경해도 생성 날짜가 같으면 별 단계가 바뀌지 않는다`() {
        val createdAt = Instant.parse("2026-09-12T03:00:00Z")
        val original = createSigh(id = 10L, createdAt = createdAt)
        val changedId = createSigh(id = 999L, createdAt = createdAt)

        assertEquals(
            original.toSighListItemUiModel(now).starStage,
            changedId.toSighListItemUiModel(now).starStage,
        )
    }

    @Test
    fun `메모와 닉네임 및 상대 시간 변환은 유지된다`() {
        val sigh =
            createSigh(
                id = 1L,
                createdAt = Instant.parse("2026-09-20T14:30:00Z"),
                nickname = "테스트 사용자",
                memo = "숨을 고르고 있어요",
            )

        val item = sigh.toSighListItemUiModel(now)

        assertEquals("테스트 사용자", item.nickname)
        assertEquals("숨을 고르고 있어요", item.memo)
        assertEquals("30분 전", item.relativeTime)
    }

    @Test
    fun `메모가 없으면 빈 메모 표시와 상태를 사용한다`() {
        val item = createSigh(id = 1L, createdAt = now, memo = " ").toSighListItemUiModel(now)

        assertEquals(EMPTY_SIGH_MEMO, item.memo)
        assertEquals(false, item.hasMemo)
    }

    private fun createSigh(
        id: Long,
        createdAt: Instant,
        nickname: String = "사용자",
        memo: String? = "메모",
    ): Sigh =
        Sigh(
            id = id,
            coordinate = coordinate,
            memo = memo,
            nickname = nickname,
            createdAt = createdAt,
        )
}
