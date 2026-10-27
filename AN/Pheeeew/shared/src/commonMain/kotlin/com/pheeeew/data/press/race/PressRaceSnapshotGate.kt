package com.pheeeew.data.press.race

/** Serial-actor-owned cursor. Reset for every local connection, even if server identifiers repeat. */
internal class PressRaceSnapshotGate {
    private var version: String? = null

    fun reset() {
        version = null
    }

    /** Reject stale frames; malformed current frames remain protocol failures for the session to handle. */
    fun accept(
        snapshot: PressRaceSnapshot,
        state: PressRaceSessionState,
    ): Boolean {
        val ready = state.ready ?: error("Snapshot before ready")
        if (snapshot.serverGeneration != ready.serverGeneration ||
            snapshot.connectionGeneration != ready.connectionGeneration
        ) {
            return false
        }
        if (snapshot.view != state.view) return false
        if (state.isAwaitingSnapshot && !snapshot.baseline) return false
        require(snapshot.activeCount >= 0L)
        if (version?.let { compareSnapshotVersions(snapshot.snapshotVersion, it) <= 0 } == true) return false
        compareSnapshotVersions(snapshot.snapshotVersion, "0")
        val previousDate =
            state.snapshot
                ?.takeIf {
                    it.serverGeneration == ready.serverGeneration &&
                        it.connectionGeneration == ready.connectionGeneration
                }?.date ?: ready.date
        if (snapshot.date < previousDate) return false
        version = snapshot.snapshotVersion
        return true
    }
}
