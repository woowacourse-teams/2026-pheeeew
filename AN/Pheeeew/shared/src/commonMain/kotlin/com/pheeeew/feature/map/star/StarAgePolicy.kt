package com.pheeeew.feature.map.star

import kotlin.time.Clock
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
        private const val SECONDS_PER_DAY = 86_400L
        private const val SEOUL_OFFSET_SECONDS = 9L * 60L * 60L
        private const val FRESH_END_DAY = 4L
        private const val WARM_END_DAY = 9L
        private const val WARM_TRANSITION_DAY = 5L
        private const val DEEP_TRANSITION_DAY = 10L

        /** 전달받은 현재 시각을 기준으로 별의 생애 단계를 계산합니다. */
        fun stageOf(
            createdAt: Instant?,
            now: Instant,
        ): StarAgeStage {
            if (createdAt == null) return StarAgeStage.Unknown

            return when (ageInKoreanCalendarDays(createdAt, now)) {
                in 0L..FRESH_END_DAY -> StarAgeStage.Fresh
                in (FRESH_END_DAY + 1)..WARM_END_DAY -> StarAgeStage.Warm
                else -> StarAgeStage.Deep
            }
        }

        /** 전달받은 현재 시각을 기준으로 다음 색상 단계 전환 시각을 계산합니다. */
        fun nextTransitionAt(
            createdAt: Instant?,
            now: Instant,
        ): Instant? {
            val created = createdAt ?: return null
            val transitionDay =
                when (stageOf(created, now)) {
                    StarAgeStage.Fresh -> WARM_TRANSITION_DAY

                    StarAgeStage.Warm -> DEEP_TRANSITION_DAY

                    StarAgeStage.Deep,
                    StarAgeStage.Unknown,
                    -> return null
                }
            val transition = koreanMidnight(createdKoreanEpochDay = koreanEpochDay(created) + transitionDay)
            return transition.takeIf { it > now }
        }

        private fun ageInKoreanCalendarDays(
            createdAt: Instant,
            now: Instant,
        ): Long = (koreanEpochDay(now) - koreanEpochDay(createdAt)).coerceAtLeast(0L)

        private fun koreanEpochDay(instant: Instant): Long {
            val shiftedEpochSeconds = instant.epochSeconds + SEOUL_OFFSET_SECONDS
            return floorDiv(shiftedEpochSeconds, SECONDS_PER_DAY)
        }

        private fun koreanMidnight(createdKoreanEpochDay: Long): Instant =
            Instant.fromEpochSeconds(
                createdKoreanEpochDay * SECONDS_PER_DAY - SEOUL_OFFSET_SECONDS,
            )

        private fun floorDiv(
            dividend: Long,
            divisor: Long,
        ): Long {
            val quotient = dividend / divisor
            val remainder = dividend % divisor
            return if (remainder < 0) quotient - 1 else quotient
        }
    }
}
