package com.pheeeew

import android.os.Bundle
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

private class MonitoringSmokeTestException : RuntimeException("Development monitoring smoke test")

private class MonitoringSmokeTestCrash : RuntimeException("Development monitoring crash test")
