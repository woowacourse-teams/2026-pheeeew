package com.pheeeew.feature.map.star

import com.pheeeew.core.designsystem.DesignSystemColors

/** 네이티브 지도 renderer가 공통으로 소비하는 별 시각 계약입니다. */
data class StarVisual(
    val imageKey: String,
    val colorHex: String,
    val scale: Float,
    val opacity: Float,
)

/** 별 생애 단계와 renderer 세부값을 연결하는 공통 정책입니다. */
object StarVisualPolicy {
    const val FRESH_IMAGE_KEY = "sigh-star-fresh"
    const val WARM_IMAGE_KEY = "sigh-star-warm"
    const val DEEP_IMAGE_KEY = "sigh-star-deep"
    const val UNKNOWN_IMAGE_KEY = "sigh-star-unknown"

    val allVisuals: List<StarVisual> =
        listOf(
            visualFor(StarAgeStage.Fresh),
            visualFor(StarAgeStage.Warm),
            visualFor(StarAgeStage.Deep),
            visualFor(StarAgeStage.Unknown),
        )

    fun visualFor(stage: StarAgeStage): StarVisual =
        when (stage) {
            StarAgeStage.Fresh -> {
                StarVisual(
                    imageKey = FRESH_IMAGE_KEY,
                    colorHex = DesignSystemColors.STAR_FRESH_HEX,
                    scale = 0.58f,
                    opacity = 1f,
                )
            }

            StarAgeStage.Warm -> {
                StarVisual(
                    imageKey = WARM_IMAGE_KEY,
                    colorHex = DesignSystemColors.STAR_WARM_HEX,
                    scale = 0.58f,
                    opacity = 1f,
                )
            }

            StarAgeStage.Deep -> {
                StarVisual(
                    imageKey = DEEP_IMAGE_KEY,
                    colorHex = DesignSystemColors.STAR_DEEP_HEX,
                    scale = 0.58f,
                    opacity = 1f,
                )
            }

            StarAgeStage.Unknown -> {
                StarVisual(
                    imageKey = UNKNOWN_IMAGE_KEY,
                    colorHex = DesignSystemColors.STAR_UNKNOWN_HEX,
                    scale = 0.58f,
                    opacity = 0.85f,
                )
            }
        }
}
