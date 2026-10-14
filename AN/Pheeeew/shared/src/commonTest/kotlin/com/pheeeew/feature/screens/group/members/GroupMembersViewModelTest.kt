package com.pheeeew.feature.screens.group.members

import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembersViewModelTest {
    @Test
    fun `keeps duplicate nicknames as separate rows and preserves page order`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val source =
                    QueueGroupMembersSource(
                        firstPage = GroupMembersPage(listOf(member("같은 닉네임"), member("같은 닉네임")), "cursor-2"),
                        nextPage = GroupMembersPage(listOf(member(null)), null),
                    )
                val viewModel = GroupMembersViewModel(GroupId("group"), source)
                advanceUntilIdle()

                val firstPage = assertIs<GroupMembersContent.Ready>(viewModel.uiState.value.content)
                assertEquals(listOf("같은 닉네임", "같은 닉네임"), firstPage.items.map { it.displayNickname })

                viewModel.loadNextPage()
                viewModel.loadNextPage()
                advanceUntilIdle()

                val allMembers = assertIs<GroupMembersContent.Ready>(viewModel.uiState.value.content)
                assertEquals(listOf("같은 닉네임", "같은 닉네임", "익명"), allMembers.items.map { it.displayNickname })
                assertEquals(listOf(null, "cursor-2"), source.requestedCursors)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `permission loss clears previously loaded names`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val source =
                    QueueGroupMembersSource(
                        firstPage = GroupMembersPage(listOf(member("이름이 노출되면 안 됨")), "cursor-2"),
                        nextResult = GroupMembersPageResult.Forbidden,
                    )
                val viewModel = GroupMembersViewModel(GroupId("group"), source)
                advanceUntilIdle()
                assertIs<GroupMembersContent.Ready>(viewModel.uiState.value.content)

                viewModel.loadNextPage()
                advanceUntilIdle()

                assertEquals(GroupMembersContent.Forbidden, viewModel.uiState.value.content)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `retries the failed page with the same cursor`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val source =
                    QueueGroupMembersSource(
                        firstPage = GroupMembersPage(listOf(member("첫 페이지")), "cursor-2"),
                        nextResults =
                            mutableListOf(
                                GroupMembersPageResult.Unavailable,
                                GroupMembersPageResult.Success(GroupMembersPage(listOf(member("두 번째 페이지")), null)),
                            ),
                    )
                val viewModel = GroupMembersViewModel(GroupId("group"), source)
                advanceUntilIdle()

                viewModel.loadNextPage()
                advanceUntilIdle()
                assertTrue(assertIs<GroupMembersContent.Ready>(viewModel.uiState.value.content).loadMoreFailed)

                viewModel.retry()
                advanceUntilIdle()

                val ready = assertIs<GroupMembersContent.Ready>(viewModel.uiState.value.content)
                assertEquals(listOf("첫 페이지", "두 번째 페이지"), ready.items.map { it.displayNickname })
                assertEquals(listOf(null, "cursor-2", "cursor-2"), source.requestedCursors)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class QueueGroupMembersSource(
        private val firstPage: GroupMembersPage,
        private val nextPage: GroupMembersPage? = null,
        private val nextResult: GroupMembersPageResult? = null,
        private val nextResults: MutableList<GroupMembersPageResult> = mutableListOf(),
    ) : GroupMembersSource {
        val requestedCursors = mutableListOf<String?>()

        override suspend fun loadPage(
            groupId: GroupId,
            cursor: String?,
        ): GroupMembersPageResult {
            requestedCursors += cursor
            if (cursor == null) return GroupMembersPageResult.Success(firstPage)
            return nextResults.removeFirstOrNull()
                ?: nextResult
                ?: GroupMembersPageResult.Success(requireNotNull(nextPage))
        }
    }

    private companion object {
        fun member(nickname: String?) =
            GroupMemberUiModel(
                nickname = nickname,
                role = GroupRole.MEMBER,
                joinedAt = "2026-09-01T08:00:00Z",
            )
    }
}
