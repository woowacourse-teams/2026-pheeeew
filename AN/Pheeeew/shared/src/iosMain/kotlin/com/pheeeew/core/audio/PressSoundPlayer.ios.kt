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
import platform.Foundation.NSData
import platform.Foundation.create

@Composable
internal actual fun rememberPressSoundPlayer(): PressSoundPlayer {
    val player = remember { IosPressSoundPlayer() }
    LaunchedEffect(player) {
        runCatching { player.load(Res.readBytes("files/audio/press_click.mp3")) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPressSoundPlayer : PressSoundPlayer {
    private val players = mutableListOf<AVAudioPlayer>()
    private var next = 0
    private var released = false

    fun load(bytes: ByteArray) {
        if (released || bytes.isEmpty()) return
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        repeat(4) {
            val player = AVAudioPlayer(data = data, error = null)
            players.add(player)
        }
    }

    override fun play() {
        if (released || players.isEmpty()) return
        val session = AVAudioSession.sharedInstance()
        if (session.category != AVAudioSessionCategoryAmbient &&
            !session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        ) {
            return
        }
        val index =
            (players.indices)
                .firstOrNull { !players[(next + it) % players.size].playing }
                ?.let { (next + it) % players.size } ?: next
        val player = players[index]
        if (player.playing) player.stop()
        player.currentTime = 0.0
        player.play()
        next = (index + 1) % players.size
    }

    override fun stop() {
        players.forEach { it.stop() }
    }

    fun release() {
        released = true
        stop()
        players.clear()
    }
}
