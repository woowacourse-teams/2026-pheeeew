package com.pheeeew.feature.screens.group.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupDetailEmotionRankingStateReducerTest {
    @Test
    fun `refresh keeps the displayed rank and failure marks it stale`() {
        val ranked =
            GroupDetailEmotionRankingUiState(
                content = GroupDetailEmotionRankingContent.Ranked(rank = 3, score = 18),
            )

        val refreshing = GroupDetailEmotionRankingStateReducer.refreshStarted(ranked)
        val failed = GroupDetailEmotionRankingStateReducer.refreshFailed(ranked)

        assertEquals(ranked.content, refreshing.content)
        assertTrue(refreshing.isRefreshing)
        assertFalse(refreshing.hasRefreshError)
        assertEquals(ranked.content, failed.content)
        assertFalse(failed.isRefreshing)
        assertTrue(failed.hasRefreshError)
    }

    @Test
    fun `unavailable initial result remains unavailable without refresh error`() {
        val state =
            GroupDetailEmotionRankingStateReducer.resultReceived(
                previous = GroupDetailEmotionRankingUiState(),
                result = GroupDetailEmotionRankingResult.Unavailable,
            )

        assertEquals(GroupDetailEmotionRankingContent.Unavailable, state.content)
        assertFalse(state.isRefreshing)
        assertFalse(state.hasRefreshError)
    }
}
