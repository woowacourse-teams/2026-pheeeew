package com.pheeeew.feature.map.star

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 별 생애 단계와 다음 경계 시각을 계산하는 순수 정책입니다.
 * 현재 시각을 외부에서 받아 결정적으로 테스트할 수 있도록 합니다.
 */
object StarAgePolicy {
    private val FRESH_DURATION = 1.hours
    private val DEEP_DURATION = 24.hours

    fun stageOf(
        createdAt: Instant?,
        now: Instant,
    ): StarAgeStage {
        if (createdAt == null) return StarAgeStage.Unknown

        return when (elapsedSince(createdAt, now)) {
            in Duration.ZERO..<FRESH_DURATION -> StarAgeStage.Fresh
            in FRESH_DURATION..<DEEP_DURATION -> StarAgeStage.Warm
            else -> StarAgeStage.Deep
        }
    }

    fun nextTransitionAt(
        createdAt: Instant?,
        now: Instant,
    ): Instant? {
        val transition =
            when (stageOf(createdAt, now)) {
                StarAgeStage.Fresh -> createdAt?.plus(FRESH_DURATION)

                StarAgeStage.Warm -> createdAt?.plus(DEEP_DURATION)

                StarAgeStage.Deep,
                StarAgeStage.Unknown,
                -> null
            }

        return transition?.takeIf { it > now }
    }

    private fun elapsedSince(
        createdAt: Instant,
        now: Instant,
    ): Duration {
        val elapsed = now - createdAt
        return if (elapsed < Duration.ZERO) Duration.ZERO else elapsed
    }
}
