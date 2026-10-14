package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.group.GroupExploreRepository
import com.pheeeew.domain.repository.group.GroupExploreResult
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePageResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiGroupExploreSourceTest {
    @Test
    fun `eleven or more characters return empty success without repository requests`() =
        runTest {
            var calls = 0
            val source =
                ApiGroupExploreSource(
                    GroupExploreRepository { _, _ ->
                        calls += 1
                        GroupExploreResult.Unavailable
                    },
                )
            listOf("가".repeat(11), "  " + "가".repeat(12) + "  ").forEach { query ->
                val page = assertIs<GroupExplorePageResult.Success>(source.loadPage(query, "old-cursor")).page
                assertTrue(page.items.isEmpty())
                assertNull(page.nextCursor)
            }
            assertEquals(0, calls)
        }

    @Test
    fun `ten characters are searched after trimming and blank queries stay unfiltered`() =
        runTest {
            val calls = mutableListOf<Pair<String?, String?>>()
            val source =
                ApiGroupExploreSource(
                    GroupExploreRepository { keyword, cursor ->
                        calls += keyword to cursor
                        GroupExploreResult.Unavailable
                    },
                )
            val boundary = "가".repeat(10)
            assertEquals(GroupExplorePageResult.Unavailable, source.loadPage("  $boundary  ", "opaque"))
            source.loadPage("  \n ", null)
            source.loadPage(null, null)
            assertEquals(listOf<Pair<String?, String?>>(boundary to "opaque", null to null, null to null), calls)
        }
}
