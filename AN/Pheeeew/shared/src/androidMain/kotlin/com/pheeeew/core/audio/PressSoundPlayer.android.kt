package com.pheeeew.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pheeeew.shared.generated.resources.Res
import java.io.File

@Composable
internal actual fun rememberPressSoundPlayer(): PressSoundPlayer {
    val context = LocalContext.current
    val player = remember(context) { AndroidPressSoundPlayer(context.applicationContext) }
    LaunchedEffect(player) {
        runCatching {
            val bytes = Res.readBytes("files/audio/press_click.mp3")
            val file =
                withContext(Dispatchers.IO) {
                    File.createTempFile("press-click-", ".mp3", context.cacheDir).also { it.writeBytes(bytes) }
                }
            try {
                player.load(file)
            } finally {
                file.delete()
            }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private class AndroidPressSoundPlayer(
    context: Context,
) : PressSoundPlayer {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool =
        SoundPool
            .Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(
                        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                    ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
    private var soundId = 0
    private var ready = false
    private var released = false
    private val streams = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (!released && sampleId == soundId) ready = status == 0
        }
    }

    fun load(file: File) {
        if (!released) soundId = pool.load(file.absolutePath, 1)
    }

    override fun play() {
        if (!released && ready && audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val stream = pool.play(soundId, 1f, 1f, 1, 0, 1f)
            if (stream != 0) {
                streams.add(stream)
                if (streams.size > 4) streams.remove(streams.first())
            }
        }
    }

    override fun stop() {
        streams.forEach(pool::stop)
        streams.clear()
    }

    fun release() {
        released = true
        ready = false
        pool.release()
        streams.clear()
    }
}
