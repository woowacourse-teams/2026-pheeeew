package com.pheeeew.data.press.race

/** Fixed metadata only: no tokens, IDs, nicknames, text frames or server error payloads. */
internal enum class PressRaceDiagnostic {
    UpgradeOpened,
    TokenRecovered,
    ReadyReceived,
    BaselineReceived,
    AppliedResultReceived,
    RejectedResultReceived,
    RetryScheduled,
    RecoveryOnlySettled,
    Inactive,
}

internal fun interface PressRaceDiagnostics {
    fun record(
        event: PressRaceDiagnostic,
        localConnection: Long,
    )
}
