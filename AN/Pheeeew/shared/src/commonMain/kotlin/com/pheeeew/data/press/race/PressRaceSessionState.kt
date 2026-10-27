package com.pheeeew.data.press.race

internal enum class PressRacePhase { Disconnected, Connecting, Initializing, Ready, Settled, Failed, Closed }

internal enum class PressRaceFailureKind { Authentication, Transport, Protocol }

/** Deliberately contains no Throwable, credentials, payload or raw exception message. */
internal data class PressRaceFailure(
    val kind: PressRaceFailureKind,
    val httpStatus: Int? = null,
    val closeCode: Short? = null,
    val code: String? = null,
)

internal data class PressRaceSessionState(
    val phase: PressRacePhase = PressRacePhase.Disconnected,
    val view: PressRaceView? = null,
    val ready: PressRaceReady? = null,
    /** Last received snapshot, retained for a stale/reconnecting presentation. */
    val snapshot: PressRaceSnapshot? = null,
    /** Ready only completes initialization; this stays true until the current view baseline arrives. */
    val isAwaitingSnapshot: Boolean = false,
    val failure: PressRaceFailure? = null,
    /** Remains true through backoff/connect/init until a fresh baseline is accepted. */
    val isRecovering: Boolean = false,
    /** Server-observed date lower bound, retained across sockets. */
    val dateFloor: String? = null,
) {
    /** A cached snapshot must never be mistaken for fresh data after init or view change. */
    val currentSnapshot: PressRaceSnapshot?
        get() =
            snapshot?.takeIf {
                phase == PressRacePhase.Ready && !isAwaitingSnapshot && it.view == view &&
                    it.serverGeneration == ready?.serverGeneration &&
                    it.connectionGeneration == ready?.connectionGeneration
            }
}
