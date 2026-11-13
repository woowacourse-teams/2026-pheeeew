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
        require(
            snapshot.date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) &&
                com.pheeeew.domain.model.emotion.SearchDate
                    .parse(snapshot.date) != null,
        )
        if (snapshot.date < previousDate || state.dateFloor?.let { snapshot.date < it } == true) return false
        if (snapshot.date > previousDate && !snapshot.baseline) return false
        if (snapshot.baseline) require(snapshot.overtakes.passed == 0L && snapshot.overtakes.passedBy == 0L)
        version = snapshot.snapshotVersion
        return true
    }
}
