package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create

@Composable
actual fun rememberAudioPlayback(): AudioPlayback {
    val player = remember { IosAudioPlayback() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosAudioPlayback : AudioPlayback {
    private val mutable = MutableStateFlow(AudioPlaybackState.Empty)
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var player: AVAudioPlayer? = null
    private var file: NSURL? = null
    private var released = false
    private val session = AVAudioSession.sharedInstance()
    private val interruptionObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { stop() }

    override suspend fun prepare(bytes: ByteArray) {
        check(!released && bytes.isNotEmpty())
        clear()
        val target = NSURL.fileURLWithPath(NSTemporaryDirectory() + "emotion-audio-" + NSUUID().UUIDString + ".m4a")
        var installed = false
        try {
            val waveform =
                withContext(Dispatchers.Default) {
                    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                    check(
                        NSFileManager.defaultManager.createFileAtPath(
                            checkNotNull(target.path),
                            contents = data,
                            attributes = null,
                        ),
                    )
                    decodeIosAudioWaveform(target)
                }
            check(!released)
            val playback = AVAudioPlayer(contentsOfURL = target, error = null)
            check(playback.prepareToPlay())
            player = playback
            file = target
            installed = true
            mutable.value = AudioPlaybackState((playback.duration * 1000).toLong(), 0, waveform, false, null)
        } finally {
            if (!installed) target.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        }
    }

    override fun togglePlayback() {
        if (state.value.isPlaying) {
            stop()
            return
        }
        val playback = player ?: return
        try {
            check(session.setCategory(AVAudioSessionCategoryPlayback, error = null))
            check(session.setActive(true, error = null))
            playback.currentTime = 0.0
            check(playback.play())
            mutable.value = state.value.copy(positionMillis = 0, isPlaying = true, error = null)
            ticker?.cancel()
            ticker =
                scope.launch {
                    while (isActive && playback.playing) {
                        mutable.value = state.value.copy(positionMillis = (playback.currentTime * 1000).toLong())
                        delay(50)
                    }
                    mutable.value = state.value.copy(positionMillis = state.value.durationMillis, isPlaying = false)
                    session.setActive(false, error = null)
                }
        } catch (_: Exception) {
            stop()
            mutable.value = state.value.copy(error = "녹음을 재생할 수 없어. 다시 시도해.")
        }
    }

    override fun stop() {
        ticker?.cancel()
        player?.stop()
        player?.currentTime = 0.0
        mutable.value = state.value.copy(positionMillis = 0, isPlaying = false)
        session.setActive(false, error = null)
    }

    private fun clear() {
        stop()
        player = null
        file?.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        file = null
        mutable.value = AudioPlaybackState.Empty
    }

    override fun release() {
        released = true
        clear()
        NSNotificationCenter.defaultCenter.removeObserver(interruptionObserver)
        scope.cancel()
    }
}
