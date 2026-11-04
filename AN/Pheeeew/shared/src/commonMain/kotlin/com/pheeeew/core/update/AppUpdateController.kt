package com.pheeeew.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppUpdateState {
    IDLE,
    CHECKING,
    LAUNCHING,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    CANCELED,
    FAILED,
    UNAVAILABLE,
}

interface AppUpdateController {
    val state: StateFlow<AppUpdateState>

    fun request(required: Boolean)

    fun resume()

    fun complete()
}

data class UpdateAvailability(
    val available: Boolean,
    val flexibleAllowed: Boolean,
    val immediateAllowed: Boolean,
    val immediateInProgress: Boolean = false,
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val installing: Boolean = false,
)

/** The platform owns Activity/launcher/listeners. No platform object is retained by the policy model. */
interface AppUpdateDriver {
    suspend fun check(): UpdateAvailability

    fun launch(required: Boolean): Boolean

    suspend fun install()
}

class DefaultAppUpdateController(
    private val driver: AppUpdateDriver,
    private val scope: CoroutineScope,
) : AppUpdateController {
    private val mutable = MutableStateFlow(AppUpdateState.IDLE)
    override val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var recovery: Job? = null

    override fun request(required: Boolean) {
        if (operation?.isActive == true || mutable.value in BUSY) return
        recovery?.cancel()
        operation =
            scope.launch {
                guarded {
                    mutable.value = AppUpdateState.CHECKING
                    val info = kotlinx.coroutines.withTimeout(10_000) { driver.check() }
                    when {
                        info.installing -> {
                            mutable.value = AppUpdateState.INSTALLING
                        }

                        info.downloaded -> {
                            mutable.value = AppUpdateState.DOWNLOADED
                        }

                        info.immediateInProgress -> {
                            launch(true)
                        }

                        info.downloading -> {
                            mutable.value = AppUpdateState.DOWNLOADING
                        }

                        !info.available || !(if (required) info.immediateAllowed else info.flexibleAllowed) -> {
                            mutable.value = AppUpdateState.UNAVAILABLE
                        }

                        else -> {
                            launch(required)
                        }
                    }
                }
            }
    }

    override fun resume() {
        if (operation?.isActive == true || recovery?.isActive == true) return
        recovery =
            scope.launch {
                // A failed background check must not erase a downloaded state or cause repeated prompts.
                try {
                    val info = kotlinx.coroutines.withTimeout(10_000) { driver.check() }
                    when {
                        info.installing -> mutable.value = AppUpdateState.INSTALLING
                        info.downloaded -> mutable.value = AppUpdateState.DOWNLOADED
                        info.immediateInProgress && mutable.value != AppUpdateState.LAUNCHING -> launch(true)
                        info.downloading -> mutable.value = AppUpdateState.DOWNLOADING
                    }
                } catch (_: TimeoutCancellationException) {
                    // A foreground probe timing out must not erase the current installation state.
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Explicit request provides the retry/fallback UI.
                }
            }
    }

    override fun complete() {
        if (mutable.value != AppUpdateState.DOWNLOADED || operation?.isActive == true) return
        recovery?.cancel()
        operation =
            scope.launch {
                guarded {
                    mutable.value = AppUpdateState.INSTALLING
                    kotlinx.coroutines.withTimeout(10_000) { driver.install() }
                }
            }
    }

    fun onResult(
        accepted: Boolean,
        canceled: Boolean,
    ) {
        if (mutable.value in setOf(AppUpdateState.DOWNLOADED, AppUpdateState.INSTALLING)) return
        mutable.value =
            when {
                canceled -> AppUpdateState.CANCELED
                accepted -> AppUpdateState.DOWNLOADING
                else -> AppUpdateState.FAILED
            }
    }

    fun onInstallState(state: AppUpdateState) {
        mutable.value = state
    }

    private fun launch(required: Boolean) {
        mutable.value = AppUpdateState.LAUNCHING
        if (!driver.launch(required)) mutable.value = AppUpdateState.FAILED
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: TimeoutCancellationException) {
            mutable.value = AppUpdateState.FAILED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            mutable.value = AppUpdateState.FAILED
        }
    }

    companion object {
        private val BUSY =
            setOf(
                AppUpdateState.CHECKING,
                AppUpdateState.LAUNCHING,
                AppUpdateState.DOWNLOADING,
                AppUpdateState.DOWNLOADED,
                AppUpdateState.INSTALLING,
            )
    }
}
