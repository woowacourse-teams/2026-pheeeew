package com.pheeeew.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import java.io.File
import kotlin.math.log10

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val context = LocalContext.current.applicationContext
    val recorder = remember(context) { AndroidVoiceRecorder(context) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), recorder::permissionResult)
    SideEffect { recorder.launchPermissionRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) } }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    return recorder
}

private class AndroidVoiceRecorder(
    private val context: Context,
) : VoiceRecorder {
    private val mutable = MutableStateFlow(VoiceRecordingState())
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var meter: Job? = null
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var file: File? = null
    private var released = false
    private var startAfterPermission = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            ).setOnAudioFocusChangeListener { change ->
                if (change < 0) {
                    stop()
                    pause()
                }
            }.build()
    var launchPermissionRequest: (() -> Unit)? = null

    override fun refreshPermissionStatus() {
        mutable.value = state.value.copy(microphonePermissionGranted = hasMicrophonePermission())
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    override fun requestMicrophonePermission() {
        requestPermission(startRecording = false)
    }

    override fun start() {
        requestPermission(startRecording = true)
    }

    private fun requestPermission(startRecording: Boolean) {
        if (released || state.value.recording || state.value.requestingPermission) return
        refreshPermissionStatus()
        if (!state.value.microphonePermissionGranted) {
            startAfterPermission = startRecording
            mutable.value =
                state.value.copy(
                    requestingPermission = true,
                    microphonePermissionDenied = false,
                    error = null,
                )
            val launch = launchPermissionRequest
            if (launch == null) {
                startAfterPermission = false
                mutable.value = state.value.copy(requestingPermission = false)
                return
            }
            try {
                launch()
            } catch (_: IllegalStateException) {
                startAfterPermission = false
                mutable.value = state.value.copy(requestingPermission = false)
            }
            return
        }
        if (startRecording) begin()
    }

    fun permissionResult(granted: Boolean) {
        if (released || !state.value.requestingPermission) return
        val shouldStart = startAfterPermission
        startAfterPermission = false
        mutable.value =
            state.value.copy(
                requestingPermission = false,
                microphonePermissionGranted = granted,
                microphonePermissionDenied = !granted,
            )
        if (granted && shouldStart) begin()
    }

    @Suppress("DEPRECATION")
    private fun begin() {
        clear()
        try {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val target = File.createTempFile("voice-", ".m4a", context.cacheDir)
            file = target
            val capture = MediaRecorder()
            recorder = capture
            capture.setAudioSource(MediaRecorder.AudioSource.MIC)
            capture.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            capture.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            capture.setAudioSamplingRate(44100)
            capture.setAudioEncodingBitRate(128000)
            capture.setOutputFile(target.absolutePath)
            capture.setOnErrorListener { _, _, _ -> fail() }
            capture.prepare()
            capture.start()
            mutable.value = VoiceRecordingState(recording = true)
            val started = SystemClock.elapsedRealtime()
            meter =
                scope.launch {
                    while (isActive) {
                        delay(80)
                        val elapsedMillis = SystemClock.elapsedRealtime() - started
                        val recordedDurationMillis =
                            elapsedMillis.coerceAtMost(MAX_RECORDING_DURATION_SECONDS * 1000L)
                        val amplitude =
                            runCatching { capture.maxAmplitude }.getOrElse {
                                fail()
                                return@launch
                            }
                        val level =
                            if (amplitude ==
                                0
                            ) {
                                0f
                            } else {
                                ((20 * log10(amplitude / 32767.0) + 60) / 60).toFloat().coerceIn(0f, 1f)
                            }
                        mutable.value =
                            state.value.copy(
                                elapsedSeconds = (recordedDurationMillis / 1000).toInt(),
                                durationMillis = recordedDurationMillis,
                                samples = (state.value.samples + level).takeLast(7500),
                            )
                        if (elapsedMillis >= MAX_RECORDING_DURATION_SECONDS * 1000L) {
                            stop()
                            return@launch
                        }
                    }
                }
        } catch (_: Exception) {
            fail()
        }
    }

    private fun fail() {
        clear()
        mutable.value = state.value.copy(error = "녹음할 수 없어요. 마이크를 확인하고 다시 시도해주세요")
    }

    override fun stop() {
        if (state.value.requestingPermission) mutable.value = state.value.copy(requestingPermission = false)
        val capture = recorder ?: return
        meter?.cancel()
        recorder = null
        val success = runCatching { capture.stop() }.isSuccess
        capture.release()
        audioManager.abandonAudioFocusRequest(focus)
        if (success && (file?.length() ?: 0) > 0) {
            mutable.value = state.value.copy(recording = false, filePath = file?.absolutePath)
        } else {
            fail()
        }
    }

    override fun togglePlayback() {
        if (state.value.playing) {
            pause()
            return
        }
        val path = state.value.filePath ?: return
        try {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val playback =
                player ?: MediaPlayer().also {
                    player = it
                    it.setDataSource(path)
                    it.prepare()
                    it.setOnCompletionListener {
                        mutable.value =
                            state.value.copy(
                                playing = false,
                                playbackPositionMillis = state.value.durationMillis,
                            )
                        it.seekTo(0)
                        audioManager.abandonAudioFocusRequest(focus)
                    }
                    it.setOnErrorListener { _, _, _ ->
                        pause()
                        mutable.value = state.value.copy(error = "녹음을 재생할 수 없어요")
                        true
                    }
                }
            playback.start()
            mutable.value =
                state.value.copy(
                    playing = true,
                    playbackPositionMillis = playback.currentPosition.toLong(),
                    error = null,
                )
            meter?.cancel()
            meter =
                scope.launch {
                    while (isActive && state.value.playing) {
                        delay(50)
                        mutable.value = state.value.copy(playbackPositionMillis = playback.currentPosition.toLong())
                    }
                }
        } catch (_: Exception) {
            audioManager.abandonAudioFocusRequest(focus)
            player?.release()
            player = null
            mutable.value = state.value.copy(playing = false, error = "녹음을 재생할 수 없어요")
        }
    }

    override fun pause() {
        val playbackPosition = player?.currentPosition?.toLong() ?: state.value.playbackPositionMillis
        runCatching { player?.pause() }
        if (!state.value.recording) meter?.cancel()
        if (!state.value.recording) audioManager.abandonAudioFocusRequest(focus)
        mutable.value = state.value.copy(playing = false, playbackPositionMillis = playbackPosition)
    }

    override fun clear() {
        startAfterPermission = false
        meter?.cancel()
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        player?.release()
        player = null
        file?.delete()
        file = null
        audioManager.abandonAudioFocusRequest(focus)
        mutable.value = VoiceRecordingState()
    }

    override fun release() {
        released = true
        clear()
        scope.cancel()
    }
}
