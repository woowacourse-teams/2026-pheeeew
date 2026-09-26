package com.pheeeew.feature.screens.group.home

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.join.GroupJoinErrorReporter
import com.pheeeew.feature.screens.group.join.GroupJoinResult
import com.pheeeew.feature.screens.group.join.GroupLookupResult
import com.pheeeew.feature.screens.group.join.JoinGroupAction
import com.pheeeew.feature.screens.group.join.LookupGroupAction
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class GroupHomeViewModelTest {
    @Test
    fun `initial failure is separate from refresh failure and keeps loaded groups`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val group = group("server-group")
                val retryResponse = CompletableDeferred<GroupListResult>()
                val refreshFailure = CompletableDeferred<GroupListResult>()
                val source =
                    QueuedGroupListSource(
                        CompletableDeferred(GroupListResult.Unavailable),
                        retryResponse,
                        refreshFailure,
                    )
                val viewModel = createViewModel(source)

                runCurrent()
                assertIs<GroupHomeContent.Failed>(viewModel.uiState.value.content)

                viewModel.onRetry()
                runCurrent()
                assertEquals(2, source.calls)
                assertEquals(GroupHomeContent.Loading, viewModel.uiState.value.content)
                assertEquals(GroupRefreshStatus.Idle, viewModel.uiState.value.refreshStatus)

                retryResponse.complete(GroupListResult.Success(listOf(group)))
                runCurrent()
                assertEquals(GroupHomeContent.Ready(listOf(group)), viewModel.uiState.value.content)
                assertEquals(GroupRefreshStatus.Idle, viewModel.uiState.value.refreshStatus)

                viewModel.refresh()
                runCurrent()
                assertEquals(3, source.calls)
                assertEquals(GroupRefreshStatus.Refreshing, viewModel.uiState.value.refreshStatus)

                refreshFailure.complete(GroupListResult.Unavailable)
                runCurrent()
                assertEquals(GroupHomeContent.Ready(listOf(group)), viewModel.uiState.value.content)
                assertEquals(GroupRefreshStatus.Failed, viewModel.uiState.value.refreshStatus)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh while a request is active does not create a duplicate call`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val first = CompletableDeferred<GroupListResult>()
                val second = CompletableDeferred<GroupListResult>()
                val source = QueuedGroupListSource(first, second)
                val viewModel = createViewModel(source)

                runCurrent()
                viewModel.refresh()
                runCurrent()
                assertEquals(1, source.calls)

                first.complete(GroupListResult.Success(listOf(group("first"))))
                runCurrent()
                viewModel.refresh()
                viewModel.refresh()
                runCurrent()
                assertEquals(2, source.calls)

                second.complete(GroupListResult.Success(listOf(group("second"))))
                runCurrent()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `membership invalidation prevents an older response replacing the refreshed list`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val oldResponse = CompletableDeferred<GroupListResult>()
                val currentResponse = CompletableDeferred<GroupListResult>()
                val source = QueuedGroupListSource(oldResponse, currentResponse)
                val viewModel = createViewModel(source)

                runCurrent()
                viewModel.invalidateMembership()
                viewModel.refresh()
                runCurrent()
                assertEquals(2, source.calls)

                val currentGroup = group("current")
                currentResponse.complete(GroupListResult.Success(listOf(currentGroup)))
                runCurrent()

                oldResponse.complete(GroupListResult.Success(listOf(group("stale"))))
                runCurrent()

                assertEquals(GroupHomeContent.Ready(listOf(currentGroup)), viewModel.uiState.value.content)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(source: GroupListSource) =
        GroupHomeViewModel(
            groupListSource = source,
            groupJoinDependencies =
                GroupJoinDependencies(
                    lookupGroupAction = LookupGroupAction { GroupLookupResult.Unavailable },
                    joinGroupAction = JoinGroupAction { _, _ -> GroupJoinResult.Unavailable },
                    errorReporter = GroupJoinErrorReporter {},
                    operationKeyAllocator = GroupOperationKeyAllocator("home-test"),
                ),
        )

    private fun group(id: String) =
        GroupSummaryUiModel(
            id = GroupId(id),
            name = id,
            memberCount = 1L,
            weeklyStampCount = null,
            stamp =
                StampAppearanceUiModel(
                    label = "그룹",
                    shape = StampShapeId.CIRCLE,
                    fillArgb = 0xFFFFFFFFL,
                    textArgb = 0xFF000000L,
                ),
        )

    private class QueuedGroupListSource(
        vararg responses: CompletableDeferred<GroupListResult>,
    ) : GroupListSource {
        private val responses = ArrayDeque(responses.toList())
        var calls = 0
            private set

        override suspend fun loadGroups(): GroupListResult {
            calls += 1
            return responses.removeFirst().await()
        }
    }
}
