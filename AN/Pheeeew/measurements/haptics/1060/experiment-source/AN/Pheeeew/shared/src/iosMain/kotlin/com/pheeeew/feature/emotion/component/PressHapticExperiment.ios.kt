package com.pheeeew.feature.emotion.component

import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioSession
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.UIKit.UIImpactFeedbackGenerator
import kotlin.time.TimeSource

/** Opt-in Debug experiment; sends no synthetic press inputs or server writes. */
internal class PressHapticExperiment private constructor(private val prepareAfterImpact: Boolean, private val stackCapture: Boolean) {
    suspend fun run(generator: UIImpactFeedbackGenerator) {
        var sent = 0
        var prepares = 0
        println("[HapticExperiment] event=armed prepare=$prepareAfterImpact stackCapture=$stackCapture count=2000 intervalMs=20 startDelayMs=5000")
        try {
            delay(5000)
            val start = TimeSource.Monotonic.markNow()
            val session = AVAudioSession.sharedInstance()
            println("[HapticExperiment] event=start prepare=$prepareAfterImpact category=${session.category} mode=${session.mode} mainThread=${NSThread.isMainThread}")
            repeat(2000) { index ->
                val deadline = if (stackCapture) start.elapsedNow().inWholeMilliseconds + (if (index == 0) 0L else 20L) else index * 20L
                val remaining = deadline - start.elapsedNow().inWholeMilliseconds
                if (remaining > 0) delay(remaining)
                val actual = start.elapsedNow().inWholeMilliseconds
                val lateness = (actual - deadline).coerceAtLeast(0)
                if (lateness >= 20 && !stackCapture) {
                    println("[HapticExperiment] event=invalid reason=missedDeadline index=${index + 1} latenessMs=$lateness; restart trial")
                    return
                }
                println("[HapticExperiment] event=impact index=${index + 1} prepare=$prepareAfterImpact elapsedMs=$actual latenessMs=$lateness")
                generator.impactOccurred()
                sent++
                if (prepareAfterImpact) {
                    generator.prepare()
                    prepares++
                }
            }
            println("[HapticExperiment] event=complete prepare=$prepareAfterImpact impacts=$sent prepares=$prepares physicalOutput=unknown")
        } finally {
            println("[HapticExperiment] event=finished prepare=$prepareAfterImpact impacts=$sent prepares=$prepares; fewerThan=2000 means incomplete")
        }
    }

    companion object {
        fun fromEnvironment(): PressHapticExperiment? {
            val environment = NSProcessInfo.processInfo.environment
            if (environment["PRESS_HAPTIC_EXPERIMENT"] != "1") return null
            return PressHapticExperiment(environment["PRESS_HAPTIC_PREPARE"] == "1", environment["PRESS_HAPTIC_STACK_CAPTURE"] == "1")
        }
    }
}
