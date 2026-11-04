package com.pheeeew.core.haptics

/** Call under the platform lock. One latest request prevents a delayed vibration backlog. */
internal class EmotionHapticRequestGate {
    private val owners = mutableSetOf<Any>()
    private var enabled = true
    private var generation = 0L
    private var scheduled = false
    private var pending: Request? = null

    val active: Boolean get() = enabled && owners.isNotEmpty()
    val ownerCount: Int get() = owners.size

    fun activate(owner: Any) {
        owners.add(owner)
    }

    fun deactivate(owner: Any) {
        if (owners.remove(owner)) invalidate()
    }

    fun setEnabled(value: Boolean) {
        if (enabled != value) {
            enabled = value
            invalidate()
        }
    }

    fun invalidate() {
        generation++
        pending = null
    }

    fun reserve(
        owner: Any,
        atMillis: Long,
    ): Boolean {
        if (!active || owner !in owners) return false
        pending = Request(owner, generation, atMillis)
        if (scheduled) return false
        scheduled = true
        return true
    }

    fun consume(nowMillis: Long): Boolean {
        val request = pending
        pending = null
        scheduled = false
        return request != null && active && request.owner in owners &&
            request.generation == generation && nowMillis - request.atMillis in 0..MAX_AGE_MS
    }

    private data class Request(
        val owner: Any,
        val generation: Long,
        val atMillis: Long,
    )

    private companion object {
        // Match the existing press sound's stale-feedback cutoff; this is not a haptic rate limit.
        const val MAX_AGE_MS = 80L
    }
}
