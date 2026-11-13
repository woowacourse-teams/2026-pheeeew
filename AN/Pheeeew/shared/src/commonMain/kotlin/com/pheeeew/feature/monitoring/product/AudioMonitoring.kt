package com.pheeeew.feature.monitoring.product

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.pheeeew.core.audio.AudioPlayer
import com.pheeeew.core.audio.EmotionAudioPlayer
import com.pheeeew.core.audio.EmotionPlaybackState
import com.pheeeew.core.audio.rememberAudioPlayer
import com.pheeeew.core.monitoring.EventValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** One actual playback interval. Listening time follows native media position samples. */
class AudioMonitoring(
    private val telemetry: ProductMonitoring,
    private val fields: Map<String, EventValue>,
) {
    private var playingSince: TimeSource.Monotonic.ValueTimeMark? = null
    private var playbackFields = emptyMap<String, EventValue>()
    private var duration = 0L
    private var lastPosition = 0L
    private var played = 0L

    fun update(
        playing: Boolean,
        durationMillis: Long,
        completed: Boolean = false,
        error: Boolean = false,
        positionMillis: Long = 0L,
    ) {
        if (playingSince != null && positionMillis >= lastPosition) played += positionMillis - lastPosition
        if (playingSince != null || playing) lastPosition = positionMillis.coerceAtLeast(0)
        duration = maxOf(duration, durationMillis)
        if (playing && playingSince == null) {
            playbackFields = fields + labels("playback_id" to Uuid.random().toString())
            played = 0
            playingSince = TimeSource.Monotonic.markNow()
            telemetry.emit("emotion_audio_playback_started", playbackFields)
        } else if (!playing) {
            finish(
                if (error) {
                    "error"
                } else if (completed) {
                    "completed"
                } else {
                    "stopped"
                },
            )
        }
    }

    fun finish(outcome: String) {
        playingSince ?: return
        playingSince = null
        val properties =
            playbackFields + labels("outcome" to outcome) +
                mapOf("played_ms" to EventValue.Integer(played), "media_duration_ms" to EventValue.Integer(duration)) +
                if (duration >
                    0
                ) {
                    mapOf("completion_ratio" to EventValue.Decimal((played.toDouble() / duration).coerceIn(0.0, 1.0)))
                } else {
                    emptyMap()
                }
        telemetry.emit("emotion_audio_playback_finished", properties)
    }
}

@Composable
fun rememberObservedEmotionPlayer(
    telemetry: ProductMonitoring,
    viewId: () -> String,
): EmotionAudioPlayer {
    val raw = rememberAudioPlayer()
    val scope = rememberCoroutineScope()
    val currentView by rememberUpdatedState(viewId)
    val adapter = remember(raw, telemetry) { ObservedEmotionPlayer(raw, telemetry, scope) { currentView() } }
    DisposableEffect(adapter) { onDispose { adapter.close() } }
    return adapter
}

private class ObservedEmotionPlayer(
    private val raw: AudioPlayer,
    private val telemetry: ProductMonitoring,
    scope: CoroutineScope,
    private val viewId: () -> String,
) : EmotionAudioPlayer {
    private val mutable = MutableStateFlow(EmotionPlaybackState())
    override val state = mutable.asStateFlow()
    private var entry: Long? = null
    private var load: ProductOperation? = null
    private var audio: AudioMonitoring? = null
    private val observer =
        scope.launch {
            raw.state.collect { value ->
                if (value.playing) {
                    load?.finish("success")
                    load = null
                } else if (value.error != null) {
                    load?.finish("failed")
                    load = null
                }
                audio?.update(
                    value.playing,
                    value.durationMillis,
                    value.completed,
                    value.error != null,
                    value.positionMillis,
                )
                mutable.value = EmotionPlaybackState(entry.takeIf { value.playing || value.loading }, value.error)
            }
        }

    override fun play(
        id: Long,
        url: String,
    ) {
        audio?.finish("replaced")
        load?.finish("cancelled")
        entry = id
        val fields = labels("entry_key" to id.toString(), "entry_source" to "list", "view_id" to viewId())
        audio = AudioMonitoring(telemetry, fields)
        load = telemetry.operation("emotion_audio_load_finished", fields)
        raw.play(url)
    }

    fun stopForReplacement() {
        audio?.finish("replaced")
        stop()
    }

    override fun stop() {
        audio?.finish("stopped")
        audio = null
        load?.finish("cancelled")
        load = null
        entry = null
        raw.stop()
        mutable.value = EmotionPlaybackState()
    }

    fun close() {
        stop()
        observer.cancel()
    }
}

fun stopForReplacement(player: EmotionAudioPlayer) {
    if (player is ObservedEmotionPlayer) player.stopForReplacement() else player.stop()
}
