package com.pheeeew.data.press.retry

import com.pheeeew.domain.press.time.PressDelayMillis
import io.ktor.http.fromHttpToGmtDate
import kotlin.time.Clock

/** Invalid or absent values defer to the delivery policy's exponential backoff. */
internal fun pressRetryAfter(
    value: String?,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): PressDelayMillis {
    val raw = value?.trim() ?: return PressDelayMillis.Zero
    if (raw.isNotEmpty() && raw.all { it in '0'..'9' }) {
        val seconds = raw.toLongOrNull() ?: return PressDelayMillis(Long.MAX_VALUE)
        return PressDelayMillis(if (seconds > Long.MAX_VALUE / 1000L) Long.MAX_VALUE else seconds * 1000L)
    }
    val timestamp = runCatching { raw.fromHttpToGmtDate().timestamp }.getOrNull() ?: return PressDelayMillis.Zero
    if (timestamp <= nowMillis) return PressDelayMillis.Zero
    val delay = if (nowMillis < 0 && timestamp > Long.MAX_VALUE + nowMillis) Long.MAX_VALUE else timestamp - nowMillis
    return PressDelayMillis(delay)
}
