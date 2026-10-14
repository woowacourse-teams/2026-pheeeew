package com.pheeeew.feature.screens.ranking

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.model.ranking.GroupRanking
import com.pheeeew.domain.model.ranking.GroupRankingItem
import com.pheeeew.domain.model.ranking.PressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import com.pheeeew.domain.repository.GroupRankingLoadResult
import com.pheeeew.domain.repository.GroupRankingRepository
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressRankingLoadResult
import com.pheeeew.feature.screens.ranking.press.data.ApiPressRankingSource
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingLoadResult
import com.pheeeew.feature.screens.ranking.stamp.data.ApiWeeklyRankingSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.pheeeew.domain.repository.PressRankingLoadResult as RepositoryPressRankingResult

class RankingSourceGroupIdTest {
    @Test
    fun `스탬프 랭킹의 그룹 ID를 상세 이동용 모델에 보존한다`() =
        runTest {
            val repository =
                object : GroupRankingRepository {
                    override suspend fun find(weeksAgo: Int) =
                        GroupRankingLoadResult.Loaded(
                            GroupRanking(
                                weeksAgo = weeksAgo,
                                startAt = "2026-10-05",
                                endAt = "2026-10-12",
                                hasPrevious = false,
                                items =
                                    listOf(
                                        GroupRankingItem(
                                            rank = 1,
                                            groupId = requireNotNull(GroupId.parse(GROUP_ID)),
                                            name = "그룹",
                                            stamp =
                                                GroupStamp(
                                                    text = "히유",
                                                    textColor = requireNotNull(StampColor.parseServerValue("#FFFFFF")),
                                                    backgroundColor =
                                                        requireNotNull(
                                                            StampColor.parseServerValue("#4A90D9"),
                                                        ),
                                                    frame = GroupStampFrame.CIRCLE,
                                                ),
                                            score = 42,
                                        ),
                                    ),
                            ),
                        )
                }

            val result = assertIs<WeeklyRankingLoadResult.Loaded>(ApiWeeklyRankingSource(repository).load(0))

            assertEquals(GROUP_ID, result.rankings.single().groupId)
        }

    @Test
    fun `프레스 랭킹의 그룹 ID를 상세 이동용 모델에 보존한다`() =
        runTest {
            val repository =
                object : PressRankingRepository {
                    override suspend fun find(
                        weeksAgo: Int,
                        state: String?,
                    ) = RepositoryPressRankingResult.Loaded(
                        PressRanking(
                            weeksAgo = weeksAgo,
                            startAt = "2026-10-05",
                            endAt = "2026-10-12",
                            hasPrevious = false,
                            items = listOf(PressRankingItem(1, GROUP_ID, "그룹", 42, true)),
                        ),
                    )
                }

            val result =
                assertIs<PressRankingLoadResult.Loaded>(ApiPressRankingSource(repository).load(PressEmotion.All, 0))

            assertEquals(GROUP_ID, result.groups.single().groupId)
        }

    private companion object {
        const val GROUP_ID = "0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
    }
}
