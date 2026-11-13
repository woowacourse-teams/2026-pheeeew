package com.pheeeew.core.time

import kotlin.time.Instant

/** Returns the calendar date in Korea Standard Time for an epoch-millisecond timestamp. */
fun koreaCalendarDate(epochMillis: Long): String =
    Instant
        .fromEpochMilliseconds(epochMillis + KOREA_UTC_OFFSET_MILLIS)
        .toString()
        .substringBefore('T')

internal fun millisUntilNextKoreaCalendarDay(epochMillis: Long): Long =
    MILLIS_PER_DAY - ((epochMillis + KOREA_UTC_OFFSET_MILLIS) % MILLIS_PER_DAY + MILLIS_PER_DAY) % MILLIS_PER_DAY

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L
private const val KOREA_UTC_OFFSET_MILLIS = 9 * 60 * 60 * 1_000L
