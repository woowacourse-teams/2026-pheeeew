package com.pheeeew.feature.map.star

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 별 생애 단계와 다음 경계 시각을 계산하는 정책입니다.
 * [clock]을 주입하면 production에서는 시스템 시각을 사용하고 테스트에서는 고정 시각을 사용할 수 있습니다.
 */
class StarAgePolicy(
    private val clock: Clock = Clock.System,
) {
    /** 주입된 현재 시각을 기준으로 별의 생애 단계를 계산합니다. */
    fun stageOf(createdAt: Instant?): StarAgeStage =
        stageOf(
            createdAt = createdAt,
            now = clock.now(),
        )

    /** 주입된 현재 시각을 기준으로 다음 색상 단계 전환 시각을 계산합니다. */
    fun nextTransitionAt(createdAt: Instant?): Instant? =
        nextTransitionAt(
            createdAt = createdAt,
            now = clock.now(),
        )

    companion object {
        private val FRESH_DURATION = 1.hours
        private val DEEP_DURATION = 24.hours

        /** 전달받은 현재 시각을 기준으로 별의 생애 단계를 계산합니다. */
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

        /** 전달받은 현재 시각을 기준으로 다음 색상 단계 전환 시각을 계산합니다. */
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
}
