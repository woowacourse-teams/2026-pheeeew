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
    const val BLUE_IMAGE_KEY = "sigh-star-blue"
    const val EXISTING_IMAGE_KEY = "sigh-star-existing"
    const val ORANGE_IMAGE_KEY = "sigh-star-orange"
    const val UNKNOWN_IMAGE_KEY = "sigh-star-unknown"

    private val palette: List<StarVisual> =
        listOf(
            StarVisual(BLUE_IMAGE_KEY, DesignSystemColors.STAR_BLUE_HEX, scale = 0.58f, opacity = 1f),
            StarVisual(EXISTING_IMAGE_KEY, DesignSystemColors.STAR_EXISTING_HEX, scale = 0.58f, opacity = 1f),
            StarVisual(ORANGE_IMAGE_KEY, DesignSystemColors.STAR_ORANGE_HEX, scale = 0.58f, opacity = 1f),
        )

    private val unknownVisual =
        StarVisual(
            imageKey = UNKNOWN_IMAGE_KEY,
            colorHex = DesignSystemColors.STAR_UNKNOWN_HEX,
            scale = 0.58f,
            opacity = 0.85f,
        )

    val allVisuals: List<StarVisual> = palette + unknownVisual

    /** 지도 재조회에도 같은 별이 같은 색을 유지하도록 ID 기반으로 팔레트를 선택합니다. */
    fun visualFor(starId: String): StarVisual = palette[stablePaletteIndex(starId)]

    /** 기존 단계 기반 호출과 fallback을 위한 호환 정책입니다. */
    fun visualFor(stage: StarAgeStage): StarVisual =
        when (stage) {
            StarAgeStage.Fresh -> palette[0]
            StarAgeStage.Warm -> palette[1]
            StarAgeStage.Deep -> palette[2]
            StarAgeStage.Unknown -> unknownVisual
        }

    private fun stablePaletteIndex(starId: String): Int {
        var hash = 0
        starId.forEach { character ->
            hash = hash * 31 + character.code
        }
        return ((hash.toLong() and Int.MAX_VALUE.toLong()) % palette.size).toInt()
    }
}
