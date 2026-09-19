package com.pheeeew

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Explicit adb-only diagnostic entry point; excluded from release source sets. */
class MonitoringSmokeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.MONITORING_ENVIRONMENT != "dev" || savedInstanceState != null) {
            finish()
            return
        }
        lifecycleScope.launch {
            // Let the application's foreground observer establish the visit context first.
            delay(1000)
            val monitoring = (application as PheeeewApplication).monitoring
            if (intent.getBooleanExtra("analytics_failures", false)) {
                monitoring.endAttempt(reason = "smoke_test_reset")
                monitoring.mapVisitEnded("smoke_test_reset")

                check(monitoring.beginAttempt(guideMode = false))
                monitoring.startFailed("smoke_test_start_failure")

                check(monitoring.beginAttempt(guideMode = false))
                monitoring.memoEditing()
                monitoring.memoShown()
                monitoring.memoValidationFailed()
                monitoring.endAttempt(reason = "smoke_test_finished")

                check(monitoring.beginAttempt(guideMode = false))
                monitoring.memoSkipped()
                monitoring.beginCapture()
                monitoring.microphoneFailed("smoke_test_microphone_failure")
                monitoring.endAttempt(reason = "smoke_test_finished")

                check(monitoring.beginAttempt(guideMode = false))
                monitoring.memoSkipped()
                monitoring.beginCapture()
                monitoring.microphoneReady()
                monitoring.gestureCancelled("smoke_test_pointer_cancel")
                monitoring.endAttempt(reason = "smoke_test_finished")

                monitoring.mapVisitStarted("smoke_test")
                monitoring.starSelected("map")
                monitoring.starDetailFailed(
                    reason = "smoke_test_detail_failure",
                    errorCode = "SMOKE_TEST",
                )
                monitoring.mapVisitEnded("smoke_test_finished")
                // PostHog capture is asynchronous; let all capture calls reach its queue before flushing.
                delay(2000)
                monitoring.flush()
                Log.i(LOG_TAG, "Analytics failure smoke events queued")
                delay(4000)
                finish()
                return@launch
            }
            if (intent.getBooleanExtra("crash", false)) {
                // An uncaught JVM exception tests the installed native Android crash handler.
                Thread { throw MonitoringSmokeTestCrash() }.start()
                return@launch
            }
            monitoring.report(MonitoringSmokeTestException())
            delay(2000)
            finish()
        }
    }
}

private const val LOG_TAG = "MonitoringSmokeTest"

private class MonitoringSmokeTestException : RuntimeException("Development monitoring smoke test")

private class MonitoringSmokeTestCrash : RuntimeException("Development monitoring crash test")
