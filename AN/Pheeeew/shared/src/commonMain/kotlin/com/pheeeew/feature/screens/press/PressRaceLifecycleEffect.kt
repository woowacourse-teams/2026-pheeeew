package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.data.press.race.PressRaceDiagnostic
import com.pheeeew.data.press.race.PressRaceSession
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.data.press.race.PressRaceViewActivation
import com.pheeeew.data.press.race.newPressRaceClock

/** One effect at App's composition root, never one per animated navigation destination. */
@Composable
internal fun PressRaceLifecycleEffect(
    session: PressRaceSession,
    view: PressRaceView?,
    enabled: Boolean,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
) {
    val binding =
        remember(session, lifecycle) {
            PressRaceLifecycleBinding(
                lifecycle = lifecycle,
                onView = { target, activation -> session.setView(target, activation) },
                onVisibility = session::noteVisibility,
                activationFactory = session::viewActivation,
            )
        }
    DisposableEffect(binding) {
        binding.attach()
        onDispose { binding.dispose() }
    }
    SideEffect { binding.update(view, enabled) }
}

/** Main-thread lifecycle adapter. Deduplication prevents recomposition from implicitly retrying a failure. */
internal class PressRaceLifecycleBinding(
    private val lifecycle: Lifecycle,
    private val onView: (PressRaceView?, PressRaceViewActivation?) -> Unit,
    private val onVisibility: (PressRaceDiagnostic) -> Unit = {},
    private val monotonicClock: (() -> Long)? = null,
    private val activationFactory: (() -> PressRaceViewActivation?)? = null,
) {
    private var requested: PressRaceView? = null
    private var enabled = false
    private var delivered: PressRaceView? = null
    private var attached = false
    private var disposed = false
    private val defaultMonotonicClock by lazy { newPressRaceClock() }
    private val observer = LifecycleEventObserver { _, _ -> synchronize() }

    fun attach() {
        if (attached || disposed) return
        attached = true
        lifecycle.addObserver(observer)
        synchronize()
    }

    fun update(
        view: PressRaceView?,
        enabled: Boolean,
    ) {
        if (disposed) return
        requested = view
        this.enabled = enabled
        synchronize()
    }

    private fun synchronize() {
        if (!attached || disposed) return
        val target = requested.takeIf { enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        if (target != delivered) {
            val wasInactive = delivered == null
            if (target == null) {
                onVisibility(
                    when {
                        !enabled -> PressRaceDiagnostic.FeatureDisabled
                        requested == null -> PressRaceDiagnostic.ViewHidden
                        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> PressRaceDiagnostic.LifecyclePaused
                        else -> PressRaceDiagnostic.LifecycleStopped
                    },
                )
            }
            delivered = target
            val activation =
                if (target != null && wasInactive) {
                    captureActivation()
                } else {
                    null
                }
            onView(target, activation)
        }
    }

    private fun captureActivation(): PressRaceViewActivation? {
        val factory = activationFactory
        if (factory != null) return runCatching { factory() }.getOrElse { PressRaceViewActivation(null) }
        val clock = monotonicClock ?: runCatching { defaultMonotonicClock }.getOrNull()
        return PressRaceViewActivation(clock?.let { runCatching { it() }.getOrNull() })
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        if (attached) lifecycle.removeObserver(observer)
        if (delivered != null) {
            onVisibility(PressRaceDiagnostic.BindingDisposed)
            delivered = null
            onView(null, null)
        }
    }
}
