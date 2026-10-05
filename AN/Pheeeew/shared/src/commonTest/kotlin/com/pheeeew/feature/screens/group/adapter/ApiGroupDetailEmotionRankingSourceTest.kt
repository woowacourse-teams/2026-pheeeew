package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.model.ranking.PressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import com.pheeeew.domain.repository.PressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.group.detail.GroupDetailEmotionRankingResult
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiGroupDetailEmotionRankingSourceTest {
    @Test
    fun `loads current all emotion ranking and preserves server rank and score`() =
        runTest {
            var requestedWeeksAgo: Int? = null
            var requestedState: String? = "not-initialized"
            val repository =
                object : PressRankingRepository {
                    override suspend fun find(
                        weeksAgo: Int,
                        state: String?,
                    ): PressRankingLoadResult {
                        requestedWeeksAgo = weeksAgo
                        requestedState = state
                        return PressRankingLoadResult.Loaded(
                            ranking(
                                PressRankingItem(
                                    rank = 3,
                                    groupId = GROUP_ID,
                                    name = "테스트 그룹",
                                    score = 28,
                                    mine = true,
                                ),
                                PressRankingItem(
                                    rank = 4,
                                    groupId = OTHER_GROUP_ID,
                                    name = "다른 그룹",
                                    score = 19,
                                    mine = false,
                                ),
                            ),
                        )
                    }
                }

            val result = ApiGroupDetailEmotionRankingSource(repository).load(GroupId(GROUP_ID))

            assertEquals(0, requestedWeeksAgo)
            assertEquals(null, requestedState)
            assertEquals(GroupDetailEmotionRankingResult.Ranked(rank = 3, score = 28), result)
        }

    @Test
    fun `does not infer a rank when the requested group is not in the response`() =
        runTest {
            val repository =
                object : PressRankingRepository {
                    override suspend fun find(
                        weeksAgo: Int,
                        state: String?,
                    ): PressRankingLoadResult =
                        PressRankingLoadResult.Loaded(
                            ranking(
                                PressRankingItem(
                                    rank = 1,
                                    groupId = OTHER_GROUP_ID,
                                    name = "다른 그룹",
                                    score = 19,
                                    mine = false,
                                ),
                            ),
                        )
                }

            val result = ApiGroupDetailEmotionRankingSource(repository).load(GroupId(GROUP_ID))

            assertEquals(GroupDetailEmotionRankingResult.NotListed, result)
        }

    @Test
    fun `reports no presses when the server includes the group with a zero score`() =
        runTest {
            val repository =
                object : PressRankingRepository {
                    override suspend fun find(
                        weeksAgo: Int,
                        state: String?,
                    ): PressRankingLoadResult =
                        PressRankingLoadResult.Loaded(
                            ranking(
                                PressRankingItem(
                                    rank = 1,
                                    groupId = GROUP_ID,
                                    name = "테스트 그룹",
                                    score = 0,
                                    mine = true,
                                ),
                            ),
                        )
                }

            val result = ApiGroupDetailEmotionRankingSource(repository).load(GroupId(GROUP_ID))

            assertEquals(GroupDetailEmotionRankingResult.NoPresses, result)
        }

    @Test
    fun `maps unavailable ranking response`() =
        runTest {
            val repository =
                object : PressRankingRepository {
                    override suspend fun find(
                        weeksAgo: Int,
                        state: String?,
                    ): PressRankingLoadResult = PressRankingLoadResult.Unavailable
                }

            val result = ApiGroupDetailEmotionRankingSource(repository).load(GroupId(GROUP_ID))

            assertEquals(GroupDetailEmotionRankingResult.Unavailable, result)
        }

    private fun ranking(vararg items: PressRankingItem) =
        PressRanking(
            weeksAgo = 0,
            startAt = "2026-10-05T00:00:00Z",
            endAt = "2026-10-12T00:00:00Z",
            hasPrevious = true,
            items = items.toList(),
        )

    private companion object {
        const val GROUP_ID = "00000000-0000-0000-0000-000000000581"
        const val OTHER_GROUP_ID = "00000000-0000-0000-0000-000000000582"
    }
}
