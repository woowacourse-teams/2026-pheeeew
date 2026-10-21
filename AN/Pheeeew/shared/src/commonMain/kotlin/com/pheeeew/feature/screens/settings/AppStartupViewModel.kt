package com.pheeeew.feature.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.di.AppVersion
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.domain.model.version.evaluateAppVersion
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class AppStartupState(
    val checked: Boolean = false,
    val decision: AppVersionDecision? = null,
    val minimumSplashElapsed: Boolean = false,
    val splashDismissed: Boolean = false,
    val splashExitReason: String? = null,
)

/** A failed public version lookup allows entry; cancellation never pretends to finish a check. */
@Inject
class AppStartupViewModel(
    private val repository: AppVersionRepository,
    @AppVersion private val version: String,
    monitoring: Monitoring = NoOpMonitoring,
) : ViewModel() {
    private val mutable = MutableStateFlow(AppStartupState())
    val state = mutable.asStateFlow()
    private var check: Job? = null
    private var splashStartedAt: TimeMark? = null
    private var onboardingCompleted = false
    private var entryResolved = false
    private var entryReady = false
    private var mapResolved = false
    private val telemetry = ProductMonitoring(monitoring, "splash")
    private val completedSteps = mutableSetOf<String>()

    /** A retained owner starts one deadline; configuration changes and retries cannot extend it. */
    fun startSplash(timeSource: TimeSource = TimeSource.Monotonic) {
        if (splashStartedAt != null) return
        splashStartedAt = timeSource.markNow()
        viewModelScope.launch {
            delay((MINIMUM_SPLASH_MILLIS - splashElapsedMillis()).coerceAtLeast(0))
            mutable.update { it.copy(minimumSplashElapsed = true) }
            finishSplashIfReady()
            delay((MAXIMUM_SPLASH_MILLIS - splashElapsedMillis()).coerceAtLeast(0))
            dismissSplash("deadline")
        }
    }

    fun splashElapsedMillis(): Long = splashStartedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L

    /** The coroutine timer may be suspended in background; enforce the same deadline on resume. */
    fun refreshSplashDeadline() {
        val elapsed = splashElapsedMillis()
        if (elapsed >= MINIMUM_SPLASH_MILLIS) {
            mutable.update { it.copy(minimumSplashElapsed = true) }
        }
        if (elapsed >= MAXIMUM_SPLASH_MILLIS) dismissSplash("deadline") else finishSplashIfReady()
    }

    fun updateEntryPreparation(
        onboardingCompleted: Boolean,
        entryPhase: AppEntryPhase,
        entryReady: Boolean,
    ) {
        this.onboardingCompleted = onboardingCompleted
        this.entryResolved = entryPhase != AppEntryPhase.LOADING && entryPhase != AppEntryPhase.READY
        this.entryReady = entryReady
        if (entryResolved || entryReady) recordStep("entry")
        finishSplashIfReady()
    }

    fun updateMapPreparation(resolved: Boolean) {
        mapResolved = resolved
        if (resolved) recordStep("map")
        finishSplashIfReady()
    }

    private fun finishSplashIfReady() {
        if (splashElapsedMillis() >= MAXIMUM_SPLASH_MILLIS) {
            dismissSplash("deadline")
            return
        }
        val state = mutable.value
        if (!state.minimumSplashElapsed || !state.checked) return
        val reason =
            when {
                state.decision is AppVersionDecision.UpdateRequired -> "required_update"
                !onboardingCompleted -> "onboarding"
                entryResolved && !entryReady -> "entry_action"
                entryReady && mapResolved -> "ready"
                else -> return
            }
        dismissSplash(reason)
    }

    private fun recordStep(step: String) {
        if (splashStartedAt == null || !completedSteps.add(step)) return
        telemetry.emit(
            "app_startup_step_finished",
            mapOf(
                "outcome" to EventValue.Text("resolved"),
                "step" to EventValue.Text(step),
                "duration_ms" to EventValue.Integer(splashElapsedMillis()),
            ),
        )
    }

    private fun dismissSplash(reason: String) {
        if (mutable.value.splashDismissed) return
        mutable.update { it.copy(splashDismissed = true, splashExitReason = reason) }
        telemetry.emit(
            "app_startup_finished",
            mapOf(
                "outcome" to EventValue.Text(reason),
                "duration_ms" to EventValue.Integer(splashElapsedMillis()),
            ),
        )
    }

    fun checkVersion() {
        if (check?.isActive == true) return
        check =
            viewModelScope.launch {
                try {
                    val policy = withTimeout(10_000L) { repository.getPolicy() }
                    mutable.update { it.copy(checked = true, decision = evaluateAppVersion(version, policy)) }
                } catch (_: TimeoutCancellationException) {
                    mutable.update { it.copy(checked = true) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    mutable.update { it.copy(checked = true) }
                }
                recordStep("version")
                finishSplashIfReady()
            }
    }

    companion object {
        const val MINIMUM_SPLASH_MILLIS = 2_000L
        const val MAXIMUM_SPLASH_MILLIS = 4_000L
    }
}
