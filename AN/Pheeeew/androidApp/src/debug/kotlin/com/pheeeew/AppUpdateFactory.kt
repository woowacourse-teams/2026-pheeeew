package com.pheeeew

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pheeeew.core.update.AppUpdateController
import com.pheeeew.core.update.AppUpdateDriver
import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.core.update.DefaultAppUpdateController
import com.pheeeew.core.update.UpdateAvailability
import com.pheeeew.update.AndroidAppUpdateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun createAppUpdateController(activity: ComponentActivity): AppUpdateController {
    val prefs = activity.getSharedPreferences("update_test", Context.MODE_PRIVATE)
    val scenario = prefs.getString("play", "real")
    if (!BuildConfig.APPLICATION_ID.endsWith(".updatetest") ||
        scenario == "real"
    ) {
        return AndroidAppUpdateController(activity)
    }
    lateinit var controller: DefaultAppUpdateController
    val driver =
        object : AppUpdateDriver {
            override suspend fun check() = UpdateAvailability(scenario != "unavailable", true, true)

            override fun launch(required: Boolean): Boolean {
                activity.lifecycleScope.launch {
                    delay(300)
                    when (scenario) {
                        "cancel" -> {
                            controller.onResult(false, true)
                        }

                        "fail" -> {
                            controller.onResult(false, false)
                        }

                        else -> {
                            controller.onResult(true, false)
                            delay(1500)
                            controller.onInstallState(AppUpdateState.DOWNLOADED)
                        }
                    }
                }
                return true
            }

            override suspend fun install() {
                error("Fake flow cannot install an APK. Use Play or adb for the real A-to-B upgrade.")
            }
        }
    controller = DefaultAppUpdateController(driver, activity.lifecycleScope)
    return controller
}
