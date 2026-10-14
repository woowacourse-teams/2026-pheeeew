package com.pheeeew.feature.screens.ranking.stamp

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId

data class RankingMember(
    val groupId: String,
    val rank: Int,
    val name: String,
    val score: Int,
    val stamp: StampAppearanceUiModel = previewRankingStamp,
)

private val previewRankingStamp =
    StampAppearanceUiModel(
        label = "히유",
        shape = StampShapeId.CIRCLE,
        fillArgb = 0xFF9DEBD5,
        textArgb = 0xFF17191A,
    )

internal val sampleRankings =
    listOf(
        RankingMember("00000000-0000-0000-0000-000000000001", 1, "우테코 8기 별터", 111),
        RankingMember("00000000-0000-0000-0000-000000000002", 2, "우테코 8기 모스", 98),
        RankingMember("00000000-0000-0000-0000-000000000003", 3, "우테코 8기 허닛", 87),
        RankingMember("00000000-0000-0000-0000-000000000004", 4, "우테코 8기 어셔", 76),
        RankingMember("00000000-0000-0000-0000-000000000005", 5, "우테코 8기 스타크", 65),
        RankingMember("00000000-0000-0000-0000-000000000006", 6, "우테코 8기 지민", 54),
        RankingMember("00000000-0000-0000-0000-000000000007", 7, "우테코 8기 수빈", 43),
    )

internal val sampleWeeks =
    listOf(
        "2026.09.02 ~ 2026.09.09",
        "2026.09.09 ~ 2026.09.16",
        "2026.09.16 ~ 2026.09.23",
        "2026.09.23 ~ 2026.09.30",
        "2026.09.30 ~ 2026.10.07",
    )
