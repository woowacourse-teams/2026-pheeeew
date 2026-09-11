@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map.star

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class StarAgePolicyTest {
    private val now = Instant.parse("2026-09-02T12:00:00Z")

    @Test
    fun `Clock을 주입하면 주입한 현재 시각을 사용한다`() {
        val policy =
            StarAgePolicy(
                clock =
                    object : Clock {
                        override fun now(): Instant = now
                    },
            )

        assertEquals(
            StarAgeStage.Warm,
            policy.stageOf(Instant.parse("2026-09-02T11:00:00Z")),
        )
    }

    /** 생성 시각이 없는 데이터가 Unknown으로 분류되는지 확인합니다. */
    @Test
    fun `생성 시각이 없으면 Unknown이다`() {
        assertEquals(StarAgeStage.Unknown, StarAgePolicy.stageOf(createdAt = null, now = now))
    }

    /** 미래 생성 시각을 Fresh로 보정하고 전환 시각을 계산하는지 확인합니다. */
    @Test
    fun `미래 생성 시각은 Fresh로 보정한다`() {
        val createdAt = Instant.parse("2026-09-02T13:00:00Z")

        assertEquals(StarAgeStage.Fresh, StarAgePolicy.stageOf(createdAt, now))
        assertEquals(Instant.parse("2026-09-02T14:00:00Z"), StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    /** 한 시간 미만의 별이 Fresh로 분류되는지 확인합니다. */
    @Test
    fun `한 시간 미만은 Fresh이다`() {
        val createdAt = Instant.parse("2026-09-02T11:00:00.001Z")

        assertEquals(StarAgeStage.Fresh, StarAgePolicy.stageOf(createdAt, now))
        assertEquals(Instant.parse("2026-09-02T12:00:00.001Z"), StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    /** 정확히 한 시간 경계에서 Warm으로 전환되는지 확인합니다. */
    @Test
    fun `정확히 한 시간은 Warm이다`() {
        val createdAt = Instant.parse("2026-09-02T11:00:00Z")

        assertEquals(StarAgeStage.Warm, StarAgePolicy.stageOf(createdAt, now))
        assertEquals(Instant.parse("2026-09-03T11:00:00Z"), StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    /** 정확히 스물네 시간 경계에서 Deep으로 전환되는지 확인합니다. */
    @Test
    fun `정확히 스물네 시간은 Deep이다`() {
        val createdAt = Instant.parse("2026-09-01T12:00:00Z")

        assertEquals(StarAgeStage.Deep, StarAgePolicy.stageOf(createdAt, now))
        assertNull(StarAgePolicy.nextTransitionAt(createdAt, now))
    }

    /** 마지막 단계가 일주일 이후에도 유지되는지 확인합니다. */
    @Test
    fun `일주일이 지나도 Deep을 유지한다`() {
        val createdAt = Instant.parse("2026-08-20T12:00:00Z")

        assertEquals(StarAgeStage.Deep, StarAgePolicy.stageOf(createdAt, now))
    }

    /** 시간이 흐를 때 생애 단계가 역행하지 않는지 확인합니다. */
    @Test
    fun `시간이 증가할 때 단계가 역행하지 않는다`() {
        val createdAt = Instant.parse("2026-09-01T12:00:00Z")
        val later = Instant.parse("2026-09-03T12:00:00Z")

        assertEquals(StarAgeStage.Deep, StarAgePolicy.stageOf(createdAt, now))
        assertEquals(StarAgeStage.Deep, StarAgePolicy.stageOf(createdAt, later))
    }
}
