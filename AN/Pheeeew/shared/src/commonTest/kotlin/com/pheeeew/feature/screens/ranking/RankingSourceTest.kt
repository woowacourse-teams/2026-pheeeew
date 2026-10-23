package com.pheeeew.feature.screens.ranking

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.model.ranking.GroupRanking
import com.pheeeew.domain.model.ranking.GroupRankingItem
import com.pheeeew.domain.repository.GroupRankingLoadResult
import com.pheeeew.domain.repository.GroupRankingRepository
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingLoadResult
import com.pheeeew.feature.screens.ranking.stamp.data.ApiWeeklyRankingSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RankingSourceTest {
    @Test
    fun `스탬프 랭킹의 순위 이름 점수와 스탬프를 표시 모델에 매핑한다`() =
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

            val ranking = result.rankings.single()
            assertEquals(1, ranking.rank)
            assertEquals("그룹", ranking.name)
            assertEquals(42, ranking.score)
            assertEquals("히유", ranking.stamp.label)
            assertEquals(0xFFFFFFFFL, ranking.stamp.textArgb)
            assertEquals(0xFF4A90D9L, ranking.stamp.fillArgb)
        }

    private companion object {
        const val GROUP_ID = "0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
    }
}
