package com.pheeeew.feature.component

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CountUpAnimationTest {
    @Test
    fun initialAndDecreasedCountsAreImmediateBeforeEffectsRun() =
        runTest {
            val counter = CounterComposition(backgroundScope.coroutineContext + BroadcastFrameClock())
            try {
                assertEquals(100L, counter.compose(100))
                runCurrent()
                assertEquals(50L, counter.compose(50))
                runCurrent()
                assertEquals(50L, counter.value)
            } finally {
                counter.close()
            }
        }

    @Test
    fun reducedMotionReturnsTargetBeforeCancellingAnInFlightIncrease() =
        runTest {
            val clock = BroadcastFrameClock()
            val counter = CounterComposition(backgroundScope.coroutineContext + clock)
            try {
                counter.compose(100)
                runCurrent()
                assertEquals(100L, counter.compose(200))
                runCurrent()
                clock.sendFrame(0L)
                runCurrent()
                clock.sendFrame(150_000_000L)
                runCurrent()
                assertTrue(counter.value in 101L..199L)
                assertEquals(200L, counter.compose(200, reducedMotion = true))
                runCurrent()
                assertEquals(200L, counter.compose(200, reducedMotion = false))
            } finally {
                counter.close()
            }
        }

    @Test
    fun increasesRemainAnimatedAcrossRecompositionAndRetargetFromDisplayedCount() =
        runTest {
            val clock = BroadcastFrameClock()
            val counter = CounterComposition(backgroundScope.coroutineContext + clock)
            try {
                counter.compose(100)
                runCurrent()
                counter.compose(200)
                runCurrent()
                clock.sendFrame(0L)
                runCurrent()
                clock.sendFrame(150_000_000L)
                runCurrent()
                val midway = counter.value
                assertTrue(midway in 101L..199L)
                assertEquals(midway, counter.compose(200))
                assertEquals(midway, counter.compose(300))
                runCurrent()
                clock.sendFrame(150_000_000L)
                runCurrent()
                clock.sendFrame(450_000_000L)
                runCurrent()
                assertEquals(300L, counter.value)
            } finally {
                counter.close()
            }
        }
}

private class CounterComposition(
    context: CoroutineContext,
) {
    private val recomposer = Recomposer(context)
    private val runner = CoroutineScope(context).launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition =
        Composition(
            object : AbstractApplier<Unit>(Unit) {
                override fun insertTopDown(
                    index: Int,
                    instance: Unit,
                ) = Unit

                override fun insertBottomUp(
                    index: Int,
                    instance: Unit,
                ) = Unit

                override fun remove(
                    index: Int,
                    count: Int,
                ) = Unit

                override fun move(
                    from: Int,
                    to: Int,
                    count: Int,
                ) = Unit

                override fun onClear() = Unit
            },
            recomposer,
        )
    private lateinit var state: State<Long>
    val value: Long get() = state.value

    fun compose(
        count: Long,
        reducedMotion: Boolean = false,
    ): Long {
        composition.setContent { state = rememberCountUp(count, reducedMotion) }
        return value
    }

    fun close() {
        composition.dispose()
        recomposer.cancel()
        runner.cancel()
    }
}
