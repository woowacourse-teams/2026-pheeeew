package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupId

fun interface GroupDetailEmotionRankingSource {
    suspend fun load(groupId: GroupId): GroupDetailEmotionRankingResult

    companion object {
        val Unavailable = GroupDetailEmotionRankingSource { GroupDetailEmotionRankingResult.Unavailable }
    }
}

sealed interface GroupDetailEmotionRankingResult {
    data class Ranked(
        val rank: Int,
        val score: Int,
    ) : GroupDetailEmotionRankingResult {
        init {
            require(rank > 0) { "순위는 양수여야 합니다." }
            require(score >= 0) { "감정 입력 횟수는 음수일 수 없습니다." }
        }
    }

    data object NoPresses : GroupDetailEmotionRankingResult

    /** The endpoint did not include this group, so its exact rank cannot be inferred from the response. */
    data object NotListed : GroupDetailEmotionRankingResult

    data object Unavailable : GroupDetailEmotionRankingResult
}
