package com.pheeeew.legacy.feature.map.breath

import com.pheeeew.legacy.core.audio.BreathInput
import com.pheeeew.legacy.core.audio.BreathInputError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO

@OptIn(ExperimentalCoroutinesApi::class)
class BreathControlStateTest {
    @Test
    fun `permission and location effects start one input session`() =
        runTest {
            val input = FakeBreathInput()
            val state =
                BreathControlState(
                    breathInput = input,
                    scope = this,
                    ensureLocationPermission = { true },
                )

            state.start()
            advanceUntilIdle()

            assertEquals(BreathSessionState.Listening(1L, 0f, 0f, ZERO), state.session.value)
            assertEquals(1, input.startCount)
            state.dispose()
        }

    @Test
    fun `old platform callback cannot mutate a later session`() =
        runTest {
            val input = FakeBreathInput()
            val state =
                BreathControlState(
                    breathInput = input,
                    scope = this,
                    ensureLocationPermission = { true },
                )

            state.start()
            advanceUntilIdle()
            state.lifecycleStopped()
            advanceUntilIdle()
            state.start()
            advanceUntilIdle()

            input.strengthCallbacks.first()(1f)
            advanceUntilIdle()

            assertEquals(BreathSessionState.Listening(2L, 0f, 0f, ZERO), state.session.value)
            state.dispose()
        }

    @Test
    fun `platform ready is observed before a queued strength sample`() =
        runTest {
            val input = FakeBreathInput()
            val observations = mutableListOf<String>()
            val state =
                BreathControlState(
                    breathInput = input,
                    scope = this,
                    ensureLocationPermission = { true },
                    onInputReady = { observations += "ready" },
                    onStrengthEvaluated = { _, _, active ->
                        if (active) observations += "sound"
                    },
                )

            state.start()
            advanceUntilIdle()
            input.strengthCallbacks.single()(0.8f)
            advanceUntilIdle()
            assertTrue(observations.isEmpty())

            input.readyCallbacks.single()()
            advanceUntilIdle()

            assertEquals(listOf("ready", "sound"), observations)
            state.dispose()
        }

    @Test
    fun `successful release reports stop reason before input stops`() =
        runTest {
            val input = FakeBreathInput()
            val stopped = mutableListOf<Triple<Float, String, Boolean>>()
            val state =
                BreathControlState(
                    breathInput = input,
                    scope = this,
                    ensureLocationPermission = { true },
                    reducer = BreathSessionReducer(BreathInteractionConfig(minimumReleaseProgress = 0f)),
                    onInputStopped = { growth, reason, interrupted ->
                        stopped += Triple(growth, reason, interrupted)
                    },
                )

            state.start()
            advanceUntilIdle()
            input.readyCallbacks.single()()
            input.strengthCallbacks.single()(0.8f)
            advanceUntilIdle()
            val growthBeforeRelease = state.session.value.growth
            state.release(upwardDistanceDp = 100f, upwardVelocityDpPerSecond = 0f)
            advanceUntilIdle()

            assertEquals(1, stopped.size)
            assertEquals(growthBeforeRelease, stopped.single().first)
            assertEquals("release", stopped.single().second)
            assertEquals(false, stopped.single().third)
            state.dispose()
        }

    @Test
    fun `input failure is reported before its stopped summary`() =
        runTest {
            val input = FakeBreathInput()
            val observations = mutableListOf<String>()
            val state =
                BreathControlState(
                    breathInput = input,
                    scope = this,
                    ensureLocationPermission = { true },
                    onInputFailed = { observations += "failed" },
                    onInputStopped = { _, reason, _ -> observations += "stopped:$reason" },
                )

            state.start()
            advanceUntilIdle()
            input.readyCallbacks.single()()
            advanceUntilIdle()
            input.errorCallbacks.single()(BreathInputError.StartFailed)
            advanceUntilIdle()

            assertEquals(listOf("failed", "stopped:input_failed"), observations)
            state.dispose()
        }

    private class FakeBreathInput : BreathInput {
        val readyCallbacks = mutableListOf<() -> Unit>()
        val strengthCallbacks = mutableListOf<(Float) -> Unit>()
        val errorCallbacks = mutableListOf<(BreathInputError) -> Unit>()
        var startCount = 0
        var stopCount = 0

        override suspend fun requestPermission(): Boolean = true

        override fun start(
            onReady: () -> Unit,
            onStrengthChanged: (Float) -> Unit,
            onError: (BreathInputError) -> Unit,
        ) {
            startCount += 1
            readyCallbacks += onReady
            strengthCallbacks += onStrengthChanged
            errorCallbacks += onError
        }

        override fun stop() {
            stopCount += 1
        }
    }
}
