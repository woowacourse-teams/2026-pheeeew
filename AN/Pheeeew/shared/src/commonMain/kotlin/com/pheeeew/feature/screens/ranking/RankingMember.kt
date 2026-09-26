package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId

data class RankingMember(
    val rank: Int,
    val name: String,
    val score: Int,
    val stamp: StampAppearanceUiModel = previewRankingStamp,
)

private val previewRankingStamp =
    StampAppearanceUiModel(
        label = "하유",
        shape = StampShapeId.CIRCLE,
        fillArgb = 0xFF9DEBD5,
        textArgb = 0xFF17191A,
    )

internal val sampleRankings =
    listOf(
        RankingMember(1, "우테코 8기 민준", 111),
        RankingMember(2, "우테코 8기 하유", 98),
        RankingMember(3, "우테코 8기 서연", 87),
        RankingMember(4, "우테코 8기 유나", 76),
        RankingMember(5, "우테코 8기 지호", 65),
        RankingMember(6, "우테코 8기 지민", 54),
        RankingMember(7, "우테코 8기 수빈", 43),
    )

internal val sampleWeeks =
    listOf(
        "2026.09.02 ~ 2026.09.09",
        "2026.09.09 ~ 2026.09.16",
        "2026.09.16 ~ 2026.09.23",
        "2026.09.23 ~ 2026.09.30",
        "2026.09.30 ~ 2026.10.07",
    )
