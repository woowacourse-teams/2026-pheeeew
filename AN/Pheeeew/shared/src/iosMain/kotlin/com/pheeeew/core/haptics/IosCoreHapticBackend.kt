@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package com.pheeeew.core.haptics

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioSession
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticTransient
import platform.CoreHaptics.CHHapticPattern
import platform.CoreHaptics.CHHapticPatternPlayerProtocol
import platform.CoreHaptics.CHHapticTimeImmediate
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import kotlin.native.Platform
import kotlin.native.ref.WeakReference

/** Native resources are confined to the controller's serial queue. */
internal class IosCoreHapticBackend private constructor(
    private val engine: CHHapticEngine,
    private val pattern: CHHapticPattern,
) : EmotionHapticBackend {
    private var player: CHHapticPatternPlayerProtocol? = null
    private var closed = false
    private var successfulPlays = 0L

    override fun start(): Boolean =
        memScoped {
            if (closed) return@memScoped false
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            if (!engine.startAndReturnError(error.ptr) || error.value != null) {
                report("engine.start", error.value)
                return@memScoped false
            }
            if (player == null) {
                error.value = null
                player = engine.createPlayerWithPattern(pattern, error.ptr)
                if (player == null || error.value != null) {
                    player = null
                    report("player.create", error.value)
                    return@memScoped false
                }
                diagnostic("player.created")
            }
            true
        }

    override fun play(): Boolean =
        memScoped {
            val current = player ?: return@memScoped false
            if (closed) return@memScoped false
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            val success = current.startAtTime(CHHapticTimeImmediate, error.ptr)
            if (!success || error.value != null) report("player.start", error.value)
            if (success && error.value == null) {
                successfulPlays++
                if (successfulPlays == 1L ||
                    successfulPlays % 100L == 0L
                ) {
                    diagnostic("player.started count=$successfulPlays")
                }
            }
            success && error.value == null
        }

    override fun invalidatePlayer() {
        player = null
    }

    override fun close() {
        if (closed) return
        closed = true
        engine.stoppedHandler = null
        engine.resetHandler = null
        player?.stopAtTime(CHHapticTimeImmediate, error = null)
        player = null
        engine.stopWithCompletionHandler(null)
        diagnostic("backend.closed")
        // Do not deactivate or change the category of the shared audio session.
    }

    companion object {
        fun create(
            onStopped: (IosCoreHapticBackend, Long) -> Unit,
            onReset: (IosCoreHapticBackend) -> Unit,
        ): IosCoreHapticBackend? =
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                error.value = null
                val audioSession = AVAudioSession.sharedInstance() as objcnames.classes.AVAudioSession
                val engine = CHHapticEngine(audioSession = audioSession, error = error.ptr)
                if (error.value != null) {
                    report("engine.create", error.value)
                    return@memScoped null
                }
                engine.playsHapticsOnly = true
                engine.autoShutdownEnabled = false
                val event =
                    CHHapticEvent(
                        eventType = CHHapticEventTypeHapticTransient,
                        parameters =
                            listOf(
                                CHHapticEventParameter(
                                    parameterID = CHHapticEventParameterIDHapticIntensity,
                                    value = 0.6f,
                                ),
                                CHHapticEventParameter(
                                    parameterID = CHHapticEventParameterIDHapticSharpness,
                                    value = 0.5f,
                                ),
                            ),
                        relativeTime = 0.0,
                    )
                error.value = null
                val pattern = CHHapticPattern(events = listOf(event), parameters = emptyList<Any>(), error = error.ptr)
                if (error.value != null) {
                    report("pattern.create", error.value)
                    engine.stopWithCompletionHandler(null)
                    return@memScoped null
                }
                val backend = IosCoreHapticBackend(engine, pattern)
                diagnostic("backend.created")
                val weak = WeakReference(backend)
                engine.stoppedHandler = { reason -> weak.get()?.let { onStopped(it, reason) } }
                engine.resetHandler = { weak.get()?.let(onReset) }
                backend
            }

        private fun report(
            operation: String,
            error: NSError?,
        ) {
            println("[EmotionHaptic] operation=$operation domain=${error?.domain} code=${error?.code}")
        }

        private fun diagnostic(event: String) {
            if (Platform.isDebugBinary && NSProcessInfo.processInfo.environment["EMOTION_HAPTIC_DIAGNOSTICS"] == "1") {
                println("[EmotionHaptic] event=$event")
            }
        }
    }
}
