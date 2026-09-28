package com.pheeeew.feature.screens.map.nearby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbySheetNestedScrollConnectionTest {
    private var expanded = true
    private var offset = 0f
    private var collapsed: Boolean? = null
    private val connection =
        NearbySheetNestedScrollConnection(
            middleOffset = 400f,
            collapseThreshold = 28f,
            isExpanded = { expanded },
            onDrag = { offset = it },
            onFinish = { collapsed = it },
        )

    @Test
    fun scrollingWithinListDoesNotMoveSheet() =
        runTest {
            assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 60f), NestedScrollSource.UserInput))
            assertEquals(Offset.Zero, pull(consumed = 60f, available = 0f))
            assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, 500f)))
            assertEquals(0f, offset)
            assertNull(collapsed)
        }

    @Test
    fun leftoverPullAtTopCollapsesExpandedSheet() =
        runTest {
            assertEquals(Offset(0f, 30f), pull(consumed = 80f, available = 30f))
            assertEquals(30f, offset)
            assertEquals(Velocity(0f, 500f), connection.onPreFling(Velocity(0f, 500f)))
            assertTrue(collapsed == true)
        }

    @Test
    fun shortPullReturnsToExpandedPosition() =
        runTest {
            pull(available = 20f)
            connection.onPreFling(Velocity.Zero)
            assertFalse(collapsed == true)
            assertEquals(false, collapsed)
        }

    @Test
    fun reversingPullReturnsRemainingScrollToList() =
        runTest {
            pull(available = 60f)
            assertEquals(Offset(0f, -60f), connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput))
            assertEquals(0f, offset)
            connection.onPreFling(Velocity.Zero)
            assertEquals(false, collapsed)
        }

    @Test
    fun longPullStopsAtMiddleAndResetsAfterRelease() =
        runTest {
            assertEquals(Offset(0f, 400f), pull(available = 1000f))
            assertEquals(400f, offset)
            connection.onPreFling(Velocity(0f, 5000f))
            assertEquals(true, collapsed)
            assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput))
            pull(available = 10f)
            assertEquals(10f, offset)
            connection.onPreFling(Velocity.Zero)
            assertEquals(false, collapsed)
        }

    @Test
    fun collapsedSheetAndFlingDoNotStartPull() =
        runTest {
            expanded = false
            assertEquals(Offset.Zero, pull(available = 80f))
            expanded = true
            assertEquals(
                Offset.Zero,
                connection.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.SideEffect),
            )
            assertEquals(Offset.Zero, pull(available = -80f))
            connection.onPreFling(Velocity.Zero)
            assertNull(collapsed)
        }

    private fun pull(
        consumed: Float = 0f,
        available: Float,
    ): Offset = connection.onPostScroll(Offset(0f, consumed), Offset(0f, available), NestedScrollSource.UserInput)
}
