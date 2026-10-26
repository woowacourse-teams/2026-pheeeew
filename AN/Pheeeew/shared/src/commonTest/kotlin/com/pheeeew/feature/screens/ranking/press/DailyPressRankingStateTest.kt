package com.pheeeew.feature.screens.ranking.press

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.domain.model.ranking.DailyPressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DailyPressRankingStateTest {
    @Test
    fun `새 알림은 같은 날짜도 다시 조회하고 새로고침 실패는 기존 결과를 보존한다`() =
        runTest {
            var calls = 0
            var fail = false
            val model =
                DailyPressRankingState({ date ->
                    calls++
                    if (fail) failure() else ApiResult.Success(ranking(date, 7))
                }, this)
            assertTrue(model.select("2026-10-26", "first"))
            advanceUntilIdle()
            assertFalse(model.select("2026-10-26", "first"))
            assertEquals(1, calls)
            assertTrue(model.select("2026-10-26", "next-notification"))
            advanceUntilIdle()
            assertEquals(2, calls)
            fail = true
            model.refresh()
            advanceUntilIdle()
            assertEquals(PressRankingStatus.Ready, model.state.value.ranking.status)
            assertEquals(
                7,
                model.state.value.ranking.me
                    ?.rank,
            )
            assertTrue(model.state.value.ranking.hasRefreshError)
            assertFalse(model.state.value.ranking.isRefreshing)
        }

    @Test
    fun `첫 조회 실패는 다시 시도할 수 있고 빈 순위와 me 없음은 정상 상태다`() =
        runTest {
            var fail = true
            val model =
                DailyPressRankingState({ date ->
                    if (fail) failure() else ApiResult.Success(ranking(date, null))
                }, this)
            model.select("2026-10-25", "past")
            advanceUntilIdle()
            assertEquals(PressRankingStatus.Failed, model.state.value.ranking.status)
            fail = false
            model.retry()
            advanceUntilIdle()
            assertEquals("2026-10-25", model.state.value.date)
            assertEquals(PressRankingStatus.Ready, model.state.value.ranking.status)
            assertNull(model.state.value.ranking.me)
            assertTrue(
                model.state.value.ranking.people
                    .isEmpty(),
            )
        }

    @Test
    fun `늦게 도착한 이전 날짜 응답은 새 날짜를 덮지 않는다`() =
        runTest {
            val old = CompletableDeferred<ApiResult<DailyPressRanking>>()
            val model =
                DailyPressRankingState({ date ->
                    if (date == "2026-10-25") {
                        withContext(NonCancellable) { old.await() }
                    } else {
                        ApiResult.Success(ranking(date, 2))
                    }
                }, this)
            model.select("2026-10-25", "old")
            runCurrent()
            model.select("2026-10-26", "new")
            runCurrent()
            old.complete(ApiResult.Success(ranking("2026-10-25", 9)))
            advanceUntilIdle()
            assertEquals("2026-10-26", model.state.value.date)
            assertEquals(
                2,
                model.state.value.ranking.me
                    ?.rank,
            )
        }

    @Test
    fun `같은 날짜의 새 알림도 진행 중 요청을 교체하고 오래된 결과를 무시한다`() =
        runTest {
            val old = CompletableDeferred<ApiResult<DailyPressRanking>>()
            var calls = 0
            val model =
                DailyPressRankingState({ date ->
                    if (++calls == 1) {
                        withContext(NonCancellable) { old.await() }
                    } else {
                        ApiResult.Success(ranking(date, 3))
                    }
                }, this)
            model.select("2026-10-26", "first")
            runCurrent()
            model.select("2026-10-26", "second")
            runCurrent()
            old.complete(ApiResult.Success(ranking("2026-10-26", 1)))
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(
                3,
                model.state.value.ranking.me
                    ?.rank,
            )
        }

    private fun ranking(
        date: String,
        rank: Int?,
    ): DailyPressRanking =
        DailyPressRanking(
            date,
            "2026-10-25T15:00:00Z",
            "2026-10-26T15:00:00Z",
            emptyList(),
            rank?.let { PressRankingItem(it, "익명", 100) },
        )

    private fun failure() = ApiResult.Failure(NetworkFailure.SessionUnavailable(MutationCertainty.NOT_A_MUTATION))
}
