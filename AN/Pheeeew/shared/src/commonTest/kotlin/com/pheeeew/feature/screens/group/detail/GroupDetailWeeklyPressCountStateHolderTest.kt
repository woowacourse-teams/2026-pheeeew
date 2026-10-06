package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailWeeklyPressCountStateHolderTest {
    @Test
    fun `주간 합계를 불러오고 수락된 입력을 즉시 더한 뒤 다음 조회에서 서버값으로 교체한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requestCount = 0
                var state = GroupDetailWeeklyPressCountUiState()
                val holder =
                    GroupDetailWeeklyPressCountStateHolder(
                        groupId = GroupId(GROUP_ID),
                        source =
                            GroupDetailWeeklyPressCountSource {
                                requestCount++
                                GroupDetailWeeklyPressCountResult.Loaded(if (requestCount == 1) 4L else 5L)
                            },
                        scope = backgroundScope,
                        onStateChanged = { state = it },
                    )

                holder.load()
                runCurrent()
                assertEquals(4L, state.displayedTotal)

                holder.recordAcceptedPress()
                assertEquals(5L, state.displayedTotal)
                assertEquals(1, requestCount)

                holder.load(force = true)
                runCurrent()
                assertEquals(5L, state.displayedTotal)
                assertEquals(0L, state.locallyConfirmedPresses)
                assertEquals(2, requestCount)
                assertFalse(state.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `조회에 실패해도 마지막 합계를 유지하고 재시도 가능한 상태를 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requestCount = 0
                var state = GroupDetailWeeklyPressCountUiState()
                val holder =
                    GroupDetailWeeklyPressCountStateHolder(
                        groupId = GroupId(GROUP_ID),
                        source =
                            GroupDetailWeeklyPressCountSource {
                                requestCount++
                                if (requestCount == 1) {
                                    GroupDetailWeeklyPressCountResult.Loaded(4L)
                                } else {
                                    GroupDetailWeeklyPressCountResult.Unavailable
                                }
                            },
                        scope = backgroundScope,
                        onStateChanged = { state = it },
                    )

                holder.load()
                runCurrent()
                holder.load(force = true)
                runCurrent()

                assertEquals(4L, state.displayedTotal)
                assertTrue(state.hasRefreshError)
                assertFalse(state.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `주간 기준값이 없을 때 성공한 입력 뒤에 주간 합계를 다시 읽는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requestCount = 0
                var state = GroupDetailWeeklyPressCountUiState()
                val holder =
                    GroupDetailWeeklyPressCountStateHolder(
                        groupId = GroupId(GROUP_ID),
                        source =
                            GroupDetailWeeklyPressCountSource {
                                requestCount++
                                if (requestCount == 1) {
                                    GroupDetailWeeklyPressCountResult.Unavailable
                                } else {
                                    GroupDetailWeeklyPressCountResult.Loaded(1L)
                                }
                            },
                        scope = backgroundScope,
                        onStateChanged = { state = it },
                    )

                holder.load()
                runCurrent()
                assertTrue(state.hasRefreshError)

                holder.recordAcceptedPress()
                runCurrent()

                assertEquals(2, requestCount)
                assertEquals(1L, state.displayedTotal)
                assertEquals(0L, state.locallyConfirmedPresses)
                assertFalse(state.hasRefreshError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private companion object {
        const val GROUP_ID = "00000000-0000-0000-0000-000000000645"
    }
}
