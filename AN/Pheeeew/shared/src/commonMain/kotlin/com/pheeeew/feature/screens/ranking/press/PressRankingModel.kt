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

internal data class PressGroupRank(
    val groupId: String,
    val rank: Int,
    val groupName: String,
    val subtitle: String,
    val count: Int,
    val isMyGroup: Boolean = false,
)

private val sampleMyGroupNames = setOf("히유 클럽", "별터 모임")

private val sampleEmotionGroupRanks =
    mapOf(
        PressEmotion.Frustrated to
            sampleRanks(
                "월요일 생존단" to 524,
                "히유 클럽" to 258,
                "퇴근이 필요해" to 237,
                "숨고르기" to 221,
                "별터 모임" to 198,
                "야근 탈출단" to 187,
                "천천히 괜찮아" to 163,
                "오늘도 버텨" to 142,
                "고요한 오후" to 119,
                "내일은 덜 바쁘길" to 94,
            ),
        PressEmotion.Annoyed to
            sampleRanks(
                "야근 탈출단" to 442,
                "히유 클럽" to 391,
                "오늘도 버텨" to 354,
                "퇴근이 필요해" to 312,
                "별터 모임" to 287,
                "월요일 생존단" to 244,
                "숨고르기" to 213,
                "내일은 덜 바쁘길" to 176,
                "천천히 괜찮아" to 151,
                "고요한 오후" to 108,
                myGroupNames = emptySet(),
            ),
        PressEmotion.Tired to
            sampleRanks(
                "고요한 오후" to 618,
                "별터 모임" to 486,
                "천천히 괜찮아" to 431,
                "히유 클럽" to 389,
                "월요일 생존단" to 352,
                "내일은 덜 바쁘길" to 318,
                "퇴근이 필요해" to 274,
                "숨고르기" to 231,
                "오늘도 버텨" to 190,
                "야근 탈출단" to 163,
            ),
        PressEmotion.Discouraged to
            sampleRanks(
                "오늘도 버텨" to 309,
                "숨고르기" to 271,
                "히유 클럽" to 264,
                "월요일 생존단" to 251,
                "별터 모임" to 209,
                "천천히 괜찮아" to 183,
                "퇴근이 필요해" to 157,
                "고요한 오후" to 132,
                "내일은 덜 바쁘길" to 106,
                "야근 탈출단" to 82,
            ),
        PressEmotion.Angry to
            sampleRanks(
                "퇴근이 필요해" to 720,
                "야근 탈출단" to 660,
                "숨고르기" to 537,
                "월요일 생존단" to 512,
                "히유 클럽" to 498,
                "오늘도 버텨" to 433,
                "숨고르기" to 387,
                "천천히 괜찮아" to 342,
                "고요한 오후" to 286,
                "내일은 덜 바쁘길" to 241,
            ),
    )

internal val samplePressGroupRanksByEmotion =
    sampleEmotionGroupRanks + (PressEmotion.All to aggregateSampleRanks())

internal val samplePressGroupRanks = samplePressGroupRanksByEmotion.getValue(PressEmotion.Frustrated)

private fun aggregateSampleRanks(): List<PressGroupRank> =
    sampleEmotionGroupRanks.values
        .flatMap { ranks -> ranks.distinctBy(PressGroupRank::groupName) }
        .groupBy(PressGroupRank::groupName)
        .map { (groupName, ranks) ->
            val isMyGroup = ranks.any(PressGroupRank::isMyGroup)
            PressGroupRank(
                groupId = ranks.first().groupId,
                rank = 0,
                groupName = groupName,
                subtitle = if (isMyGroup) "내 그룹" else "함께 누른 마음",
                count = ranks.sumOf(PressGroupRank::count),
                isMyGroup = isMyGroup,
            )
        }.sortedWith(compareByDescending<PressGroupRank> { it.count }.thenBy { it.groupName })
        .mapIndexed { index, rank -> rank.copy(rank = index + 1) }

private fun sampleRanks(
    vararg groups: Pair<String, Int>,
    myGroupNames: Set<String> = sampleMyGroupNames,
): List<PressGroupRank> =
    groups.mapIndexed { index, (groupName, count) ->
        val isMyGroup = groupName in myGroupNames
        PressGroupRank(
            groupId = "00000000-0000-0000-0000-${(index + 1).toString().padStart(12, '0')}",
            rank = index + 1,
            groupName = groupName,
            subtitle = if (isMyGroup) "내 그룹" else "함께 누른 마음",
            count = count,
            isMyGroup = isMyGroup,
        )
    }

internal val samplePressWeeks =
    listOf(
        "2026.09.02 ~ 2026.09.09",
        "2026.09.09 ~ 2026.09.16",
        "2026.09.16 ~ 2026.09.23",
        "2026.09.23 ~ 2026.09.30",
    )
