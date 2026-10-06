package com.pheeeew.feature.screens.group.detail

/** Keeps ranking UI state transitions independent from request scheduling and screen state. */
internal object GroupDetailEmotionRankingStateReducer {
    fun refreshStarted(previous: GroupDetailEmotionRankingUiState): GroupDetailEmotionRankingUiState =
        if (previous.content.isResolved()) {
            previous.copy(isRefreshing = true, hasRefreshError = false)
        } else {
            GroupDetailEmotionRankingUiState()
        }

    fun resultReceived(
        previous: GroupDetailEmotionRankingUiState,
        result: GroupDetailEmotionRankingResult,
    ): GroupDetailEmotionRankingUiState =
        when (result) {
            is GroupDetailEmotionRankingResult.Ranked -> {
                GroupDetailEmotionRankingUiState(
                    content = GroupDetailEmotionRankingContent.Ranked(rank = result.rank, score = result.score),
                )
            }

            GroupDetailEmotionRankingResult.NoPresses -> {
                GroupDetailEmotionRankingUiState(content = GroupDetailEmotionRankingContent.NoPresses)
            }

            GroupDetailEmotionRankingResult.NotListed -> {
                GroupDetailEmotionRankingUiState(content = GroupDetailEmotionRankingContent.NotListed)
            }

            GroupDetailEmotionRankingResult.Unavailable -> {
                refreshFailed(previous)
            }
        }

    fun refreshFailed(previous: GroupDetailEmotionRankingUiState): GroupDetailEmotionRankingUiState =
        if (previous.content.isResolved()) {
            previous.copy(isRefreshing = false, hasRefreshError = true)
        } else {
            GroupDetailEmotionRankingUiState(content = GroupDetailEmotionRankingContent.Unavailable)
        }

    private fun GroupDetailEmotionRankingContent.isResolved(): Boolean =
        this is GroupDetailEmotionRankingContent.Ranked ||
            this == GroupDetailEmotionRankingContent.NoPresses ||
            this == GroupDetailEmotionRankingContent.NotListed
}
