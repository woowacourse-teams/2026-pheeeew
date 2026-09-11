package com.pheeeew.feature.map

import com.pheeeew.feature.map.star.StarAgePolicy
import com.pheeeew.feature.map.star.StarVisualPolicy
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * 에뮬레이터에서 별 생애 색상을 확인하기 위한 임시 지도 fixture입니다.
 * 실제 한숨 데이터와 함께 노출되며, 확인이 끝나면 ENABLED만 false로 전환합니다.
 */
internal object MapDebugStarFixtures {
    const val ENABLED = true

    private val positions =
        listOf(
            RelativePosition(-0.018, -0.020),
            RelativePosition(-0.009, -0.020),
            RelativePosition(0.000, -0.020),
            RelativePosition(0.009, -0.020),
            RelativePosition(0.018, -0.020),
            RelativePosition(-0.018, -0.010),
            RelativePosition(-0.009, -0.010),
            RelativePosition(0.000, -0.010),
            RelativePosition(0.009, -0.010),
            RelativePosition(0.018, -0.010),
            RelativePosition(-0.018, 0.010),
            RelativePosition(-0.009, 0.010),
            RelativePosition(0.000, 0.010),
            RelativePosition(0.009, 0.010),
            RelativePosition(0.018, 0.010),
            RelativePosition(-0.018, 0.020),
            RelativePosition(-0.009, 0.020),
            RelativePosition(0.000, 0.020),
            RelativePosition(0.009, 0.020),
            RelativePosition(0.018, 0.020),
        )

    /** 각 생애 단계가 지도에서 모두 보이도록 0~7일의 생성 시각을 섞습니다. */
    private val agesInDays = listOf(0, 1, 2, 3, 4, 5, 6, 7, 1, 3, 5, 7, 0, 2, 4, 6, 1, 4, 6, 2)

    fun markers(
        center: MapPoint,
        now: Instant,
    ): List<SighMarker> =
        positions.mapIndexed { index, position ->
            val createdAt = now - agesInDays[index].days
            SighMarker(
                id = "debug-star-$index",
                latitude = center.latitude + position.latitudeDelta,
                longitude = center.longitude + position.longitudeDelta,
                visual = StarVisualPolicy.visualFor(StarAgePolicy.stageOf(createdAt, now)),
            )
        }

    private data class RelativePosition(
        val latitudeDelta: Double,
        val longitudeDelta: Double,
    )
}
