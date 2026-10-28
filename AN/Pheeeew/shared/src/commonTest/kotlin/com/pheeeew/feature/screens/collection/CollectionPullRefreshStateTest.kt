package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionPullRefreshStateTest {
    @Test
    fun `release requests once and returns without waiting for loading`() =
        runTest {
            val clock = BroadcastFrameClock()
            var requests = 0
            var enabled = true
            val state =
                CollectionPullRefreshState(CoroutineScope(coroutineContext + clock), 56f, { enabled }) {
                    requests++
                    enabled = false
                }
            state.beginGesture()
            state.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.UserInput)
            assertEquals(80f, state.offset)
            assertEquals(0, requests)
            state.release()
            assertEquals(1, requests)
            runCurrent()
            clock.sendFrame(0)
            runCurrent()
            clock.sendFrame(300_000_000)
            runCurrent()
            assertEquals(0f, state.offset)
            state.beginGesture()
            assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput))
        }

    @Test
    fun `reverse below threshold and cancellation do not refresh`() =
        runTest {
            val clock = BroadcastFrameClock()
            var requests = 0
            val state =
                CollectionPullRefreshState(
                    scope = CoroutineScope(backgroundScope.coroutineContext + clock),
                    threshold = 56f,
                    isEnabled = { true },
                    onRefresh = { requests++ },
                )
            state.beginGesture()
            state.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.UserInput)
            state.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
            state.release()
            assertEquals(0, requests)
            state.beginGesture()
            state.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.UserInput)
            state.release(cancelled = true)
            assertEquals(0, requests)
            assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 80f), NestedScrollSource.SideEffect))
        }
}
