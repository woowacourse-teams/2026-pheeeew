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
    private var dismissed = false
    private val connection =
        NearbySheetNestedScrollConnection(
            middleOffset = 400f,
            hiddenOffset = 800f,
            collapseThreshold = 28f,
            dismissThreshold = 72f,
            isExpanded = { expanded },
            onDrag = { offset = it },
            onFinish = { collapsed = it },
            onDismiss = { dismissed = true },
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
            assertFalse(dismissed)
            assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput))
            pull(available = 10f)
            assertEquals(10f, offset)
            connection.onPreFling(Velocity.Zero)
            assertEquals(false, collapsed)
        }

    @Test
    fun leftoverPullAtTopDismissesHalfSheet() =
        runTest {
            expanded = false
            assertEquals(Offset(0f, 72f), pull(consumed = 80f, available = 72f))
            assertEquals(472f, offset)
            assertEquals(Velocity(0f, 500f), connection.onPreFling(Velocity(0f, 500f)))
            assertTrue(dismissed)
            assertNull(collapsed)
        }

    @Test
    fun shortPullReturnsToHalfPosition() =
        runTest {
            expanded = false
            pull(available = 71f)
            assertEquals(471f, offset)
            connection.onPreFling(Velocity(0f, 5000f))
            assertFalse(dismissed)
            assertEquals(false, collapsed)
        }

    @Test
    fun reversingHalfSheetPullReturnsRemainingScrollToList() =
        runTest {
            expanded = false
            pull(available = 100f)
            assertEquals(Offset(0f, -100f), connection.onPreScroll(Offset(0f, -150f), NestedScrollSource.UserInput))
            assertEquals(400f, offset)
            connection.onPreFling(Velocity.Zero)
            assertFalse(dismissed)
            assertEquals(false, collapsed)
        }

    @Test
    fun secondPullAfterCollapseDismissesHalfSheet() =
        runTest {
            pull(available = 1000f)
            connection.onPreFling(Velocity.Zero)
            assertEquals(true, collapsed)
            assertFalse(dismissed)
            expanded = false
            assertEquals(Offset(0f, 400f), pull(available = 1000f))
            assertEquals(800f, offset)
            connection.onPreFling(Velocity.Zero)
            assertTrue(dismissed)
        }

    @Test
    fun scrollingWithinHalfSheetDoesNotMoveSheet() =
        runTest {
            expanded = false
            assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 80f), NestedScrollSource.UserInput))
            assertEquals(Offset.Zero, pull(consumed = 80f, available = 0f))
            assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, 500f)))
            assertFalse(dismissed)
            assertNull(collapsed)
        }

    @Test
    fun flingAndUpwardScrollDoNotStartPull() =
        runTest {
            assertEquals(
                Offset.Zero,
                connection.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.SideEffect),
            )
            assertEquals(Offset.Zero, pull(available = -80f))
            connection.onPreFling(Velocity.Zero)
            assertNull(collapsed)
            assertFalse(dismissed)
        }

    private fun pull(
        consumed: Float = 0f,
        available: Float,
    ): Offset = connection.onPostScroll(Offset(0f, consumed), Offset(0f, available), NestedScrollSource.UserInput)
}
