package com.pheeeew.feature.screens.group.members

import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembersRecoveryTest {
    @Test
    fun `expired cursor retries first page after failed recovery without repeating expired cursor`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val cursors = mutableListOf<String?>()
                val responses =
                    mutableListOf(
                        success("old", "expired"),
                        GroupMembersPageResult.InvalidCursor,
                        GroupMembersPageResult.Unavailable,
                        success("new", "fresh"),
                    )
                val vm =
                    GroupMembersViewModel(
                        GroupId("group"),
                        source { cursor ->
                            cursors += cursor
                            responses.removeAt(0)
                        },
                    )
                advanceUntilIdle()
                vm.loadNextPage()
                advanceUntilIdle()
                val expired = assertIs<GroupMembersContent.Ready>(vm.uiState.value.content)
                assertTrue(expired.needsCursorRecovery)
                assertNull(expired.nextCursor)
                vm.loadNextPage()
                vm.retry()
                vm.retry()
                advanceUntilIdle()
                assertEquals(
                    "old",
                    assertIs<GroupMembersContent.Ready>(vm.uiState.value.content).items.single().nickname,
                )
                vm.loadNextPage()
                vm.retry()
                advanceUntilIdle()
                val ready = assertIs<GroupMembersContent.Ready>(vm.uiState.value.content)
                assertEquals("new", ready.items.single().nickname)
                assertEquals("fresh", ready.nextCursor)
                assertEquals(listOf(null, "expired", null, null), cursors)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `recovery blocks load more and duplicate retry until new first page finishes`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val deferred = CompletableDeferred<GroupMembersPageResult>()
                var calls = 0
                val vm =
                    GroupMembersViewModel(
                        GroupId("group"),
                        source {
                            when (++calls) {
                                1 -> success("old", "expired")
                                2 -> GroupMembersPageResult.InvalidCursor
                                else -> deferred.await()
                            }
                        },
                    )
                runCurrent()
                vm.loadNextPage()
                runCurrent()
                vm.retry()
                runCurrent()
                assertTrue(assertIs<GroupMembersContent.Ready>(vm.uiState.value.content).isRecovering)
                vm.loadNextPage()
                vm.retry()
                runCurrent()
                assertEquals(3, calls)
                deferred.complete(success("new", null))
                runCurrent()
                assertEquals(
                    "new",
                    assertIs<GroupMembersContent.Ready>(vm.uiState.value.content).items.single().nickname,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `permission deletion and authentication failure clear names during paging and recovery`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for ((result, expected) in listOf(
                    GroupMembersPageResult.Forbidden to GroupMembersContent.Forbidden,
                    GroupMembersPageResult.NotFound to GroupMembersContent.NotFound,
                    GroupMembersPageResult.Unauthorized to GroupMembersContent.Unauthorized,
                )) {
                    for (recover in listOf(false, true)) {
                        var calls = 0
                        val vm =
                            GroupMembersViewModel(
                                GroupId("group"),
                                source {
                                    when (++calls) {
                                        1 -> success("sensitive name", "next")
                                        2 -> if (recover) GroupMembersPageResult.InvalidCursor else result
                                        else -> result
                                    }
                                },
                            )
                        advanceUntilIdle()
                        vm.loadNextPage()
                        advanceUntilIdle()
                        if (recover) {
                            vm.retry()
                            advanceUntilIdle()
                        }
                        assertEquals(expected, vm.uiState.value.content)
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `group sources receive only their own group and cursor`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val requests = mutableListOf<Pair<String, String?>>()
                val source =
                    object : GroupMembersSource {
                        override suspend fun loadPage(
                            groupId: GroupId,
                            cursor: String?,
                        ): GroupMembersPageResult {
                            requests += groupId.value to cursor
                            return success(groupId.value, if (cursor == null) groupId.value + ":next" else null)
                        }
                    }
                val a = GroupMembersViewModel(GroupId("a"), source)
                val b = GroupMembersViewModel(GroupId("b"), source)
                advanceUntilIdle()
                a.loadNextPage()
                b.loadNextPage()
                advanceUntilIdle()
                assertEquals(listOf("a" to null, "b" to null, "a" to "a:next", "b" to "b:next"), requests)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun source(load: suspend (String?) -> GroupMembersPageResult) =
        object : GroupMembersSource {
            override suspend fun loadPage(
                groupId: GroupId,
                cursor: String?,
            ) = load(cursor)
        }

    private fun success(
        name: String,
        cursor: String?,
    ) = GroupMembersPageResult.Success(
        GroupMembersPage(listOf(GroupMemberUiModel(name, GroupRole.MEMBER, "2026-10-11T09:00:00Z")), cursor),
    )
}
