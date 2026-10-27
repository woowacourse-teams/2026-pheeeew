package com.pheeeew.data.press.race

/** Latest occurrence only. No UI strings, ranking inference or notification queue. */
internal data class PressRaceNoticeState(
    val id: Long,
    val entered: Boolean,
    val passed: Long,
    val passedBy: Long,
    val expiresAtMillis: Long,
)

/** Called synchronously by the session actor after SnapshotGate accepts a frame. */
internal class PressRaceNoticeReducer {
    private data class Context(
        val epoch: Long,
        val server: String,
        val connection: String,
        val date: String,
        val view: PressRaceView,
    )

    private var context: Context? = null
    private var version: String? = null
    private var baselineSeen = false
    private var participating = false
    private var sequence = 0L
    var notice: PressRaceNoticeState? = null
        private set

    fun accept(
        snapshot: PressRaceSnapshot,
        epoch: Long,
        nowMillis: Long,
    ): PressRaceNoticeState? {
        require(snapshot.overtakes.passed >= 0 && snapshot.overtakes.passedBy >= 0)
        val next =
            Context(epoch, snapshot.serverGeneration, snapshot.connectionGeneration, snapshot.date, snapshot.view)
        if (next != context) {
            reset()
            context = next
        }
        if (version?.let { compareSnapshotVersions(snapshot.snapshotVersion, it) <= 0 } == true) return notice
        version = snapshot.snapshotVersion
        if (snapshot.baseline) {
            notice = null
            baselineSeen = true
            participating = snapshot.me.participating
            return null
        }
        if (!baselineSeen || snapshot.view != PressRaceView.PRESS) return null
        val entered = !participating && snapshot.me.participating
        participating = snapshot.me.participating
        if (entered || snapshot.overtakes.passed > 0 || snapshot.overtakes.passedBy > 0) {
            notice =
                PressRaceNoticeState(
                    ++sequence,
                    entered,
                    snapshot.overtakes.passed,
                    snapshot.overtakes.passedBy,
                    nowMillis + DISPLAY_MILLIS,
                )
        }
        return notice
    }

    fun expire(
        id: Long,
        nowMillis: Long,
    ): Boolean {
        if (notice?.let { it.id == id && nowMillis >= it.expiresAtMillis } != true) return false
        notice = null
        return true
    }

    fun reset() {
        context = null
        version = null
        baselineSeen = false
        participating = false
        notice = null
    }

    companion object {
        const val DISPLAY_MILLIS = 2_000L
    }
}
