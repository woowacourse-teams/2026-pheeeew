package com.pheeeew.feature.screens.group.detail.component

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** CSS pixels map to dp; timings are wall-clock milliseconds, independent of frame rate. */
internal data class TapTransform(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val scale: Double = 1.0,
    val rotation: Double = 0.0,
    val alpha: Double = 1.0,
) {
    fun between(
        other: TapTransform,
        t: Double,
    ) =
        TapTransform(
            x + (other.x - x) * t,
            y + (other.y - y) * t,
            scale + (other.scale - scale) * t,
            rotation + (other.rotation - rotation) * t,
            alpha + (other.alpha - alpha) * t,
        )
}

/** Solves x(t) before evaluating y(t), including the original release overshoot. */
internal fun tapBezier(
    progress: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
): Double {
    if (progress <= 0.0) return 0.0
    if (progress >= 1.0) return 1.0

    fun curve(
        t: Double,
        a: Double,
        b: Double,
    ) = 3 * (1 - t).pow(2) * t * a + 3 * (1 - t) * t * t * b + t * t * t

    var low = 0.0
    var high = 1.0
    repeat(48) {
        val mid = (low + high) / 2
        if (curve(mid, x1, x2) < progress) low = mid else high = mid
    }
    return curve((low + high) / 2, y1, y2)
}

internal class TapButtonMotion {
    private var from = TapTransform()
    private var target = TapTransform()
    private var start = 0.0
    private var duration = 0.0
    private var pressingCurve = false
    private var pressedAt = Double.NEGATIVE_INFINITY
    private var releaseAt: Double? = null

    var pressed = false
        private set

    private fun value(now: Double): TapTransform {
        val t = if (duration == 0.0) 1.0 else ((now - start) / duration).coerceIn(0.0, 1.0)
        val eased =
            if (pressingCurve) {
                tapBezier(t, .2, .7, .3, 1.0)
            } else {
                tapBezier(t, .34, 1.35, .64, 1.0)
            }
        return from.between(target, eased)
    }

    fun sample(now: Double): TapTransform {
        releaseAt?.let { if (now >= it) releaseNow(it, false) }
        return value(now)
    }

    private fun move(
        now: Double,
        next: TapTransform,
        millis: Double,
        press: Boolean,
        reduced: Boolean,
    ) {
        from = value(now) // Retarget from the visible position, never from the resting position.
        target = next
        start = now
        duration = if (reduced) 0.0 else millis
        pressingCurve = press
    }

    fun press(
        now: Double,
        reduced: Boolean = false,
    ) {
        sample(now)
        releaseAt = null
        pressedAt = now
        if (pressed) return
        pressed = true
        move(now, TapTransform(y = 4.0, scale = .985), 70.0, true, reduced)
    }

    fun release(
        now: Double,
        reduced: Boolean = false,
    ) {
        sample(now)
        releaseAt = null
        if (!pressed) {
            if (reduced) move(now, TapTransform(), 0.0, false, true)
            return
        }
        if (!reduced && now - pressedAt < 60) {
            releaseAt = pressedAt + 60
        } else {
            releaseNow(now, reduced)
        }
    }

    private fun releaseNow(
        now: Double,
        reduced: Boolean,
    ) {
        releaseAt = null
        pressed = false
        move(now, TapTransform(), 230.0, false, reduced)
    }

    fun tap(
        now: Double,
        reduced: Boolean = false,
    ) {
        press(now, reduced)
        release(now, reduced)
    }

    fun reset() {
        from = TapTransform()
        target = from
        duration = 0.0
        releaseAt = null
        pressed = false
    }

    fun isRunning(now: Double) = releaseAt != null || now < start + duration
}

/** Same uint32 rejection sampling and draw order as random.js. Not a security API. */
internal class TapRandom(
    private val entropy: () -> UInt = { Random.nextInt().toUInt() },
) {
    fun int(bound: Int): Int {
        require(bound > 0)
        val size = 4_294_967_296L
        val limit = size - size % bound
        var value: Long
        do {
            value = entropy().toLong()
        } while (value >= limit)
        return (value % bound).toInt()
    }

    fun range(
        min: Double,
        max: Double,
    ): Double = min + entropy().toLong() / 4_294_967_296.0 * (max - min)
}

internal data class TapRect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

internal data class TapPoint(
    val x: Double,
    val y: Double,
)

internal data class TapFlight(
    val left: Double,
    val top: Double,
    val duration: Double,
    val frames: List<TapTransform>,
) {
    fun sample(elapsed: Double): TapTransform {
        val t = (elapsed / duration).coerceIn(0.0, 1.0)
        if (frames.size == 3) {
            return if (t <= .2) {
                frames[0].between(frames[1], t / .2)
            } else {
                frames[1].between(frames[2], (t - .2) / .8)
            }
        }
        val p = t * 32
        val i = floor(p).toInt().coerceAtMost(31)
        return frames[i].between(frames[i + 1], p - i)
    }
}

internal object TapTrajectory {
    // Match the JS transform serialization before interpolation by the browser.
    private fun fixed(
        value: Double,
        decimals: Int,
    ): Double {
        val unit = 10.0.pow(decimals)
        return sign(value) * floor(abs(value) * unit + .5) / unit
    }

    fun frames(
        deltaX: Double,
        controlX: Double,
        rise: Double,
        fitScale: Double,
        initialRotation: Double,
        rotation: Double,
    ): List<TapTransform> =
        List(33) { index ->
            val t = index / 32.0
            val p = 1 - (1 - t).pow(3)
            val remaining = 1 - p
            val fade = ((t - .74) / .26).coerceIn(0.0, 1.0)
            TapTransform(
                fixed(2 * remaining * p * controlX + p * p * deltaX, 2),
                fixed(-(2 * remaining * p * rise * .88 + p * p * rise), 2),
                fixed(fitScale * (1 - .42 * exp(-24 * t) * cos(32 * t)), 3),
                fixed(initialRotation + (rotation - initialRotation) * p, 2),
                min(1.0, t / .045) * (1 - fade * fade * (3 - 2 * fade)),
            )
        }

    fun create(
        random: TapRandom,
        button: TapRect,
        pointer: TapPoint?,
        viewportWidth: Double,
        width: Double,
        height: Double,
        combo: Int,
        reduced: Boolean,
    ): TapFlight {
        val originX =
            pointer?.x?.coerceIn(button.left + 16, button.left + button.width - 16)
                ?: (button.left + button.width / 2)
        val originY =
            pointer?.y?.coerceIn(button.top + 16, button.top + button.height - 16)
                ?: (button.top + button.height * .4)
        val bonus = min(22.0, log2(max(1, combo).toDouble()) * 5)
        val rotation = if (reduced) 0.0 else random.range(-12.0, 12.0)
        val initialRotation = if (reduced) 0.0 else rotation + random.range(-5.0, 5.0)
        val rotatedWidth = width + height * sin((max(abs(rotation), abs(initialRotation)) + 3) * PI / 180)
        val fit = min(1.0, max(.1, (viewportWidth - 36) / (rotatedWidth * 1.06)))
        val halfWidth = rotatedWidth * fit * 1.06 / 2
        val minX = 18 + halfWidth
        val maxX = max(minX, viewportWidth - 18 - halfWidth)
        val x = (originX + random.range(-28.0, 28.0)).coerceIn(minX, maxX)
        val y = originY - height / 2 + random.range(-10.0, 7.0)
        val endX = (x + random.range(-44.0, 44.0)).coerceIn(minX, maxX)
        val deltaX = endX - x
        val controlX = (x + deltaX * .35 + random.range(-12.0, 12.0)).coerceIn(minX, maxX) - x
        val rise = random.range(72.0, 106.0) + bonus
        val frames =
            if (reduced) {
                listOf(
                    TapTransform(scale = fixed(fit, 3), alpha = 0.0),
                    TapTransform(scale = fixed(fit, 3)),
                    TapTransform(scale = fixed(fit, 3), alpha = 0.0),
                )
            } else {
                frames(deltaX, controlX, rise, fit, initialRotation, rotation)
            }
        val duration = if (reduced) 150.0 else random.range(760.0, 940.0)
        return TapFlight(x - width / 2, y, duration, frames)
    }
}

/** Per-emotion combo; visual capacity never filters business callbacks. */
internal class TapFeedbackState<K>(
    private val capacity: Int = 84,
) {
    init {
        require(capacity > 0)
    }

    private val lastTaps = mutableMapOf<K, Pair<Double, Int>>()
    val particles = mutableListOf<TapParticle<K>>()
    private var nextId = 0L

    fun combo(
        key: K,
        now: Double,
    ): Int {
        val last = lastTaps[key]
        val combo = if (last != null && now - last.first < 330) min(last.second + 1, 12) else 1
        lastTaps[key] = now to combo
        return combo
    }

    fun add(
        key: K,
        reaction: TapReaction,
        flight: TapFlight,
        now: Double,
        width: Double,
        height: Double,
    ) {
        advance(now)
        while (particles.size >= capacity) particles.removeAt(0)
        particles += TapParticle(++nextId, key, reaction, flight, now, width, height)
    }

    fun advance(now: Double) {
        particles.removeAll { now - it.started >= it.flight.duration }
    }

    fun clear() {
        particles.clear()
    }
}

internal data class TapParticle<K>(
    val id: Long,
    val key: K,
    val reaction: TapReaction,
    val flight: TapFlight,
    val started: Double,
    val width: Double,
    val height: Double,
)
