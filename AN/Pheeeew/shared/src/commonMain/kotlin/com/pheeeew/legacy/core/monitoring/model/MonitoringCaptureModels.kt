package com.pheeeew.legacy.core.monitoring

internal const val SEGMENT_MERGE_GAP_MS = 150L
internal const val PARTIAL_OBSERVATION_GAP_MS = 500L

internal data class SaveObservation(
    val origin: MonitoringSnapshot,
    val startedAt: Long,
    var resultAt: Long? = null,
    var minDisplayWaitMs: Long = 0L,
    var foregroundContinuous: Boolean = true,
)
