package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import pheeeew.shared.generated.resources.Res
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.time.TimeSource

@OptIn(ExperimentalNativeApi::class)
@Composable
internal actual fun rememberPressSoundPlayer(): PressSoundPlayer {
    val player =
        remember {
            IosPressSoundPlayer(
                probeEnabled =
                    Platform.isDebugBinary &&
                        NSProcessInfo.processInfo.environment["PRESS_SOUND_PROBE"] == "1",
                disabled =
                    Platform.isDebugBinary &&
                        NSProcessInfo.processInfo.environment["PRESS_SOUND_DISABLED"] == "1",
            )
        }
    LaunchedEffect(player) {
        runCatching { player.load(Res.readBytes("files/audio/press_click.mp3")) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPressSoundPlayer(
    private val disabled: Boolean,
    probeEnabled: Boolean,
) : PressSoundPlayer {
    override val feedbackProbe =
        if (probeEnabled) PressFeedbackProbe(if (disabled) "off" else "on", ::println) else null

    private val queue = dispatch_queue_create("com.pheeeew.press-sound", null)
    private val lock = NSLock()
    private val gate = PressSoundRequestGate()

    // AVAudioPlayer and session operations are confined to this serial queue.
    private val players = mutableListOf<AVAudioPlayer>()
    private var prepared = false
    private var next = 0

    private fun <T> guarded(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    fun load(bytes: ByteArray) {
        if (disabled || bytes.isEmpty()) return
        dispatch_async(queue) {
            if (!guarded { gate.canLoad() }) return@dispatch_async
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            repeat(8) { players.add(AVAudioPlayer(data = data, error = null)) }
            if (guarded { gate.activeToken() } != null) preparePlayers()
        }
    }

    override fun prepare() {
        if (disabled) return
        val token = guarded { gate.resume() }
        dispatch_async(queue) {
            if (guarded { gate.isCurrent(token) }) preparePlayers()
        }
    }

    private fun preparePlayers() {
        prepared = false
        if (players.isEmpty()) return
        val session = AVAudioSession.sharedInstance()
        if (!session.setCategory(AVAudioSessionCategoryAmbient, error = null)) return
        if (!session.setActive(true, error = null)) return
        prepared = players.map { it.prepareToPlay() }.all { it }
    }

    override fun play() {
        if (disabled) return
        val requestedAt = TimeSource.Monotonic.markNow()
        val token = guarded { gate.reserve() } ?: return
        dispatch_async(queue) {
            if (!guarded { gate.consume(token) } || !prepared) return@dispatch_async
            if (requestedAt.elapsedNow().inWholeMilliseconds > 80) return@dispatch_async
            val index =
                players.indices
                    .firstOrNull { !players[(next + it) % players.size].playing }
                    ?.let { (next + it) % players.size } ?: next
            // Reuse the oldest voice without stop(), which discards its prepared resources.
            // Seeking a playing voice preserves a new attack when all voices are busy.
            val player = players[index]
            player.currentTime = 0.0
            if (guarded { gate.isCurrent(token) }) player.play()
            next = (index + 1) % players.size
        }
    }

    override fun stop() {
        guarded { gate.stop() }
        dispatch_async(queue) {
            players.forEach { it.stop() }
            prepared = false
        }
        // Do not deactivate the app-wide session: the recorder may already own it.
    }

    fun release() {
        guarded { gate.release() }
        dispatch_async(queue) {
            players.forEach { it.stop() }
            players.clear()
            prepared = false
        }
    }
}
