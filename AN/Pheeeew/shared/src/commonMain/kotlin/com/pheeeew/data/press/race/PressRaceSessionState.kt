package com.pheeeew.data.press.race

internal enum class PressRacePhase { Disconnected, Connecting, Initializing, Ready, Failed, Closed }

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
    val snapshot: PressRaceSnapshot? = null,
    val failure: PressRaceFailure? = null,
)
