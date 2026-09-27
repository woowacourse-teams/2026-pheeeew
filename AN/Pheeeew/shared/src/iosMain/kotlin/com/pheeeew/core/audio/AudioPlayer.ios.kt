@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { IosAudioPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

internal class IosAudioPlayer : AudioPlayer {
    private val mutableState = MutableStateFlow(AudioPlaybackState())
    override val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitor: Job? = null
    private var player: AVPlayer? = null
    private var completion: NSObjectProtocol? = null
    private var ownsSession = false

    private val interruption =
        NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification,
            AVAudioSession.sharedInstance(),
            NSOperationQueue.mainQueue,
        ) { if (state.value.playing || state.value.loading) pause() }

    override fun play(source: String) {
        val resume = state.value.source == source && player != null
        if (!resume) stop()
        val address = if (source.startsWith("https://")) NSURL.URLWithString(source) else NSURL.fileURLWithPath(source)
        if (address == null) return
        val session = AVAudioSession.sharedInstance()
        if (!session.setCategory(AVAudioSessionCategoryPlayback, error = null) ||
            !session.setActive(true, error = null)
        ) {
            mutableState.value = AudioPlaybackState(error = "녹음을 재생하지 못했어요. 다시 눌러 주세요.")
            return
        }
        ownsSession = true
        val next = if (resume) player!! else AVPlayer(uRL = address)
        player = next
        mutableState.value = AudioPlaybackState(source, playing = true, loading = true)
        completion?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        completion =
            NSNotificationCenter.defaultCenter.addObserverForName(
                AVPlayerItemDidPlayToEndTimeNotification,
                next.currentItem,
                NSOperationQueue.mainQueue,
            ) { if (player === next) stop() }
        next.play()
        monitor?.cancel()
        monitor =
            scope.launch {
                var started = false
                var ticks = 0
                while (isActive && player === next) {
                    delay(250)
                    ticks++
                    if (next.rate > 0f) {
                        started = true
                        mutableState.value = state.value.copy(loading = false)
                    }
                    if (next.currentItem?.status == AVPlayerItemStatusFailed || (!started && ticks > 120)) {
                        stop()
                        mutableState.value = AudioPlaybackState(error = "녹음을 재생하지 못했어요. 다시 눌러 주세요.")
                        break
                    }
                }
            }
    }

    override fun pause() {
        monitor?.cancel()
        player?.pause()
        if (ownsSession) AVAudioSession.sharedInstance().setActive(false, error = null)
        ownsSession = false
        mutableState.value = state.value.copy(playing = false, loading = false)
    }

    override fun stop() {
        completion?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        completion = null
        monitor?.cancel()
        monitor = null
        player?.pause()
        player = null
        if (ownsSession) AVAudioSession.sharedInstance().setActive(false, error = null)
        ownsSession = false
        mutableState.value = AudioPlaybackState()
    }

    override fun release() {
        NSNotificationCenter.defaultCenter.removeObserver(interruption)
        stop()
        scope.cancel()
    }
}
