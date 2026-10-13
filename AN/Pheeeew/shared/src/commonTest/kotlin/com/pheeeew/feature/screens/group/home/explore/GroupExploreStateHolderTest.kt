package com.pheeeew.feature.screens.group.home.explore

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupExploreStateHolderTest {
    @Test
    fun `search is trimmed debounced and pagination failure can retry the same cursor`() =
        runTest {
            val first = item("first", "산책 친구")
            val second = item("second", "주말 산책")
            val third = item("third", "동네 산책")
            val source =
                QueuedExploreSource(
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(first), "page-1")),
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(second), "page-2")),
                    GroupExplorePageResult.Unavailable,
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(second, third), null)),
                )
            val holder = GroupExploreStateHolder(this, source, debounceMillis = 300)

            holder.ensureLoaded()
            runCurrent()
            assertEquals(listOf(first), assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items)

            holder.onQueryChanged("  산책  ")
            advanceTimeBy(299)
            runCurrent()
            assertEquals(1, source.calls.size)

            advanceTimeBy(1)
            runCurrent()
            assertEquals("산책", source.calls[1].first)
            assertEquals(listOf(second), assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items)

            holder.loadNextPage()
            runCurrent()
            val failedPage = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content)
            assertEquals(listOf(second), failedPage.items)
            assertTrue(failedPage.loadMoreFailed)

            holder.retry()
            runCurrent()
            val recoveredPage = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content)
            assertEquals(listOf(second, third), recoveredPage.items)
            assertEquals(listOf(null to null, "산책" to null, "산책" to "page-2", "산책" to "page-2"), source.calls)
        }

    @Test
    fun `empty normalized query loads the complete in memory catalog and partial names match`() =
        runTest {
            val source = InMemoryGroupExploreSource(pageSize = 20)

            val fullPage = assertIs<GroupExplorePageResult.Success>(source.loadPage("   ", null)).page
            val searchPage = assertIs<GroupExplorePageResult.Success>(source.loadPage("  산책 ", null)).page

            assertEquals(8, fullPage.items.size)
            val expectedGroupIds = listOf("demo-hike", "demo-weekend", "demo-neighborhood", "demo-night")
            val actualGroupIds = searchPage.items.map { it.group.id.value }
            assertEquals(expectedGroupIds, actualGroupIds)
            assertEquals("산책", normalizeGroupExploreKeyword("  산책  "))
            assertEquals(null, normalizeGroupExploreKeyword("  \n "))
        }

    @Test
    fun `expired cursor keeps rows and retries first page even after failed recovery`() =
        runTest {
            val original = item("old", "이전 목록")
            val replacement = item("new", "새 목록")
            val source =
                QueuedExploreSource(
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(original), "expired")),
                    GroupExplorePageResult.InvalidCursor,
                    GroupExplorePageResult.Unavailable,
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(replacement), null)),
                )
            val holder = GroupExploreStateHolder(this, source)
            holder.onQueryChanged("산책")
            advanceTimeBy(300)
            runCurrent()
            holder.loadNextPage()
            runCurrent()
            val expired = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content)
            assertEquals(listOf(original), expired.items)
            assertTrue(expired.requiresFirstPageReload)
            holder.loadNextPage()
            assertEquals(2, source.calls.size)
            holder.retry()
            runCurrent()
            val failed = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content)
            assertEquals(listOf(original), failed.items)
            assertTrue(failed.requiresFirstPageReload)
            assertTrue(failed.refreshFailed)
            holder.retry()
            runCurrent()
            val recovered = assertIs<GroupExploreContent.Ready>(holder.uiState.value.content)
            assertEquals(listOf(replacement), recovered.items)
            assertEquals(false, recovered.requiresFirstPageReload)
            holder.loadNextPage()
            assertEquals(
                listOf<Pair<String?, String?>>("산책" to null, "산책" to "expired", "산책" to null, "산책" to null),
                source.calls,
            )
        }

    @Test
    fun `slow previous search cannot overwrite current results`() =
        runTest {
            val old = item("old", "old")
            val latest = item("latest", "latest")
            val source =
                object : GroupExploreSource {
                    override suspend fun loadPage(
                        keyword: String?,
                        cursor: String?,
                    ): GroupExplorePageResult {
                        if (keyword == "old") {
                            kotlinx.coroutines.withContext(
                                kotlinx.coroutines.NonCancellable,
                            ) { kotlinx.coroutines.delay(1000) }
                        }
                        return GroupExplorePageResult.Success(
                            GroupExplorePage(
                                listOf(
                                    if (keyword ==
                                        "old"
                                    ) {
                                        old
                                    } else {
                                        latest
                                    },
                                ),
                                null,
                            ),
                        )
                    }
                }
            val holder = GroupExploreStateHolder(this, source)
            holder.onQueryChanged("old")
            advanceTimeBy(300)
            runCurrent()
            holder.onQueryChanged("latest")
            advanceTimeBy(300)
            runCurrent()
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf(latest), assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items)
        }

    @Test
    fun `duplicate page requests and groups are suppressed without reordering`() =
        runTest {
            val first = item("first", "first")
            val second = item("second", "second")
            val source =
                QueuedExploreSource(
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(first, first), "next")),
                    GroupExplorePageResult.Success(GroupExplorePage(listOf(first, second), null)),
                )
            val holder = GroupExploreStateHolder(this, source)
            holder.ensureLoaded()
            runCurrent()
            holder.loadNextPage()
            holder.loadNextPage()
            runCurrent()
            assertEquals(listOf(first, second), assertIs<GroupExploreContent.Ready>(holder.uiState.value.content).items)
            holder.loadNextPage()
            assertEquals(2, source.calls.size)
        }

    private fun item(
        id: String,
        name: String,
    ): GroupExploreItemUiModel =
        GroupExploreItemUiModel(
            group =
                GroupSummaryUiModel(
                    id = GroupId(id),
                    name = name,
                    memberCount = 4,
                    weeklyStampCount = null,
                    stamp =
                        StampAppearanceUiModel(
                            label = "산책",
                            shape = StampShapeId.CIRCLE,
                            fillArgb = 0xFF9DE8D0L,
                            textArgb = 0xFF15181BL,
                        ),
                ),
            joinType = GroupJoinType.PUBLIC,
            isJoined = false,
        )

    private class QueuedExploreSource(
        vararg results: GroupExplorePageResult,
    ) : GroupExploreSource {
        private val results = ArrayDeque(results.toList())
        val calls = mutableListOf<Pair<String?, String?>>()

        override suspend fun loadPage(
            keyword: String?,
            cursor: String?,
        ): GroupExplorePageResult {
            calls += keyword to cursor
            return results.removeFirst()
        }
    }
}
