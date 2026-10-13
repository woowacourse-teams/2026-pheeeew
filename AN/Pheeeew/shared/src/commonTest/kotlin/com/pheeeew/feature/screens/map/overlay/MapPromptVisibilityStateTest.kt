package com.pheeeew.feature.screens.map.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapPromptVisibilityStateTest {
    @Test
    fun remainsHiddenWhileMovingAndForThreeSecondsAfterStopping() =
        runTest {
            val state = MapPromptVisibilityState(this)
            assertFalse(state.isHidden)
            state.onCameraMovingChanged(true)
            advanceTimeBy(10_000)
            assertTrue(state.isHidden)
            state.onCameraMovingChanged(false)
            advanceTimeBy(2_999)
            assertTrue(state.isHidden)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(state.isHidden)
        }

    @Test
    fun anotherMovementCancelsThePreviousRestoreAndStartsANewDelay() =
        runTest {
            val state = MapPromptVisibilityState(this)
            state.onCameraMovingChanged(true)
            state.onCameraMovingChanged(false)
            advanceTimeBy(2_000)
            state.onCameraMovingChanged(true)
            advanceTimeBy(2_000)
            assertTrue(state.isHidden)
            state.onCameraMovingChanged(false)
            advanceTimeBy(3_000)
            runCurrent()
            assertFalse(state.isHidden)
        }

    @Test
    fun bubbleResetShowsThePromptImmediatelyAndCancelsPendingRestore() =
        runTest {
            val state = MapPromptVisibilityState(this)
            state.onCameraMovingChanged(true)
            state.onCameraMovingChanged(false)
            advanceTimeBy(1_000)
            state.reset()
            assertFalse(state.isHidden)
            state.onCameraMovingChanged(true)
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(state.isHidden)
            state.reset()
        }

    @Test
    fun stoppingAfterBubbleResetDoesNotHideThePromptAgain() =
        runTest {
            val state = MapPromptVisibilityState(this)
            state.onCameraMovingChanged(true)
            state.reset()
            state.onCameraMovingChanged(false)
            advanceTimeBy(3_000)
            runCurrent()
            assertFalse(state.isHidden)
        }
}
