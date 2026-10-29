package com.pheeeew

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.update.AndroidAppUpdateController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Activity and production Android adapter, Google Fake manager. No Play installation claim. */
class AppUpdateAdapterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun flexibleDownloadsInstallsAndRecoversOnResume() {
        withAdapter { activity, manager, controller ->
            main {
                manager.setUpdateAvailable(103202, AppUpdateType.FLEXIBLE)
                controller.request(false)
            }
            await { manager.isConfirmationDialogVisible }
            main {
                manager.userAcceptsUpdate()
                manager.downloadStarts()
            }
            await { controller.state.value == AppUpdateState.DOWNLOADING }
            main { manager.downloadCompletes() }
            await { controller.state.value == AppUpdateState.DOWNLOADED }
            main { controller.resume() }
            await { controller.state.value == AppUpdateState.DOWNLOADED }
            main { controller.complete() }
            await { manager.isInstallSplashScreenVisible }
            main { manager.installCompletes() }
            await { controller.state.value == AppUpdateState.IDLE }
            assertTrue(!activity.isFinishing)
        }
    }

    @Test
    fun immediateFlowAndUnavailableFallbackUseAllowedType() {
        withAdapter { _, manager, controller ->
            main {
                manager.setUpdateAvailable(103202, AppUpdateType.IMMEDIATE)
                controller.request(false)
            }
            await { controller.state.value == AppUpdateState.UNAVAILABLE }
            main { controller.request(true) }
            await { manager.isImmediateFlowVisible }
            assertEquals(AppUpdateState.LAUNCHING, controller.state.value)
            main {
                manager.userAcceptsUpdate()
                manager.downloadStarts()
                manager.downloadFails()
            }
            // Immediate's installation status is owned by Play; common controller cancellation/failure is unit tested.
            main { controller.resume() }
        }
    }

    private fun withAdapter(
        block: (AppUpdateAdapterTestActivity, FakeAppUpdateManager, AndroidAppUpdateController) -> Unit,
    ) {
        val manager = FakeAppUpdateManager(instrumentation.targetContext)
        lateinit var controller: AndroidAppUpdateController
        AppUpdateAdapterTestActivity.configure = { controller = AndroidAppUpdateController(it, manager) }
        val intent =
            Intent(
                instrumentation.targetContext,
                AppUpdateAdapterTestActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as AppUpdateAdapterTestActivity
        try {
            instrumentation.waitForIdleSync()
            block(activity, manager, controller)
        } finally {
            main { activity.finish() }
            AppUpdateAdapterTestActivity.configure = null
        }
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            SystemClock.sleep(30)
        }
        error("Play adapter did not reach expected state")
    }
}
