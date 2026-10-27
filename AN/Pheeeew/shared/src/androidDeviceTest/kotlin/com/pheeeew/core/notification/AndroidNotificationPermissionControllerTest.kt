package com.pheeeew.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pheeeew.core.permission.AppSettingsLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Run on an emulator with POST_NOTIFICATIONS revoked and its user-set/user-fixed flags cleared. */
class AndroidNotificationPermissionControllerTest {
    /** Run separately after granting POST_NOTIFICATIONS to the isolated test package. */
    @Test
    fun blockedRemoteChannelDoesNotChangeAppAuthorization() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val granted =
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        assumeTrue(
            "Requires the granted notification permission test profile",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "pheeeew.remote.v1"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Test remote channel", NotificationManager.IMPORTANCE_NONE),
        )
        val activity =
            instrumentation.startActivitySync(
                Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        lateinit var controller: AndroidNotificationPermissionController
        instrumentation.runOnMainSync {
            controller = AndroidNotificationPermissionController(activity, UnavailableSettingsFallback)
        }
        try {
            assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(channelId).importance)
            assertEquals(NotificationAuthorization.AUTHORIZED, runBlocking { controller.authorization() })
        } finally {
            manager.deleteNotificationChannel(channelId)
            instrumentation.runOnMainSync {
                controller.close()
                activity.finish()
            }
        }
    }

    @Test
    fun dismissalRemainsRequestableButRepeatedDenialOpensSettings() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val denySelector = By.res(Pattern.compile(".*:id/permission_deny.*_button"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        lateinit var controller: AndroidNotificationPermissionController
        instrumentation.runOnMainSync {
            controller = AndroidNotificationPermissionController(activity, UnavailableSettingsFallback)
        }
        try {
            assumeTrue(
                "Requires revoked notification permission with denial history and flags cleared",
                runBlocking { controller.canRequestAuthorization() },
            )
            device.wakeUp()
            val dismissed = scope.async { controller.requestAuthorization() }
            assertNotNull(device.wait(Until.findObject(denySelector), 5_000))
            device.pressBack()
            runBlocking { withTimeout(10_000) { dismissed.await() } }
            assertTrue(runBlocking { controller.canRequestAuthorization() })

            repeat(2) { attempt ->
                val denied = scope.async { controller.requestAuthorization() }
                val deny =
                    device.wait(Until.findObject(denySelector), 5_000)
                val visibleResources = device.findObjects(By.clickable(true)).map { it.resourceName }
                assertNotNull(deny, "attempt=$attempt: $visibleResources").click()
                runBlocking { withTimeout(10_000) { denied.await() } }
                if (attempt == 0) assertTrue(runBlocking { controller.canRequestAuthorization() })
            }
            assertFalse(runBlocking { controller.canRequestAuthorization() })
            assertTrue(runBlocking { controller.openSettings() })
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 5_000))
            device.pressBack()
        } finally {
            scope.cancel()
            instrumentation.runOnMainSync {
                controller.close()
                activity.finish()
            }
        }
    }

    private object UnavailableSettingsFallback : AppSettingsLauncher {
        override suspend fun openAppSettings(): Boolean = false

        override suspend fun openLocationSettings(): Boolean = false
    }
}
