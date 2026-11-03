package com.pheeeew.core.haptics

internal interface EmotionHapticBackend {
    fun start(): Boolean

    fun play(): Boolean

    fun invalidatePlayer()

    fun close()
}

/** All operations belong to one serial executor. A recovering session never replays an old tap. */
internal class EmotionHapticSession(
    private val supported: Boolean,
    private val nowMillis: () -> Long,
    private val createBackend: () -> EmotionHapticBackend?,
) {
    private var active = false
    private var backend: EmotionHapticBackend? = null
    private var ready = false
    private var retryAfter = 0L

    fun activate() {
        if (active) return
        active = true
        retryAfter = 0
        ensureReady()
    }

    fun deactivate() {
        active = false
        ready = false
        val previous = backend
        backend = null
        previous?.close()
    }

    fun owns(source: EmotionHapticBackend): Boolean = backend === source

    fun stopped(source: EmotionHapticBackend) {
        if (owns(source)) ready = false
    }

    fun reset(source: EmotionHapticBackend) {
        if (!owns(source)) return
        ready = false
        source.invalidatePlayer()
        retryAfter = 0
        ensureReady()
    }

    fun play() {
        if (!active || !supported) return
        if (!ready) {
            ensureReady()
            return
        }
        val current = backend ?: return
        if (!current.play()) {
            ready = false
            backend = null
            current.close()
            retryAfter = nowMillis() + RETRY_DELAY_MS
        }
    }

    private fun ensureReady() {
        if (!active || !supported || ready || nowMillis() < retryAfter) return
        val current = backend ?: createBackend()?.also { backend = it }
        if (current != null && current.start()) {
            ready = true
        } else {
            backend = null
            current?.close()
            retryAfter = nowMillis() + RETRY_DELAY_MS
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 1000L
    }
}
