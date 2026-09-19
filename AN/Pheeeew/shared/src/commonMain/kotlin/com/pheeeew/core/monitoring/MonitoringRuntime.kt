package com.pheeeew.core.monitoring

/**
 * Shared runtime services used by monitoring coordinators.
 *
 * Coordinators depend on this small runtime boundary instead of receiving a long list of
 * individual callbacks from [Monitoring]. The event schema and persistence rules remain owned by
 * the façade and recorder.
 */
internal class MonitoringRuntime(
    val now: () -> Long,
    val elapsed: () -> Long,
    val id: () -> String,
    val currentState: () -> MonitoringState,
    val replaceState: (MonitoringState) -> Unit,
    val usable: () -> Boolean,
    val screen: () -> String,
    val phase: () -> String,
    val setPhase: (String) -> Unit,
    val attempt: () -> MonitoringSnapshot?,
    val activeSave: () -> MonitoringSnapshot?,
    val change: (() -> Unit) -> Unit,
    val emit: (String, MonitoringSnapshot, Map<String, Any>, String?) -> Unit,
    val endRecord: (MonitoringSnapshot, String, String, String) -> Unit,
    val observe: () -> Unit,
    val updateContext: () -> Unit,
    val clearAttempt: () -> Unit,
    val resetForAttempt: () -> Unit,
    val flush: () -> Unit,
    val drain: () -> Unit,
)
