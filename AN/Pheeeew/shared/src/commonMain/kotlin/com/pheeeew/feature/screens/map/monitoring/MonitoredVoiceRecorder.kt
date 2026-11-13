package com.pheeeew.feature.screens.map.monitoring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.pheeeew.core.audio.VoiceRecorder
import com.pheeeew.core.audio.VoiceRecordingState
import com.pheeeew.core.audio.rememberVoiceRecorder
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.feature.monitoring.product.ProductOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Composable
fun rememberMonitoredVoiceRecorder(funnel: RecordFunnelMonitoring): VoiceRecorder {
    val recorder = rememberVoiceRecorder()
    val scope = rememberCoroutineScope()
    val observed = remember(recorder, funnel) { MonitoredVoiceRecorder(recorder, funnel, scope) }
    DisposableEffect(observed) { onDispose { observed.close() } }
    return observed
}

private class MonitoredVoiceRecorder(
    private val delegate: VoiceRecorder,
    private val funnel: RecordFunnelMonitoring,
    scope: CoroutineScope,
) : VoiceRecorder {
    override val state = delegate.state
    private var recording: ProductOperation? = null
    private var preview: ProductOperation? = null
    private var permissionPending = false
    private var hadRecording = false
    private var previewDuration = 0L
    private val observer = scope.launch { state.collect { update(it) } }

    private fun update(value: VoiceRecordingState) {
        if (value.requestingPermission) permissionPending = true
        if (permissionPending && !value.requestingPermission) {
            permissionPending = false
            funnel.microphonePermission(
                if (value.microphonePermissionGranted) {
                    "granted"
                } else if (value.microphonePermissionDenied) {
                    "denied"
                } else {
                    "cancelled"
                },
            )
        }
        if (value.recording) hadRecording = true
        if (recording != null && !value.recording && !value.requestingPermission &&
            (hadRecording || value.error != null || value.microphonePermissionDenied)
        ) {
            finishRecording(if (hadRecording && value.filePath != null) "completed" else "start_failed", value)
        }
        if (value.playing && preview == null) {
            previewDuration = value.durationMillis
            preview =
                funnel.observe(
                    "voice_preview_finished",
                    mapOf("playback_id" to EventValue.Text(Uuid.random().toString())),
                )
        }
        if (!value.playing && preview != null) {
            preview?.finish(
                if (value.error !=
                    null
                ) {
                    "error"
                } else if (value.durationMillis > 0 &&
                    value.playbackPositionMillis >= value.durationMillis
                ) {
                    "completed"
                } else {
                    "stopped"
                },
                mapOf(
                    "played_ms" to EventValue.Integer(value.playbackPositionMillis.coerceAtLeast(0)),
                    "media_duration_ms" to EventValue.Integer(previewDuration.coerceAtLeast(0)),
                ),
            )
            preview = null
        }
    }

    private fun finishRecording(
        outcome: String,
        value: VoiceRecordingState = state.value,
    ) {
        recording?.finish(
            outcome,
            mapOf("media_duration_ms" to EventValue.Integer(value.durationMillis.coerceAtLeast(0))),
        )
        recording = null
        hadRecording = false
    }

    override fun start() {
        if (state.value.recording || state.value.requestingPermission) return
        recording =
            funnel.observe(
                "voice_recording_finished",
                mapOf("recording_id" to EventValue.Text(Uuid.random().toString())),
            )
        delegate.start()
        update(state.value)
        if (!state.value.recording && !state.value.requestingPermission) finishRecording("start_failed")
    }

    override fun stop() {
        delegate.stop()
        update(state.value)
    }

    override fun clear() {
        finishRecording("cancelled")
        delegate.clear()
        update(state.value)
    }

    override fun togglePlayback() {
        delegate.togglePlayback()
        update(state.value)
    }

    override fun pause() {
        delegate.pause()
        update(state.value)
    }

    override fun refreshPermissionStatus() = delegate.refreshPermissionStatus()

    override fun requestMicrophonePermission() = delegate.requestMicrophonePermission()

    override fun release() {
        close()
        delegate.release()
    }

    fun close() {
        finishRecording("cancelled")
        pause()
        observer.cancel()
    }
}
