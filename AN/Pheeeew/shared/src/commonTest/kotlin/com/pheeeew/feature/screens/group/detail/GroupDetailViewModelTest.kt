package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {
    private val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)

    @Test
    fun `first resume after initial load does not refresh but later resume does`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val refresh = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel {
                        calls += 1
                        if (calls == 1) GroupDetailLoadResult.Loaded(detail) else refresh.await()
                    }

                runCurrent()
                viewModel.onResumed()
                runCurrent()
                assertEquals(1, calls)
                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isRefreshing)

                viewModel.onResumed()
                runCurrent()
                assertEquals(2, calls)
                assertFalse(viewModel.uiState.value.isRefreshing)

                refresh.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                assertFalse(viewModel.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `first resume during initial load keeps loading and manual refresh still works`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val initial = CompletableDeferred<GroupDetailLoadResult>()
                val refresh = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel {
                        calls += 1
                        if (calls == 1) initial.await() else refresh.await()
                    }

                runCurrent()
                viewModel.onResumed()
                runCurrent()
                assertEquals(1, calls)
                assertEquals(GroupDetailContent.Loading, viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isRefreshing)

                initial.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                viewModel.onRetry()
                runCurrent()
                assertEquals(2, calls)
                assertTrue(viewModel.uiState.value.isRefreshing)

                refresh.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                assertFalse(viewModel.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(source: GroupDetailSource): GroupDetailViewModel =
        GroupDetailViewModel(
            groupId = detail.group.id,
            dependencies =
                GroupDetailDependencies(
                    source = source,
                    pressGroupEmotionAction = { _, _ -> PressGroupEmotionResult.Unavailable },
                    leaveGroupAction = { LeaveGroupResult.Unavailable },
                    errorReporter = { throw it },
                    operationKeyAllocator = GroupOperationKeyAllocator("detail-test"),
                ),
        )
}
