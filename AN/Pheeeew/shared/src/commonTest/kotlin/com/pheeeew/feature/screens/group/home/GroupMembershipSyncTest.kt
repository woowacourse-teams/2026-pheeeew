package com.pheeeew.feature.screens.group.home

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.home.explore.GroupExploreContent
import com.pheeeew.feature.screens.group.home.explore.GroupExploreItemUiModel
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePage
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePageResult
import com.pheeeew.feature.screens.group.home.explore.GroupExploreSource
import com.pheeeew.feature.screens.group.home.explore.GroupExploreStateHolder
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.join.GroupJoinResult
import com.pheeeew.feature.screens.group.join.GroupLookupResult
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembershipSyncTest {
    @Test
    fun `plain flow first event updates both catalogs preserving active tab and query`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val events = MutableSharedFlow<Long>()
                var joined = false
                var myCalls = 0
                val calls = mutableListOf<Pair<String?, String?>>()
                val vm =
                    GroupHomeViewModel(
                        groupListSource =
                            listSource {
                                myCalls++
                                GroupListResult.Success(if (joined) listOf(group("a", 9)) else emptyList())
                            },
                        groupJoinDependencies = dependencies(),
                        membershipChanges = events,
                        groupExploreSource =
                            source { keyword, cursor ->
                                calls += keyword to cursor
                                success(listOf(item("a", joined, if (joined) 9 else 8)), null)
                            },
                    )
                advanceUntilIdle()
                vm.selectTab(GroupHomeTab.Explore)
                advanceUntilIdle()
                vm.onExploreQueryChanged("  모임  ")
                advanceUntilIdle()
                joined = true
                events.emit(1)
                advanceUntilIdle()
                assertEquals(2, myCalls)
                assertEquals(GroupHomeTab.Explore, vm.selectedTab.value)
                assertEquals("  모임  ", vm.exploreUiState.value.query)
                val row = assertIs<GroupExploreContent.Ready>(vm.exploreUiState.value.content).items.single()
                assertTrue(row.isJoined)
                assertEquals(9L, row.group.memberCount)
                assertEquals("모임" to null, calls.last())
                vm.refresh()
                vm.refresh()
                advanceUntilIdle()
                assertEquals(2, myCalls)
                assertEquals(4, calls.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `unopened explore remains lazy after membership revision`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val revisions = MutableStateFlow(0L)
                var exploreCalls = 0
                var myCalls = 0
                val vm =
                    GroupHomeViewModel(
                        groupListSource =
                            listSource {
                                myCalls++
                                GroupListResult.Success(emptyList())
                            },
                        groupJoinDependencies = dependencies(),
                        membershipChanges = revisions,
                        groupExploreSource =
                            source { _, _ ->
                                exploreCalls++
                                success(listOf(item("a")), null)
                            },
                    )
                advanceUntilIdle()
                revisions.value = 1
                advanceUntilIdle()
                assertEquals(2, myCalls)
                assertEquals(0, exploreCalls)
                vm.selectTab(GroupHomeTab.Explore)
                advanceUntilIdle()
                assertEquals(1, exploreCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh rebuilds loaded range with new cursors before replacing snapshot`() =
        runTest {
            val calls = mutableListOf<String?>()
            var updated = false
            val holder =
                GroupExploreStateHolder(
                    this,
                    source { _, cursor ->
                        calls += cursor
                        if (cursor == null) {
                            success(listOf(item("a", updated)), if (updated) "new" else "old")
                        } else {
                            success(listOf(item("b", updated)), null)
                        }
                    },
                )
            holder.ensureLoaded()
            runCurrent()
            holder.loadNextPage()
            runCurrent()
            updated = true
            holder.onMembershipChanged()
            runCurrent()
            val rows = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items
            assertEquals(listOf("a", "b"), rows.map { it.group.id.value })
            assertTrue(rows.all { it.isJoined })
            assertEquals(listOf(null, "old", null, "new"), calls)
        }

    @Test
    fun `snapshot failure retains rows blocks old pagination and retries fresh first page`() =
        runTest {
            val responses =
                ArrayDeque(
                    listOf(
                        success(listOf(item("a")), "old"),
                        success(listOf(item("b")), null),
                        success(listOf(item("a", true)), "new"),
                        GroupExplorePageResult.Unavailable,
                        success(listOf(item("a", true), item("b", true)), null),
                    ),
                )
            val calls = mutableListOf<String?>()
            val holder =
                GroupExploreStateHolder(
                    this,
                    source { _, cursor ->
                        calls += cursor
                        responses.removeFirst()
                    },
                )
            holder.ensureLoaded()
            runCurrent()
            holder.loadNextPage()
            runCurrent()
            holder.onMembershipChanged()
            runCurrent()
            val failed = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content)
            assertTrue(failed.refreshFailed && failed.requiresFirstPageReload)
            assertTrue(failed.items.none { it.isJoined })
            holder.loadNextPage()
            runCurrent()
            assertEquals(4, calls.size)
            holder.retry()
            runCurrent()
            assertEquals(listOf(null, "old", null, "new", null), calls)
            assertTrue(assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items.all { it.isJoined })
        }

    @Test
    fun `new revision supersedes delayed old response without overwriting joined state`() =
        runTest {
            val old = CompletableDeferred<GroupExplorePageResult>()
            var calls = 0
            val holder =
                GroupExploreStateHolder(
                    this,
                    source { _, _ ->
                        when (++calls) {
                            1 -> success(listOf(item("a")), null)
                            2 -> withContext(NonCancellable) { old.await() }
                            else -> success(listOf(item("a", true, 12)), null)
                        }
                    },
                )
            holder.ensureLoaded()
            runCurrent()
            holder.onMembershipChanged()
            runCurrent()
            holder.onMembershipChanged()
            runCurrent()
            old.complete(success(listOf(item("a", false, 1)), null))
            runCurrent()
            val row = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items.single()
            assertTrue(row.isJoined)
            assertEquals(12L, row.group.memberCount)
        }

    private fun source(load: suspend (String?, String?) -> GroupExplorePageResult) =
        object : GroupExploreSource {
            override suspend fun loadPage(
                keyword: String?,
                cursor: String?,
            ) = load(keyword, cursor)
        }

    private fun success(
        items: List<GroupExploreItemUiModel>,
        cursor: String?,
    ) = GroupExplorePageResult.Success(GroupExplorePage(items, cursor))

    private fun item(
        id: String,
        joined: Boolean = false,
        count: Long = 8,
    ) = GroupExploreItemUiModel(group(id, count), GroupJoinType.PUBLIC, joined)

    private fun group(
        id: String,
        count: Long,
    ) = GroupSummaryUiModel(
        GroupId(id),
        id,
        count,
        null,
        StampAppearanceUiModel("모임", StampShapeId.CIRCLE, 0xFFFFFFFF, 0xFF000000),
    )

    private fun listSource(load: suspend () -> GroupListResult) =
        object : GroupListSource {
            override suspend fun loadGroups() = load()
        }

    private fun dependencies() =
        GroupJoinDependencies(
            lookupGroupAction = { GroupLookupResult.Unavailable },
            joinGroupAction = { _, _ -> GroupJoinResult.Unavailable },
            errorReporter = {},
            operationKeyAllocator = GroupOperationKeyAllocator("sync-test"),
        )
}
