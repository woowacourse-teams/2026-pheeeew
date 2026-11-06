package com.pheeeew.feature.emotion.component

import kotlin.time.TimeSource

/** Bounded debug correlation from the input callback to the first draw of its particle. */
internal class EmotionTapDrawProbe(
    private val enabled: Boolean,
    private val clockMicros: () -> Long = newEmotionTapDrawClock(),
    private val report: (String) -> Unit = ::println,
) {
    internal class Token internal constructor(
        internal val session: Any,
        internal val id: Long,
    )

    private data class Sample(
        val id: Long,
        val startedAtMicros: Long,
        var callbackMicros: Long? = null,
        var accepted: Boolean? = null,
        var particleId: Long? = null,
    )

    private val session = Any()
    private val samples = linkedMapOf<Long, Sample>()
    private val sampleByParticleId = mutableMapOf<Long, Long>()
    private var nextSampleId = 0L
    private var startedSamples = 0
    private var overflowReported = false
    private var closed = false

    val isEnabled: Boolean get() = enabled && !closed

    /** Call immediately before the existing business callback. */
    fun begin(): Token? {
        if (!isEnabled) return null
        if (startedSamples >= MAX_SAMPLES) {
            reportOverflow("sample_limit")
            return null
        }
        if (samples.size >= MAX_PENDING_SAMPLES) {
            reportOverflow("pending_limit")
            return null
        }
        startedSamples++
        val id = ++nextSampleId
        val start = runCatching(clockMicros).getOrNull()
        if (start == null) {
            emit("sample=$id outcome=missing phase=clock_start")
            return null
        }
        samples[id] = Sample(id, start)
        return Token(session, id)
    }

    /** Record the acceptance only after the existing callback returns. */
    fun callbackReturned(
        token: Token?,
        accepted: Boolean,
    ) {
        val sample = sample(token) ?: return
        sample.callbackMicros = elapsedMicros(sample)
        sample.accepted = accepted
        if (!accepted) finish(sample, "rejected")
    }

    fun callbackThrew(token: Token?) {
        sample(token)?.let { finish(it, "callback_exception") }
    }

    /** Associate only the particle actually inserted into TapFeedbackState. */
    fun particleAdded(
        token: Token?,
        particleId: Long,
    ) {
        val sample = sample(token) ?: return
        if (sample.accepted != true || sample.particleId != null) return
        if (sampleByParticleId.containsKey(particleId)) {
            finish(sample, "overflow", "particle_id_collision")
            reportOverflow("particle_id_collision")
            return
        }
        sample.particleId = particleId
        sampleByParticleId[particleId] = sample.id
    }

    /** Finish an accepted callback that returned without inserting a correlatable particle. */
    fun feedbackCompleted(token: Token?) {
        val sample = sample(token) ?: return
        if (sample.accepted == true && sample.particleId == null) {
            finish(sample, "missing", "particle_not_added")
        }
    }

    /** A failure before insertion is missing; a failure after insertion still awaits its draw. */
    fun feedbackThrew(token: Token?) {
        val sample = sample(token) ?: return
        if (sample.accepted == true && sample.particleId == null) {
            finish(sample, "missing", "feedback_exception")
        }
    }

    /** Called after drawContent succeeds for this particle's Compose layer. */
    fun particleDrawn(particleId: Long) {
        val sampleId = sampleByParticleId[particleId] ?: return
        val sample = samples[sampleId] ?: return
        if (sample.particleId != particleId || sample.accepted != true) return
        finish(sample, "drawn")
    }

    fun particleRemoved(
        particleId: Long,
        outcome: RemovalOutcome,
        reason: String,
    ) {
        val sampleId = sampleByParticleId[particleId] ?: return
        val sample = samples[sampleId] ?: return
        finish(sample, outcome.value, reason)
    }

    /** Clear every still-pending sample when the feedback state is settled or disposed. */
    fun cancelAll(reason: String) {
        if (!isEnabled) return
        samples.values.toList().forEach { finish(it, "cancelled", reason) }
    }

    fun close() {
        if (!isEnabled) return
        cancelAll("disposed")
        closed = true
    }

    private fun sample(token: Token?): Sample? {
        if (!isEnabled || token == null || token.session !== session) return null
        return samples[token.id]
    }

    private fun elapsedMicros(sample: Sample): Long? =
        runCatching { (clockMicros() - sample.startedAtMicros).coerceAtLeast(0L) }.getOrNull()

    private fun finish(
        sample: Sample,
        outcome: String,
        reason: String? = null,
    ) {
        if (samples.remove(sample.id) == null) return
        sample.particleId?.let { particleId ->
            if (sampleByParticleId[particleId] == sample.id) sampleByParticleId.remove(particleId)
        }
        val elapsed = elapsedMicros(sample)
        val callback = sample.callbackMicros
        val detail =
            when (outcome) {
                "drawn" -> {
                    " callbackUs=${callback ?: "unknown"} tapToDrawUs=${elapsed ?: "unknown"}" +
                        " drawAfterCallbackUs=${if (callback != null && elapsed != null) (elapsed - callback).coerceAtLeast(0L) else "unknown"}"
                }

                "rejected", "callback_exception" -> " callbackUs=${callback ?: elapsed ?: "unknown"}"
                else -> " elapsedUs=${elapsed ?: "unknown"}"
            }
        val acceptance = sample.accepted?.let { " accepted=$it" } ?: ""
        emit("sample=${sample.id} outcome=$outcome${acceptance}${reason?.let { " reason=$it" } ?: ""}$detail")
    }

    private fun reportOverflow(reason: String) {
        if (overflowReported) return
        overflowReported = true
        emit("outcome=overflow reason=$reason limit=$MAX_PENDING_SAMPLES samples=$MAX_SAMPLES")
    }

    private fun emit(message: String) {
        runCatching { report("[EmotionTapDrawProbe] $message") }
    }

    internal enum class RemovalOutcome(val value: String) {
        Cancelled("cancelled"),
        Evicted("evicted"),
        Missing("missing"),
    }

    private companion object {
        const val MAX_PENDING_SAMPLES = 84
        const val MAX_SAMPLES = 128
    }
}

internal data class EmotionTapCallbackResult(
    val accepted: Boolean,
    val token: EmotionTapDrawProbe.Token?,
)

/** Keeps diagnostic exceptions from replacing the callback result or its original exception. */
internal fun invokeEmotionTapCallback(
    probe: EmotionTapDrawProbe?,
    callback: () -> Boolean,
): EmotionTapCallbackResult {
    val token = probe?.begin()
    return try {
        val accepted = callback()
        probe?.callbackReturned(token, accepted)
        EmotionTapCallbackResult(accepted, token)
    } catch (failure: Throwable) {
        probe?.callbackThrew(token)
        throw failure
    }
}

internal fun newEmotionTapDrawClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMicroseconds }
}
