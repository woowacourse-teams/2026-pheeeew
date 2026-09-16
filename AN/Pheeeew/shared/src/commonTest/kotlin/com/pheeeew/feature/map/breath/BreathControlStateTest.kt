package com.pheeeew.feature.map.breath

import com.pheeeew.core.audio.BreathInput
import com.pheeeew.core.audio.BreathInputError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private class FakeBreathInput : BreathInput {
        val strengthCallbacks = mutableListOf<(Float) -> Unit>()
        var startCount = 0
        var stopCount = 0

        override suspend fun requestPermission(): Boolean = true

        override fun start(
            onStrengthChanged: (Float) -> Unit,
            onError: (BreathInputError) -> Unit,
        ) {
            startCount += 1
            strengthCallbacks += onStrengthChanged
        }

        override fun stop() {
            stopCount += 1
        }
    }
}
