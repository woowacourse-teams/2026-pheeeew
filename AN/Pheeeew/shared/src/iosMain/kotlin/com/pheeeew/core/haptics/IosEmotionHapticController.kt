@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.pheeeew.core.haptics

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.CoreHaptics.CHHapticEngine
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState.UIApplicationStateActive
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import kotlin.native.Platform
import kotlin.native.ref.WeakReference
import kotlin.time.TimeSource

/** One app-scoped owner; one engine/player while at least one foreground screen is using haptics. */
internal class IosEmotionHapticController {
    private val lock = NSLock()
    private val gate = EmotionHapticRequestGate()
    private val queue = dispatch_queue_create("com.pheeeew.emotion-haptics", null)
    private val clock = TimeSource.Monotonic.markNow()
    private var applicationActive = UIApplication.sharedApplication.applicationState == UIApplicationStateActive
    private var interrupted = false
    private var released = false
    private val observers = mutableListOf<NSObjectProtocol>()
    private val session: EmotionHapticSession

    init {
        val weak = WeakReference(this)
        session =
            EmotionHapticSession(
                supported = CHHapticEngine.capabilitiesForHardware().supportsHaptics,
                nowMillis = ::now,
                createBackend = {
                    runCatching {
                        IosCoreHapticBackend.create(
                            onStopped = { source, reason -> weak.get()?.stopped(source, reason) },
                            onReset = { source -> weak.get()?.reset(source) },
                        )
                    }.getOrElse {
                        println("[EmotionHaptic] operation=backend.create failed=true")
                        null
                    }
                },
            )
        val center = NSNotificationCenter.defaultCenter
        observers +=
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
                weak.get()?.updateApplication(false)
            }
        observers +=
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
                weak.get()?.updateApplication(true)
            }
        // SwiftUI uses a scene lifecycle; app-level activation alone does not cover scene restoration.
        observers +=
            center.addObserverForName(UISceneWillDeactivateNotification, null, NSOperationQueue.mainQueue) {
                weak.get()?.updateApplication(false)
            }
        observers +=
            center.addObserverForName(UISceneDidActivateNotification, null, NSOperationQueue.mainQueue) {
                weak.get()?.updateApplication(true)
            }
        observers +=
            center.addObserverForName(
                AVAudioSessionInterruptionNotification,
                AVAudioSession.sharedInstance(),
                NSOperationQueue.mainQueue,
            ) {
                val type = (it?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.intValue
                if (type != null) weak.get()?.updateInterruption(type == AVAudioSessionInterruptionTypeBegan.toInt())
            }
        locked { updateGate() }
    }

    fun activate(owner: Any) {
        locked { if (!released) gate.activate(owner) }
        diagnostic("owner.activate")
        dispatch_async(queue) { reconcile() }
    }

    fun deactivate(owner: Any) {
        locked { gate.deactivate(owner) }
        diagnostic("owner.deactivate")
        dispatch_async(queue) { reconcile() }
    }

    fun play(owner: Any) {
        val schedule = locked { !released && gate.reserve(owner, now()) }
        if (schedule) {
            dispatch_async(queue) {
                if (locked { !released && gate.consume(now()) }) session.play()
            }
        }
    }

    fun release() {
        val shouldRelease =
            locked {
                if (released) {
                    false
                } else {
                    released = true
                    gate.setEnabled(false)
                    true
                }
            }
        if (!shouldRelease) return
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        dispatch_async(queue) { session.deactivate() }
    }

    private fun updateApplication(active: Boolean) {
        locked {
            // An interruption begin is not guaranteed to have a matching end notification.
            // On foreground entry, let engine.start determine whether resources are available.
            if (active && !applicationActive) interrupted = false
            applicationActive = active
            updateGate()
        }
        diagnostic("application.$active")
        dispatch_async(queue) { reconcile() }
    }

    private fun updateInterruption(began: Boolean) {
        locked {
            interrupted = began
            updateGate()
        }
        diagnostic("interruption.$began")
        dispatch_async(queue) { reconcile() }
    }

    private fun stopped(
        source: IosCoreHapticBackend,
        reason: Long,
    ) {
        dispatch_async(queue) {
            if (!session.owns(source)) return@dispatch_async
            locked { gate.invalidate() }
            // Delayed stop callbacks must not overwrite newer foreground/interruption notifications.
            session.stopped(source)
            println("[EmotionHaptic] operation=engine.stopped reason=$reason")
        }
    }

    private fun reset(source: IosCoreHapticBackend) {
        dispatch_async(queue) {
            if (!session.owns(source)) return@dispatch_async
            locked { gate.invalidate() }
            if (locked { !released && gate.active }) session.reset(source) else session.deactivate()
        }
    }

    private fun reconcile() {
        if (locked { !released && gate.active }) session.activate() else session.deactivate()
    }

    private fun updateGate() {
        gate.setEnabled(!released && applicationActive && !interrupted)
    }

    private fun now(): Long = clock.elapsedNow().inWholeMilliseconds

    private fun diagnostic(event: String) {
        if (Platform.isDebugBinary && NSProcessInfo.processInfo.environment["EMOTION_HAPTIC_DIAGNOSTICS"] == "1") {
            println(
                locked {
                    "[EmotionHaptic] event=$event owners=${gate.ownerCount} applicationActive=$applicationActive interrupted=$interrupted"
                },
            )
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
