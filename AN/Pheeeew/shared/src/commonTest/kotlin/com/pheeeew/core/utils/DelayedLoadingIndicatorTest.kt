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
class DelayedLoadingIndicatorTest {
    @Test
    fun `빠른 성공과 실패는 로딩을 표시하거나 결과를 지연하지 않는다`() =
        runTest {
            val visibility = mutableListOf<Boolean>()
            val success =
                async {
                    withDelayedLoadingIndicator(onVisibleChanged = { visibility += it }) {
                        delay(100)
                        "success"
                    }
                }
            val failure =
                async {
                    runCatching {
                        withDelayedLoadingIndicator(onVisibleChanged = { visibility += it }) {
                            delay(100)
                            error("failed")
                        }
                    }
                }
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals("success", success.await())
            assertTrue(failure.await().isFailure)
            assertFalse(visibility.contains(true))
            assertEquals(100, testScheduler.currentTime)
        }

    @Test
    fun `150ms 후 표시한 로딩은 성공과 실패 모두 300ms 동안 유지한다`() =
        runTest {
            for (shouldFail in listOf(false, true)) {
                var visible = false
                val result =
                    async {
                        runCatching {
                            withDelayedLoadingIndicator(onVisibleChanged = { visible = it }) {
                                delay(200)
                                if (shouldFail) error("failed")
                            }
                        }
                    }
                runCurrent()
                advanceTimeBy(149)
                runCurrent()
                assertFalse(visible)
                advanceTimeBy(1)
                runCurrent()
                assertTrue(visible)
                advanceTimeBy(299)
                runCurrent()
                assertTrue(visible)
                assertFalse(result.isCompleted)
                advanceTimeBy(1)
                runCurrent()
                assertTrue(result.isCompleted)
                assertFalse(visible)
                assertEquals(shouldFail, result.await().isFailure)
            }
        }

    @Test
    fun `최소 표시 시간을 넘긴 요청은 완료 즉시 로딩을 닫는다`() =
        runTest {
            var visible = false
            val result =
                async {
                    withDelayedLoadingIndicator(onVisibleChanged = { visible = it }) { delay(1_000) }
                }
            runCurrent()
            advanceTimeBy(999)
            runCurrent()
            assertTrue(visible)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(result.isCompleted)
            assertFalse(visible)
            assertEquals(1_000, testScheduler.currentTime)
        }

    @Test
    fun `취소하면 최소 표시 시간을 기다리지 않고 타이머를 정리한다`() =
        runTest {
            var visible = false
            val result =
                async {
                    withDelayedLoadingIndicator(onVisibleChanged = { visible = it }) { delay(1_000) }
                }
            runCurrent()
            advanceTimeBy(150)
            runCurrent()
            assertTrue(visible)
            result.cancel()
            runCurrent()
            assertTrue(result.isCompleted)
            assertFalse(visible)
            assertEquals(150, testScheduler.currentTime)
        }
}
