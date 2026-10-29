package com.pheeeew.developertools.data.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class NetworkRecoveryRun(
    val networkRevision: Long,
    val restoreAfterMillis: Long?,
)

/** Changes only external network conditions. The product observes connectivity and owns recovery. */
internal class NetworkRecoveryController(
    private val scope: CoroutineScope,
) {
    private val mutableRun = MutableStateFlow<NetworkRecoveryRun?>(null)
    val run = mutableRun.asStateFlow()
    private var waiting: Job? = null

    fun start(restoreAfterMillis: Long?): Boolean {
        require(restoreAfterMillis == null || restoreAfterMillis in 1..300_000L)
        if (run.value != null ||
            ErrorSimulation.state.value.modes
                .isNotEmpty()
        ) {
            return false
        }
        val active = NetworkRecoveryRun(ErrorSimulation.setRecoveryNetwork(offline = true), restoreAfterMillis)
        mutableRun.value = active
        waiting =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    if (restoreAfterMillis == null) {
                        ErrorSimulation.state.first { it.networkRevision != active.networkRevision }
                    } else {
                        val changed =
                            withTimeoutOrNull(restoreAfterMillis) {
                                ErrorSimulation.state.first { it.networkRevision != active.networkRevision }
                            }
                        if (changed == null) restore()
                    }
                } finally {
                    if (mutableRun.value === active) mutableRun.value = null
                }
            }
        return true
    }

    fun restore() {
        val active = run.value ?: return
        stop()
        if (ErrorSimulation.state.value.networkRevision == active.networkRevision) {
            ErrorSimulation.setRecoveryNetwork(offline = false)
        }
    }

    /** Cancels the scheduled change; the caller applies its replacement conditions. */
    fun stop() {
        mutableRun.value = null
        waiting?.cancel()
        waiting = null
    }
}

/** Process memory ownership keeps a running scenario across UI/Activity recreation. */
internal object NetworkRecovery {
    val controller = NetworkRecoveryController(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
}
