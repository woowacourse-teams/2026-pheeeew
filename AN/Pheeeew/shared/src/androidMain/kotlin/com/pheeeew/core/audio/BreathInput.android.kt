package com.pheeeew.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

@Composable
actual fun rememberBreathInput(): BreathInput {
    val context = LocalContext.current
    val input = remember(context) { AndroidBreathInput(context.applicationContext) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), input::onPermissionResult)
    SideEffect { input.permissionRequester = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) } }
    DisposableEffect(input) { onDispose { input.release() } }
    return input
}

private class AndroidBreathInput(
    private val context: Context,
) : BreathInput {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var wantsRecording = false

    @Volatile private var recording = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var strengthCallback: ((Float) -> Unit)? = null
    private var errorCallback: ((BreathInputError) -> Unit)? = null
    private var permissionContinuation: CancellableContinuation<Boolean>? = null

    @Volatile private var inputGeneration = 0L
    private val strengthProcessor = BreathStrengthProcessor()
    var permissionRequester: (() -> Unit)? = null

    override suspend fun requestPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            permissionContinuation?.cancel()
            permissionContinuation = continuation
            continuation.invokeOnCancellation {
                if (permissionContinuation === continuation) {
                    permissionContinuation = null
                }
            }

            try {
                permissionRequester?.invoke()
                    ?: run {
                        permissionContinuation = null
                        continuation.resume(false)
                    }
            } catch (_: IllegalStateException) {
                permissionContinuation = null
                continuation.resume(false)
            }
        }
    }

    override fun start(
        onStrengthChanged: (Float) -> Unit,
        onError: (BreathInputError) -> Unit,
    ) {
        val generation = ++inputGeneration
        strengthCallback = onStrengthChanged
        errorCallback = onError
        wantsRecording = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording(generation)
        } else {
            permissionRequester?.invoke() ?: publishError(BreathInputError.PermissionDenied)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        permissionContinuation?.let { continuation ->
            permissionContinuation = null
            if (continuation.isActive) continuation.resume(granted)
        }

        if (!granted) {
            wantsRecording = false
            publishError(BreathInputError.PermissionDenied)
        } else if (wantsRecording) {
            startRecording(inputGeneration)
        }
    }

    @Synchronized private fun startRecording(generation: Long) {
        if (recording || !wantsRecording) return
        val sampleRate = 16_000
        val minimumBuffer =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minimumBuffer <= 0) {
            publishError(BreathInputError.MicrophoneUnavailable)
            return
        }
        val bufferSize = max(minimumBuffer, 2_048)
        val newRecorder =
            runCatching {
                AudioRecord
                    .Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(
                                AudioFormat.ENCODING_PCM_16BIT,
                            ).setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    ).setBufferSizeInBytes(bufferSize * 2)
                    .apply { if (android.os.Build.VERSION.SDK_INT >= 30) setPrivacySensitive(true) }
                    .build()
            }.getOrElse {
                publishError(BreathInputError.StartFailed)
                return
            }
        if (newRecorder.state !=
            AudioRecord.STATE_INITIALIZED
        ) {
            newRecorder.release()
            publishError(BreathInputError.MicrophoneUnavailable)
            return
        }
        recorder = newRecorder
        strengthProcessor.reset()
        runCatching { newRecorder.startRecording() }.onFailure {
            newRecorder.release()
            recorder = null
            publishError(BreathInputError.StartFailed)
            return
        }
        recording = true
        worker = thread(name = "breath-input") { analyze(newRecorder, bufferSize, sampleRate, generation) }
    }

    private fun analyze(
        source: AudioRecord,
        bufferSize: Int,
        sampleRate: Int,
        generation: Long,
    ) {
        val samples = ShortArray(bufferSize)
        val dt = 1.0 / sampleRate
        val hp = (1.0 / (2.0 * PI * 80.0)) / ((1.0 / (2.0 * PI * 80.0)) + dt)
        val lp500 = dt / ((1.0 / (2.0 * PI * 500.0)) + dt)
        val lp2000 = dt / ((1.0 / (2.0 * PI * 2_000.0)) + dt)
        var previousInput = 0.0
        var previousHigh = 0.0
        var low500 = 0.0
        var low2000 = 0.0
        var previousBand = 0.0
        while (recording) {
            val count = source.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
            if (generation != inputGeneration) return
            if (count <= 0) continue
            var energy = 0.0
            var lowEnergy = 0.0
            var crossings = 0
            for (index in 0 until count) {
                val input = samples[index].toDouble() / Short.MAX_VALUE
                val high = hp * (previousHigh + input - previousInput)
                previousInput = input
                previousHigh = high
                low500 += lp500 * (high - low500)
                low2000 += lp2000 * (high - low2000)
                energy += low2000 * low2000
                lowEnergy += low500 * low500
                if ((previousBand < 0 && low2000 >= 0) || (previousBand >= 0 && low2000 < 0)) crossings++
                previousBand = low2000
            }
            val rms = sqrt((energy / count).coerceAtLeast(1e-12))
            val amplitude = ((20.0 * ln(rms) / ln(10.0) + 48.0) / 40.0).coerceIn(0.0, 1.0).toFloat()
            val lowPresence =
                (
                    (
                        lowEnergy / count /
                            (energy / count).coerceAtLeast(
                                1e-12,
                            ) - 0.12
                    ) / 0.58
                ).coerceIn(0.0, 1.0).toFloat()
            val texture = ((crossings.toDouble() / count - 0.035) / 0.16).coerceIn(0.0, 1.0).toFloat()
            val strength =
                strengthProcessor.process(
                    BreathSignalMetrics(
                        amplitude = amplitude,
                        lowFrequencyPresence = lowPresence,
                        noisyTexture = texture,
                    ),
                )
            publishStrength(strength, generation)
        }
    }

    override fun stop() {
        inputGeneration += 1L
        wantsRecording = false
        recording = false
        runCatching { recorder?.stop() }
        worker?.interrupt()
        worker = null
        recorder?.release()
        recorder = null
    }

    fun release() {
        stop()
        strengthCallback = null
        errorCallback = null
    }

    private fun publishStrength(
        value: Float,
        generation: Long,
    ) {
        mainHandler.post {
            if (generation == inputGeneration && recording) {
                strengthCallback?.invoke(value.coerceIn(0f, 1f))
            }
        }
    }

    private fun publishError(error: BreathInputError) = mainHandler.post { errorCallback?.invoke(error) }
}
