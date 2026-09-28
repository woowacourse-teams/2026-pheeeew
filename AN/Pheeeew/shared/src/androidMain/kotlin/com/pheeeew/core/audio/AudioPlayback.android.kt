package com.pheeeew.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberAudioPlayback(): AudioPlayback {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { AndroidAudioPlayback(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

internal class AndroidAudioPlayback(
    private val context: Context,
) : AudioPlayback {
    private val mutable = MutableStateFlow(AudioPlaybackState.Empty)
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var player: MediaPlayer? = null
    private var file: File? = null
    private var released = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
            .build()

    override suspend fun prepare(bytes: ByteArray) {
        check(!released)
        clear()
        val target = File.createTempFile("emotion-audio-", ".m4a", context.cacheDir)
        var prepared: MediaPlayer? = null
        var installed = false
        try {
            val waveform =
                withContext(Dispatchers.IO) {
                    target.writeBytes(bytes)
                    decodeAndroidAudioWaveform(target.absolutePath)
                }
            check(!released)
            val playback = MediaPlayer()
            prepared = playback
            playback.setAudioAttributes(attributes)
            playback.setDataSource(target.absolutePath)
            suspendCancellableCoroutine<Unit> { continuation ->
                playback.setOnPreparedListener { if (continuation.isActive) continuation.resume(Unit) }
                playback.setOnErrorListener { _, _, _ ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Audio preparation failed"),
                        )
                    }
                    true
                }
                playback.prepareAsync()
            }
            check(!released)
            player = playback
            file = target
            installed = true
            mutable.value = AudioPlaybackState(playback.duration.toLong(), 0, waveform, false, null)
            playback.setOnCompletionListener {
                ticker?.cancel()
                mutable.value = state.value.copy(positionMillis = state.value.durationMillis, isPlaying = false)
                audioManager.abandonAudioFocusRequest(focus)
            }
            playback.setOnErrorListener { _, _, _ ->
                stop()
                mutable.value = state.value.copy(error = "녹음을 재생할 수 없어요. 다시 시도해주세요")
                true
            }
        } finally {
            if (!installed) {
                prepared?.release()
                target.delete()
            }
        }
    }

    override fun togglePlayback() {
        if (state.value.isPlaying) {
            stop()
            return
        }
        val playback = player ?: return
        try {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            playback.seekTo(0)
            playback.start()
            mutable.value = state.value.copy(positionMillis = 0, isPlaying = true, error = null)
            ticker?.cancel()
            ticker =
                scope.launch {
                    while (isActive && state.value.isPlaying) {
                        delay(50)
                        mutable.value = state.value.copy(positionMillis = playback.currentPosition.toLong())
                    }
                }
        } catch (_: Exception) {
            stop()
            mutable.value = state.value.copy(error = "녹음을 재생할 수 없어요. 다시 시도해주세요")
        }
    }

    override fun stop() {
        ticker?.cancel()
        player?.let { playback ->
            runCatching {
                playback.pause()
                playback.seekTo(0)
            }
        }
        audioManager.abandonAudioFocusRequest(focus)
        mutable.value = state.value.copy(positionMillis = 0, isPlaying = false)
    }

    private fun clear() {
        stop()
        player?.release()
        player = null
        file?.delete()
        file = null
        mutable.value = AudioPlaybackState.Empty
    }

    override fun release() {
        released = true
        clear()
        scope.cancel()
    }
}
