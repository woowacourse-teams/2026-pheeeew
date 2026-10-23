package com.pheeeew.core.designsystem.component

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
class RestoringSheetFlingBehaviorTest {
    @Test
    fun confirmationStartsAtReleaseAndReturnNeverTravelsToHidden() =
        runTest {
            val state = AnchoredDraggableState(SheetValue.Expanded)
            state.updateAnchors(
                DraggableAnchors {
                    SheetValue.Expanded at 0f
                    SheetValue.Hidden at 600f
                },
            )
            val samples = mutableListOf<Float>()
            val requests = mutableListOf<Float>()
            val clock =
                object : MonotonicFrameClock {
                    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                        delay(16)
                        samples += state.requireOffset()
                        return onFrame(testScheduler.currentTime * 1_000_000)
                    }
                }
            val fling =
                RestoringSheetFlingBehavior(
                    state = state,
                    positionalThreshold = 56f,
                    velocityThreshold = 125f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    onDismissRequest = { requests += state.requireOffset() },
                )

            suspend fun performFling(velocity: Float) {
                // anchoredDraggable already owns the drag mutation while invoking FlingBehavior.
                state.anchoredDrag {
                    val scroll =
                        object : ScrollScope {
                            override fun scrollBy(pixels: Float): Float {
                                val previous = state.requireOffset()
                                val next = (previous + pixels).coerceIn(0f, 600f)
                                dragTo(next)
                                return next - previous
                            }
                        }
                    with(fling) { scroll.performFling(velocity) }
                }
            }
            withContext(clock) {
                // A short downward fling should request confirmation before any return animation.
                repeat(2) {
                    state.dispatchRawDelta(80f)
                    performFling(200f)
                    assertEquals(SheetValue.Expanded, state.settledValue)
                    assertEquals(0f, state.requireOffset())
                }
                assertEquals(listOf(80f, 80f), requests)
                assertTrue(samples.all { it <= 80f }, "The return must not first animate toward Hidden")

                // A deliberate upward release cancels dismissal and still restores the sheet.
                state.dispatchRawDelta(300f)
                performFling(-200f)
                assertEquals(2, requests.size)
                assertEquals(0f, state.requireOffset())

                // A user can drag to the bottom manually; even then return and confirmation repeat.
                state.snapTo(SheetValue.Hidden)
                performFling(0f)
                assertEquals(600f, requests.last())
                assertEquals(SheetValue.Expanded, state.settledValue)
            }
        }
}
