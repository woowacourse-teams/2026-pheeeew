package com.pheeeew.feature.screens.group.home

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.join.GroupJoinErrorReporter
import com.pheeeew.feature.screens.group.join.GroupJoinResult
import com.pheeeew.feature.screens.group.join.GroupJoinSubmissionState
import com.pheeeew.feature.screens.group.join.GroupLookupResult
import com.pheeeew.feature.screens.group.join.GroupLookupState
import com.pheeeew.feature.screens.group.join.JoinGroupAction
import com.pheeeew.feature.screens.group.join.LookupGroupAction
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupHomeViewModelTest {
    @Test
    fun `fast refresh keeps content and loading feedback for at least 1000 milliseconds`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val initialGroup = group("initial")
                val source =
                    QueuedGroupListSource(
                        CompletableDeferred(GroupListResult.Success(listOf(initialGroup))),
                        CompletableDeferred(GroupListResult.Unavailable),
                    )
                val viewModel = createViewModel(source)
                advanceUntilIdle()

                viewModel.refresh()
                runCurrent()
                advanceTimeBy(999)
                runCurrent()
                assertEquals(GroupRefreshStatus.Refreshing, viewModel.uiState.value.refreshStatus)
                assertEquals(GroupHomeContent.Ready(listOf(initialGroup)), viewModel.uiState.value.content)
                viewModel.refresh()
                assertEquals(2, source.calls)

                advanceTimeBy(1)
                runCurrent()
                assertEquals(GroupRefreshStatus.Failed, viewModel.uiState.value.refreshStatus)
                assertEquals(GroupHomeContent.Ready(listOf(initialGroup)), viewModel.uiState.value.content)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `returning to home only reloads after membership changes`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val initialGroup = group("initial")
                val updatedGroup = group("updated")
                val source =
                    QueuedGroupListSource(
                        CompletableDeferred(GroupListResult.Success(listOf(initialGroup))),
                        CompletableDeferred(GroupListResult.Success(listOf(updatedGroup))),
                    )
                val viewModel = createViewModel(source)

                advanceUntilIdle()
                assertEquals(1, source.calls)
                repeat(2) {
                    viewModel.refreshIfDirty()
                    advanceUntilIdle()
                }
                assertEquals(1, source.calls)
                assertEquals(GroupHomeContent.Ready(listOf(initialGroup)), viewModel.uiState.value.content)

                viewModel.invalidateMembership()
                viewModel.refreshIfDirty()
                advanceUntilIdle()
                assertEquals(2, source.calls)
                assertEquals(GroupHomeContent.Ready(listOf(updatedGroup)), viewModel.uiState.value.content)

                viewModel.refreshIfDirty()
                advanceUntilIdle()
                assertEquals(2, source.calls)
            } finally {
                Dispatchers.resetMain()
            }
        }

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

                advanceUntilIdle()
                assertIs<GroupHomeContent.Failed>(viewModel.uiState.value.content)

                viewModel.onRetry()
                advanceUntilIdle()
                assertEquals(2, source.calls)
                assertEquals(GroupHomeContent.Loading, viewModel.uiState.value.content)
                assertEquals(GroupRefreshStatus.Idle, viewModel.uiState.value.refreshStatus)

                retryResponse.complete(GroupListResult.Success(listOf(group)))
                advanceUntilIdle()
                assertEquals(GroupHomeContent.Ready(listOf(group)), viewModel.uiState.value.content)
                assertEquals(GroupRefreshStatus.Idle, viewModel.uiState.value.refreshStatus)

                viewModel.refresh()
                advanceUntilIdle()
                assertEquals(3, source.calls)
                assertEquals(GroupRefreshStatus.Refreshing, viewModel.uiState.value.refreshStatus)

                refreshFailure.complete(GroupListResult.Unavailable)
                advanceUntilIdle()
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

                advanceUntilIdle()
                viewModel.refresh()
                advanceUntilIdle()
                assertEquals(1, source.calls)

                first.complete(GroupListResult.Success(listOf(group("first"))))
                advanceUntilIdle()
                viewModel.refresh()
                viewModel.refresh()
                advanceUntilIdle()
                assertEquals(2, source.calls)

                second.complete(GroupListResult.Success(listOf(group("second"))))
                advanceUntilIdle()
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

                advanceUntilIdle()
                viewModel.invalidateMembership()
                viewModel.refresh()
                advanceUntilIdle()
                assertEquals(2, source.calls)

                val currentGroup = group("current")
                currentResponse.complete(GroupListResult.Success(listOf(currentGroup)))
                advanceUntilIdle()

                oldResponse.complete(GroupListResult.Success(listOf(group("stale"))))
                advanceUntilIdle()

                assertEquals(GroupHomeContent.Ready(listOf(currentGroup)), viewModel.uiState.value.content)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `joining a different group than the preview reports the joined group and refreshes membership`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val joinedGroupId = GroupId("joined-group")
                val refreshedGroups = CompletableDeferred<GroupListResult>()
                val source =
                    QueuedGroupListSource(
                        CompletableDeferred(GroupListResult.Success(emptyList())),
                        refreshedGroups,
                    )
                val viewModel =
                    GroupHomeViewModel(
                        groupListSource = source,
                        groupJoinDependencies =
                            GroupJoinDependencies(
                                lookupGroupAction = { GroupLookupResult.Found(group("preview-group")) },
                                joinGroupAction = { _, _ -> GroupJoinResult.Joined(joinedGroupId) },
                                errorReporter = GroupJoinErrorReporter {},
                                operationKeyAllocator = GroupOperationKeyAllocator("join-mismatch-test"),
                            ),
                    )

                advanceUntilIdle()
                viewModel.openJoinSheet()
                viewModel.onJoinCodeChanged("ABC123")
                viewModel.onJoinSearchClick()
                advanceUntilIdle()
                assertIs<GroupLookupState.Found>(viewModel.joinUiState.value.lookup)

                viewModel.onJoinClick()
                advanceUntilIdle()

                val succeeded = assertIs<GroupJoinSubmissionState.Succeeded>(viewModel.joinUiState.value.submission)
                assertEquals(joinedGroupId, succeeded.groupId)
                assertTrue(viewModel.consumeJoinAndClose(succeeded.operationKey))

                viewModel.refreshIfDirty()
                advanceUntilIdle()
                assertEquals(2, source.calls)

                val joinedGroup = group(joinedGroupId.value)
                refreshedGroups.complete(GroupListResult.Success(listOf(joinedGroup)))
                advanceUntilIdle()

                assertEquals(GroupHomeContent.Ready(listOf(joinedGroup)), viewModel.uiState.value.content)
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
