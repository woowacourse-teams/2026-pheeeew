@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.pheeeew.core.audio.MAX_RECORDING_DURATION_SECONDS
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
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { IosVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    return recorder
}

private class IosVoiceRecorder : VoiceRecorder {
    private val mutable = MutableStateFlow(VoiceRecordingState())
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var meter: Job? = null
    private var recorder: AVAudioRecorder? = null
    private var player: AVAudioPlayer? = null
    private var url: NSURL? = null
    private var generation = 0
    private var released = false
    private val session = AVAudioSession.sharedInstance()
    private val interruptionObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) {
            if (state.value.recording || state.value.playing || state.value.requestingPermission) {
                stop()
                pause()
            }
        }

    override fun refreshPermissionStatus() {
        mutable.value =
            state.value.copy(
                microphonePermissionGranted = session.recordPermission == AVAudioSessionRecordPermissionGranted,
            )
    }

    override fun start() {
        if (released || state.value.recording || state.value.requestingPermission) return
        val token = ++generation
        refreshPermissionStatus()
        mutable.value = state.value.copy(requestingPermission = true, microphonePermissionDenied = false, error = null)
        session.requestRecordPermission { granted ->
            scope.launch {
                if (released || generation != token) return@launch
                mutable.value =
                    state.value.copy(
                        requestingPermission = false,
                        microphonePermissionGranted = granted,
                        microphonePermissionDenied = !granted,
                    )
                if (granted) begin()
            }
        }
    }

    private fun begin() {
        clear()
        try {
            check(
                session.setCategory(
                    AVAudioSessionCategoryPlayAndRecord,
                    withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
                    error = null,
                ),
            )
            check(session.setActive(true, error = null))
            val target = NSURL.fileURLWithPath(NSTemporaryDirectory() + "voice-" + NSUUID().UUIDString + ".m4a")
            url = target
            val capture =
                AVAudioRecorder(
                    uRL = target,
                    settings =
                        mapOf<Any?, Any?>(
                            AVFormatIDKey to 1633772320,
                            AVSampleRateKey to 44100.0,
                            AVNumberOfChannelsKey to 1,
                            AVEncoderBitRateKey to 128000,
                        ),
                    error = null,
                )
            recorder = capture
            capture.meteringEnabled = true
            check(capture.prepareToRecord())
            check(capture.record())
            mutable.value = VoiceRecordingState(recording = true)
            meter =
                scope.launch {
                    while (isActive) {
                        delay(80)
                        if (!capture.recording) {
                            stop()
                            return@launch
                        }
                        capture.updateMeters()
                        val db = capture.averagePowerForChannel(0u).toDouble()
                        val level = ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
                        val durationMillis = (capture.currentTime * 1000).toLong()
                        mutable.value =
                            state.value.copy(
                                elapsedSeconds = (durationMillis / 1000).toInt(),
                                durationMillis = durationMillis,
                                samples = (state.value.samples + level).takeLast(7500),
                            )
                        if (capture.currentTime >= MAX_RECORDING_DURATION_SECONDS.toDouble()) {
                            mutable.value =
                                state.value.copy(
                                    elapsedSeconds = MAX_RECORDING_DURATION_SECONDS,
                                    durationMillis = MAX_RECORDING_DURATION_SECONDS * 1000L,
                                )
                            stop()
                            return@launch
                        }
                    }
                }
        } catch (_: Exception) {
            clear()
            mutable.value = state.value.copy(error = "녹음할 수 없어요. 마이크를 확인하고 다시 시도해주세요")
        }
    }

    override fun stop() {
        generation++
        mutable.value = state.value.copy(requestingPermission = false)
        val capture = recorder ?: return
        meter?.cancel()
        val duration = capture.currentTime
        mutable.value =
            state.value.copy(
                elapsedSeconds = duration.toInt(),
                durationMillis = (duration * 1000).toLong(),
            )
        capture.stop()
        recorder = null
        session.setActive(false, error = null)
        if (duration > 0 && url?.path?.let { NSFileManager.defaultManager.fileExistsAtPath(it) } == true) {
            mutable.value = state.value.copy(recording = false, filePath = url?.path)
        } else {
            clear()
            mutable.value = state.value.copy(error = "녹음이 너무 짧아요. 다시 녹음해주세요")
        }
    }

    override fun togglePlayback() {
        if (state.value.playing) {
            pause()
            return
        }
        val target = url ?: return
        if (state.value.filePath == null) return
        try {
            check(session.setCategory(AVAudioSessionCategoryPlayback, error = null))
            check(session.setActive(true, error = null))
            val playback = player ?: AVAudioPlayer(contentsOfURL = target, error = null).also { player = it }
            check(playback.play())
            mutable.value = state.value.copy(playing = true, error = null)
            meter?.cancel()
            meter =
                scope.launch {
                    while (isActive && playback.playing) {
                        mutable.value =
                            state.value.copy(playbackPositionMillis = (playback.currentTime * 1000).toLong())
                        delay(50)
                    }
                    playback.currentTime = 0.0
                    mutable.value =
                        state.value.copy(
                            playing = false,
                            playbackPositionMillis = state.value.durationMillis,
                        )
                    session.setActive(false, error = null)
                }
        } catch (_: Exception) {
            pause()
            mutable.value = state.value.copy(error = "녹음을 재생할 수 없어요")
        }
    }

    override fun pause() {
        if (!state.value.recording) meter?.cancel()
        val playbackPosition = player?.currentTime?.let { (it * 1000).toLong() } ?: state.value.playbackPositionMillis
        player?.pause()
        mutable.value = state.value.copy(playing = false, playbackPositionMillis = playbackPosition)
        if (!state.value.recording) session.setActive(false, error = null)
    }

    override fun clear() {
        generation++
        meter?.cancel()
        recorder?.stop()
        recorder = null
        player?.stop()
        player = null
        url?.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        url = null
        session.setActive(false, error = null)
        mutable.value = VoiceRecordingState()
    }

    override fun release() {
        released = true
        NSNotificationCenter.defaultCenter.removeObserver(interruptionObserver)
        clear()
        scope.cancel()
    }
}
