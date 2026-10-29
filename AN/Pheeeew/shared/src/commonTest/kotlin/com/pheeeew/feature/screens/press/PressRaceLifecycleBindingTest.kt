package com.pheeeew.feature.screens.press

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pheeeew.data.press.race.PressRaceDiagnostic
import com.pheeeew.data.press.race.PressRaceView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressRaceLifecycleBindingTest {
    @Test
    fun recordsVisibilityCauseSeparatelyFromSocketFailureWithoutClosingTwice() {
        val lifecycle = TestLifecycle(Lifecycle.State.RESUMED)
        val targets = mutableListOf<PressRaceView?>()
        val reasons = mutableListOf<PressRaceDiagnostic>()
        val binding = PressRaceLifecycleBinding(lifecycle, targets::add, reasons::add)
        binding.attach()
        binding.update(PressRaceView.PRESS, true)
        lifecycle.event(Lifecycle.Event.ON_PAUSE)
        lifecycle.event(Lifecycle.Event.ON_STOP)
        assertEquals(listOf(PressRaceDiagnostic.LifecyclePaused), reasons)
        lifecycle.event(Lifecycle.Event.ON_RESUME)
        binding.update(PressRaceView.PRESS, false)
        assertEquals(PressRaceDiagnostic.FeatureDisabled, reasons.last())
        binding.update(PressRaceView.PRESS, true)
        binding.update(null, true)
        assertEquals(PressRaceDiagnostic.ViewHidden, reasons.last())
        binding.update(PressRaceView.PRESS, true)
        binding.dispose()
        assertEquals(PressRaceDiagnostic.BindingDisposed, reasons.last())
        assertEquals(4, targets.count { it == null })
    }

    @Test
    fun screenSwitchDoesNotInsertDisconnectAndRecompositionDoesNotRetry() {
        val lifecycle = TestLifecycle(Lifecycle.State.RESUMED)
        val targets = mutableListOf<PressRaceView?>()
        val binding = PressRaceLifecycleBinding(lifecycle, targets::add)
        binding.attach()
        binding.update(PressRaceView.PRESS, true)
        repeat(5) { binding.update(PressRaceView.PRESS, true) }
        binding.update(PressRaceView.ALL, true)
        repeat(5) { binding.update(PressRaceView.ALL, true) }
        binding.update(PressRaceView.PRESS, true)
        assertEquals(listOf<PressRaceView?>(PressRaceView.PRESS, PressRaceView.ALL, PressRaceView.PRESS), targets)
        binding.dispose()
        assertEquals(null, targets.last())
    }

    @Test
    fun backgroundClosesOnceAndReturnUsesLatestRequestedView() {
        val lifecycle = TestLifecycle(Lifecycle.State.RESUMED)
        val targets = mutableListOf<PressRaceView?>()
        val binding = PressRaceLifecycleBinding(lifecycle, targets::add)
        binding.attach()
        binding.update(PressRaceView.PRESS, true)
        lifecycle.event(Lifecycle.Event.ON_PAUSE)
        lifecycle.event(Lifecycle.Event.ON_STOP)
        binding.update(PressRaceView.ALL, true)
        lifecycle.event(Lifecycle.Event.ON_START)
        assertEquals(listOf<PressRaceView?>(PressRaceView.PRESS, null), targets)
        lifecycle.event(Lifecycle.Event.ON_RESUME)
        assertEquals(listOf<PressRaceView?>(PressRaceView.PRESS, null, PressRaceView.ALL), targets)
        binding.dispose()
    }

    @Test
    fun readinessAndNonRaceDestinationGateConnection() {
        val lifecycle = TestLifecycle(Lifecycle.State.RESUMED)
        val targets = mutableListOf<PressRaceView?>()
        val binding = PressRaceLifecycleBinding(lifecycle, targets::add)
        binding.attach()
        binding.update(PressRaceView.PRESS, false)
        assertTrue(targets.isEmpty())
        binding.update(PressRaceView.PRESS, true)
        binding.update(PressRaceView.PRESS, false)
        binding.update(PressRaceView.ALL, false)
        binding.update(PressRaceView.ALL, true)
        binding.update(null, true)
        assertEquals(listOf<PressRaceView?>(PressRaceView.PRESS, null, PressRaceView.ALL, null), targets)
        binding.dispose()
    }

    @Test
    fun disposalDetachesObserverWithoutClosingTheSharedClient() {
        val lifecycle = TestLifecycle(Lifecycle.State.RESUMED)
        val targets = mutableListOf<PressRaceView?>()
        val binding = PressRaceLifecycleBinding(lifecycle, targets::add)
        binding.attach()
        binding.attach()
        binding.update(PressRaceView.PRESS, true)
        binding.dispose()
        binding.dispose()
        lifecycle.event(Lifecycle.Event.ON_RESUME)
        binding.update(PressRaceView.ALL, true)
        assertEquals(listOf<PressRaceView?>(PressRaceView.PRESS, null), targets)
        assertTrue(lifecycle.observers.isEmpty())
    }

    @Test
    fun initiallyStoppedScreenDoesNotConnectBeforeResume() {
        val lifecycle = TestLifecycle(Lifecycle.State.CREATED)
        val targets = mutableListOf<PressRaceView?>()
        val binding = PressRaceLifecycleBinding(lifecycle, targets::add)
        binding.update(PressRaceView.ALL, true)
        binding.attach()
        lifecycle.event(Lifecycle.Event.ON_START)
        assertTrue(targets.isEmpty())
        lifecycle.event(Lifecycle.Event.ON_RESUME)
        assertEquals(listOf<PressRaceView?>(PressRaceView.ALL), targets)
        lifecycle.event(Lifecycle.Event.ON_PAUSE)
        lifecycle.event(Lifecycle.Event.ON_STOP)
        lifecycle.event(Lifecycle.Event.ON_DESTROY)
        assertEquals(listOf<PressRaceView?>(PressRaceView.ALL, null), targets)
        binding.dispose()
    }
}

/** Emits the same state/event pairs the platform lifecycle owner supplies, without a UI/Looper. */
private class TestLifecycle(
    initial: Lifecycle.State,
) : Lifecycle() {
    val observers = mutableSetOf<LifecycleObserver>()
    override var currentState = initial
    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = this@TestLifecycle
        }

    override fun addObserver(observer: LifecycleObserver) {
        observers += observer
    }

    override fun removeObserver(observer: LifecycleObserver) {
        observers -= observer
    }

    fun event(event: Lifecycle.Event) {
        currentState = event.targetState
        observers.toList().forEach { (it as LifecycleEventObserver).onStateChanged(owner, event) }
    }
}
