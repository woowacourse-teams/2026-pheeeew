package com.pheeeew.core.haptics

import io.github.compose.jindong.core.dsl.buildHapticPattern
import io.github.compose.jindong.core.executor.HapticExecutor
import io.github.compose.jindong.core.executor.HapticHandle
import io.github.compose.jindong.core.model.HapticIntensity
import io.github.compose.jindong.core.model.IosHapticParameters
import kotlin.time.Duration.Companion.milliseconds

/** All calls are made on the UI thread. No coroutine queue can replay an old tap after exit. */
internal class JindongEmotionHapticController {
    private val owners = mutableSetOf<Any>()
    private var executor: HapticExecutor? = null
    private var handle: HapticHandle? = null

    fun activate(
        owner: Any,
        createExecutor: () -> HapticExecutor,
    ) {
        owners.add(owner)
        if (executor == null) {
            executor =
                runCatching(createExecutor).getOrElse {
                    report("create")
                    null
                }
        }
    }

    fun play(owner: Any) {
        if (owner !in owners) return
        val current = executor ?: return
        runCatching {
            if (!current.isSupported) return
            handle?.cancel()
            handle = null
            handle = current.executeAsync(TAP_PATTERN)
        }.onFailure {
            // Feedback must never interrupt the accepted emotion input or its visual reaction.
            report("play")
        }
    }

    fun deactivate(owner: Any) {
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        val previousHandle = handle
        val previousExecutor = executor
        handle = null
        executor = null
        runCatching { previousHandle?.cancel() }.onFailure { report("cancel") }
        runCatching { previousExecutor?.release() }.onFailure { report("release") }
    }

    private fun report(operation: String) {
        println("[EmotionHaptic] backend=jindong operation=$operation failed=true")
    }

    companion object {
        val shared = JindongEmotionHapticController()

        // 1.1.0 supports continuous events only; this is a candidate, not a transient-equivalent pulse.
        val TAP_PATTERN =
            buildHapticPattern {
                haptic(
                    duration = 20.milliseconds,
                    intensity = HapticIntensity.Custom(0.6f),
                    iosParameters = IosHapticParameters(sharpness = 0.5f),
                )
            }
    }
}
