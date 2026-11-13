package com.pheeeew.core.audio

/** Caller serializes access. A bounded burst of sounds may wait; input acceptance is independent. */
internal class PressSoundRequestGate(
    private val maxPending: Int = 8,
) {
    init {
        require(maxPending in 1..32)
    }

    private var generation = 0L
    private var active = false
    private var released = false
    private var pending = 0

    fun resume(): Long {
        generation++
        pending = 0
        active = !released
        return generation
    }

    fun canLoad(): Boolean = !released

    fun activeToken(): Long? = if (active && !released) generation else null

    fun isCurrent(token: Long): Boolean = active && !released && token == generation

    fun reserve(): Long? {
        if (!active || released || pending >= maxPending) return null
        pending++
        return generation
    }

    fun consume(token: Long): Boolean {
        if (!isCurrent(token) || pending == 0) return false
        pending--
        return true
    }

    fun stop() {
        active = false
        generation++
        pending = 0
    }

    fun release() {
        stop()
        released = true
    }
}
