package com.pheeeew.core.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MinimumLoadingTimeTest {
    @Test
    fun `fast success and failure both wait for minimum display time`() =
        runTest {
            val success = async { withMinimumLoadingTime { "loaded" } }
            val failure = async { runCatching { withMinimumLoadingTime { error("failed") } } }
            runCurrent()
            advanceTimeBy(999)
            runCurrent()
            assertFalse(success.isCompleted)
            assertFalse(failure.isCompleted)

            advanceTimeBy(1)
            runCurrent()
            assertEquals("loaded", success.await())
            assertTrue(failure.await().isFailure)
        }

    @Test
    fun `slow request has no additional delay`() =
        runTest {
            val result =
                async {
                    withMinimumLoadingTime {
                        delay(1_500)
                        "loaded"
                    }
                }
            runCurrent()
            advanceTimeBy(1_500)
            runCurrent()
            assertTrue(result.isCompleted)
            assertEquals("loaded", result.await())
            assertEquals(1_500, testScheduler.currentTime)
        }

    @Test
    fun `cancellation does not wait for minimum display time`() =
        runTest {
            val result = async { withMinimumLoadingTime { "loaded" } }
            runCurrent()
            advanceTimeBy(100)
            result.cancel()
            runCurrent()
            assertTrue(result.isCancelled)
            assertTrue(result.isCompleted)
            assertEquals(100, testScheduler.currentTime)
        }
}
