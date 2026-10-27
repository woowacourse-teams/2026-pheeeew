package com.pheeeew.feature.screens.ranking.press

internal enum class PressEmotion(
    val label: String,
    val phrase: String,
    val apiState: String?,
) {
    All("전체", "", null),
    Frustrated("답답", "답답한", "FRUSTRATED"),
    Annoyed("짜증", "짜증나는", "IRRITATED"),
    Tired("지침", "지친", "EXHAUSTED"),
    Discouraged("좌절", "좌절한", "DISCOURAGED"),
    Angry("분노", "분노한", "ANGRY"),
}

internal data class PressPersonRank(
    val rank: Long,
    val nickname: String,
    val count: Long,
    val publicId: String? = null,
    val isMe: Boolean = false,
)

internal val samplePressPersonRanks =
    listOf(
        PressPersonRank(1, "한숨많은곰", 524),
        PressPersonRank(2, "숨고르기", 258),
        PressPersonRank(2, "익명", 258),
    )

internal val samplePressWeeks = listOf("2026.10.12 ~ 2026.10.19")
