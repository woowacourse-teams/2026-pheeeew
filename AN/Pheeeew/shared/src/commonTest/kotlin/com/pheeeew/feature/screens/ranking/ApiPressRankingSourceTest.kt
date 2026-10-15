package com.pheeeew.feature.screens.ranking

import com.pheeeew.domain.model.ranking.PressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressRankingLoadResult
import com.pheeeew.feature.screens.ranking.press.data.ApiPressRankingSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import com.pheeeew.domain.repository.PressRankingLoadResult as RepositoryResult

class ApiPressRankingSourceTest {
    @Test
    fun `내 순위는 목록에서 추정하지 않고 me로 표시한다`() =
        runTest {
            var me: PressRankingItem? = PressRankingItem(150, "익명", 42)
            val repository =
                object : PressRankingRepository {
                    override suspend fun find(
                        weeksAgo: Int,
                        state: String?,
                    ): RepositoryResult {
                        assertEquals(2, weeksAgo)
                        assertEquals("ANGRY", state)
                        return RepositoryResult.Loaded(
                            PressRanking(
                                weeksAgo,
                                "2026-10-05",
                                "2026-10-12",
                                true,
                                listOf(PressRankingItem(1, "익명", 100)),
                                me,
                            ),
                        )
                    }
                }
            val source = ApiPressRankingSource(repository)
            val loaded = assertIs<PressRankingLoadResult.Loaded>(source.load(PressEmotion.Angry, 2))
            assertEquals(150, loaded.me?.rank)
            assertEquals(42L, loaded.me?.count)
            assertEquals(1, loaded.people.single().rank)
            me = null
            assertNull(assertIs<PressRankingLoadResult.Loaded>(source.load(PressEmotion.Angry, 2)).me)
        }
}
