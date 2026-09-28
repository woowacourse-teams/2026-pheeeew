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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { AndroidAudioPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

internal class AndroidAudioPlayer(
    context: Context,
) : AudioPlayer {
    private val mutable = MutableStateFlow(AudioPlayerState())
    override val state = mutable.asStateFlow()
    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            while (isActive) {
                delay(100)
                if (state.value.playing) {
                    runCatching { player?.currentPosition?.toLong() }.getOrNull()?.let {
                        mutable.value = state.value.copy(positionMillis = it.coerceAtLeast(0))
                    }
                }
            }
        }
    }

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
            .setOnAudioFocusChangeListener { if (it < 0) pause() }
            .build()

    override fun play(source: String) {
        try {
            if (state.value.source == source && player != null && !state.value.loading) {
                check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                player?.start()
                mutable.value = state.value.copy(playing = true, error = null, completed = false)
                return
            }
            stop()
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val next = MediaPlayer()
            player = next
            mutable.value = AudioPlayerState(source, loading = true)
            next.setAudioAttributes(attributes)
            next.setOnPreparedListener {
                if (player === it) {
                    it.start()
                    mutable.value =
                        state.value.copy(
                            playing = true,
                            loading = false,
                            durationMillis = it.duration.toLong(),
                            completed = false,
                        )
                }
            }
            next.setOnCompletionListener {
                if (player === it) {
                    it.seekTo(0)
                    audioManager.abandonAudioFocusRequest(focus)
                    mutable.value =
                        state.value.copy(playing = false, completed = true, positionMillis = state.value.durationMillis)
                }
            }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) fail()
                true
            }
            next.setDataSource(source)
            next.prepareAsync()
        } catch (_: Exception) {
            fail()
        }
    }

    private fun fail() {
        stop()
        mutable.value = AudioPlayerState(error = "녹음을 재생할 수 없어. 다시 시도해.")
    }

    override fun pause() {
        if (state.value.loading) {
            stop()
            return
        }
        runCatching { player?.pause() }
        audioManager.abandonAudioFocusRequest(focus)
        mutable.value = state.value.copy(playing = false)
    }

    override fun stop() {
        val previous = player
        player = null
        previous?.release()
        audioManager.abandonAudioFocusRequest(focus)
        mutable.value = AudioPlayerState()
    }

    override fun release() {
        stop()
        scope.cancel()
    }
}
