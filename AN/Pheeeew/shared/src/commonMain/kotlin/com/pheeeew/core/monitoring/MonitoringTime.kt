package com.pheeeew.core.monitoring

import kotlin.time.Instant

/** Single source of truth for timestamps and the Seoul calendar date used by cohorts. */
internal object MonitoringTime {
    private const val SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000L

    fun iso(epochMs: Long): String = Instant.fromEpochMilliseconds(epochMs).toString()

    fun seoulDay(epochMs: Long): String = iso(epochMs + SEOUL_OFFSET_MS).take(10)
}
