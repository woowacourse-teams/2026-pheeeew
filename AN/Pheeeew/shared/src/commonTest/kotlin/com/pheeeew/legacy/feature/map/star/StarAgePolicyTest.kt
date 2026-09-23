@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.feature.map.star

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class StarAgePolicyTest {
    private val seoulMidnightOnSeptember16 = Instant.parse("2026-09-15T15:00:00Z")

    @Test
    fun `Clock을 주입하면 주입한 현재 시각을 사용한다`() {
        val policy =
            StarAgePolicy(
                clock =
                    object : Clock {
                        override fun now(): Instant = seoulMidnightOnSeptember16
                    },
            )

        assertEquals(
            StarAgeStage.Fresh,
            policy.stageOf(Instant.parse("2026-09-15T15:00:00Z")),
        )
    }

    @Test
    fun `생성 시각이 없으면 Unknown이다`() {
        assertEquals(StarAgeStage.Unknown, StarAgePolicy.stageOf(createdAt = null, now = seoulMidnightOnSeptember16))
    }

    @Test
    fun `생성 당일은 Fresh이다`() {
        assertEquals(
            StarAgeStage.Fresh,
            StarAgePolicy.stageOf(
                createdAt = Instant.parse("2026-09-16T03:00:00Z"),
                now = Instant.parse("2026-09-16T14:59:59Z"),
            ),
        )
    }

    @Test
    fun `한국 날짜 기준 4일째 23시 59분은 Fresh이다`() {
        assertEquals(
            StarAgeStage.Fresh,
            StarAgePolicy.stageOf(
                createdAt = Instant.parse("2026-09-11T15:00:00Z"),
                now = Instant.parse("2026-09-16T14:59:59Z"),
            ),
        )
    }

    @Test
    fun `한국 날짜 기준 5일째 자정은 Warm이다`() {
        val createdAt = Instant.parse("2026-09-16T14:59:00Z")
        val now = Instant.parse("2026-09-20T15:00:00Z")

        assertEquals(StarAgeStage.Warm, StarAgePolicy.stageOf(createdAt, now))
        assertEquals(Instant.parse("2026-09-25T15:00:00Z"), StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    @Test
    fun `한국 날짜 기준 9일째 23시 59분은 Warm이다`() {
        assertEquals(
            StarAgeStage.Warm,
            StarAgePolicy.stageOf(
                createdAt = Instant.parse("2026-09-11T15:00:00Z"),
                now = Instant.parse("2026-09-20T14:59:59Z"),
            ),
        )
    }

    @Test
    fun `한국 날짜 기준 10일째 자정은 Deep이다`() {
        val createdAt = Instant.parse("2026-09-16T03:00:00Z")
        val now = Instant.parse("2026-09-25T15:00:00Z")

        assertEquals(StarAgeStage.Deep, StarAgePolicy.stageOf(createdAt, now))
        assertNull(StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    @Test
    fun `14일 이상인 별도 Deep을 유지한다`() {
        assertEquals(
            StarAgeStage.Deep,
            StarAgePolicy.stageOf(
                createdAt = Instant.parse("2026-09-01T03:00:00Z"),
                now = Instant.parse("2026-09-16T03:00:00Z"),
            ),
        )
    }

    @Test
    fun `한국 날짜가 달라지면 UTC 날짜와 무관하게 경과일을 계산한다`() {
        assertEquals(
            StarAgeStage.Fresh,
            StarAgePolicy.stageOf(
                createdAt = Instant.parse("2026-09-16T14:30:00Z"),
                now = Instant.parse("2026-09-17T15:00:00Z"),
            ),
        )
    }

    @Test
    fun `미래 생성 시각은 Fresh로 보정하고 생성 한국 날짜 기준 전환 시각을 계산한다`() {
        val createdAt = Instant.parse("2026-09-17T03:00:00Z")
        val now = Instant.parse("2026-09-16T14:59:59Z")

        assertEquals(StarAgeStage.Fresh, StarAgePolicy.stageOf(createdAt, now))
        assertEquals(Instant.parse("2026-09-21T15:00:00Z"), StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    @Test
    fun `Deep과 Unknown은 다음 색상 전환 시각이 없다`() {
        assertNull(
            StarAgePolicy.nextTransitionAt(
                createdAt = Instant.parse("2026-09-01T03:00:00Z"),
                now = Instant.parse("2026-09-16T03:00:00Z"),
            ),
        )
        assertNull(StarAgePolicy.nextTransitionAt(createdAt = null, now = seoulMidnightOnSeptember16))
    }
}
