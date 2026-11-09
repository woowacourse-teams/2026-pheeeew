package com.pheeeew.hapticcomparison

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HapticComparisonDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val runId = args.getString("runId") ?: "manual-run"
    private val phase = args.getString("phase") ?: "core"
    private lateinit var comparisonActivity: ComparisonActivity
    private val output by lazy { File(context.filesDir, "haptic-comparison/$runId").apply { mkdirs() } }

    @Test
    fun compareRealProjectHapticCallbacks() {
        HapticComparisonProbe.rows.clear()
        device.wakeUp()
        val activity =
            instrumentation.startActivitySync(
                Intent(context, ComparisonActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComparisonActivity
        comparisonActivity = activity
        try {
            bounds("tap-haptic")
            instrumentation.waitForIdleSync()
            val focusDeadline = SystemClock.uptimeMillis() + 5_000
            while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < focusDeadline) SystemClock.sleep(20)
            assertTrue("Comparison window must have focus", activity.hasWindowFocus())
            // Wait for the launch transition, without warming up the haptic executor.
            SystemClock.sleep(500)
            snapshot("entry")
            if (phase == "project-pad") {
                click("toggle-project-pad")
                val padButton = device.wait(Until.findObject(By.res("emotion-Blocked")), 5_000)
                assertNotNull("Actual project emotion button is missing", padButton)
                val rect = padButton.visibleBounds
                repeat(30) { index ->
                    HapticComparisonProbe.scenario = if (index < 10) "project-pad-ordinary" else "project-pad-burst"
                    if (index < 10) {
                        assertTrue("Another window took focus", comparisonActivity.hasWindowFocus())
                        assertTrue(device.click(rect.centerX(), rect.centerY()))
                        SystemClock.sleep(120)
                    } else {
                        sendTap(rect)
                        SystemClock.sleep(25)
                    }
                    if (index == 9 || index == 29) {
                        instrumentation.waitForIdleSync()
                        SystemClock.sleep(150)
                        snapshot("project-pad-$index")
                    }
                }
                assertEquals(30, HapticComparisonProbe.rows.size)
            } else if (phase == "cold") {
                sample("cold-first", 1, 0)
            } else if (phase == "settings-off") {
                sample("settings-off", 10, 100)
                assertTrue(HapticComparisonProbe.rows.all { it.getInt("systemHapticsEnabled") == 0 })
            } else {
                sample("first", 1, 0)
                sample("ordinary", 30, 120, captureEvery = 10)
                repeat(3) { sample("burst-$it", 20, 25) }
                repeat(10) { round ->
                    HapticComparisonProbe.scenario = "screen-exit-$round"
                    click("toggle-screen")
                    assertTrue(device.wait(Until.gone(By.desc("tap-haptic")), 5_000))
                    SystemClock.sleep(100)
                    snapshot("screen-exit-$round")
                    click("toggle-screen")
                    sample("screen-return", 1, 0)
                }
                repeat(10) { round ->
                    HapticComparisonProbe.scenario = "background-$round"
                    device.pressHome()
                    SystemClock.sleep(300)
                    snapshot("background-$round")
                    device.executeShellCommand(
                        "am start -n ${context.packageName}/com.pheeeew.hapticcomparison.ComparisonActivity -f 0x10200000",
                    )
                    bounds("tap-haptic")
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(500)
                    sample("foreground-return", 1, 0)
                }
                click("toggle-reject")
                sample("rejected", 10, 80)
                assertTrue(
                    HapticComparisonProbe.rows
                        .filter {
                            it.getString("scenario") == "rejected"
                        }.none { it.getBoolean("hapticCallbackInvoked") },
                )
                click("toggle-reject")
                click("toggle-visual-only")
                sample("visual-only", 10, 80)
                assertTrue(
                    HapticComparisonProbe.rows
                        .filter {
                            it.getString("scenario") == "visual-only"
                        }.none { it.getBoolean("hapticCallbackInvoked") },
                )
                click("toggle-visual-only")
                click("toggle-view-haptics")
                sample("view-disabled", 10, 80)
                assertTrue(
                    HapticComparisonProbe.rows
                        .filter {
                            it.getString("scenario") == "view-disabled"
                        }.none { it.getBoolean("viewEnabled") },
                )
                click("toggle-view-haptics")
                sample("view-restored", 1, 0)
                assertEquals(142, HapticComparisonProbe.rows.size)
            }
            device.takeScreenshot(File(output, "screen.png"))
        } finally {
            if (activity.hasWindowFocus()) device.takeScreenshot(File(output, "final-screen.png"))
            val result =
                JSONObject()
                    .put("runId", runId)
                    .put("phase", phase)
                    .put("package", context.packageName)
                    .put("model", Build.MODEL)
                    .put("androidRelease", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("rows", HapticComparisonProbe.snapshot())
            File(output, "result.json").writeText(result.toString(2))
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun bounds(description: String): Rect {
        val node = device.wait(Until.findObject(By.desc(description)), 5_000)
        assertNotNull("Missing $description", node)
        return node.visibleBounds
    }

    private fun click(description: String) {
        val rect = bounds(description)
        assertTrue("Another window took focus", comparisonActivity.hasWindowFocus())
        assertTrue("Click injection failed", device.click(rect.centerX(), rect.centerY()))
        instrumentation.waitForIdleSync()
    }

    private fun sendTap(rect: Rect) {
        assertTrue("Another window took focus", comparisonActivity.hasWindowFocus())
        val down = SystemClock.uptimeMillis()
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        val start = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        val end = MotionEvent.obtain(down, down + 16, MotionEvent.ACTION_UP, x, y, 0)
        try {
            instrumentation.sendPointerSync(start)
            SystemClock.sleep(16)
            instrumentation.sendPointerSync(end)
        } finally {
            start.recycle()
            end.recycle()
        }
    }

    private fun sample(
        name: String,
        count: Int,
        gapMillis: Long,
        captureEvery: Int = count,
    ) {
        HapticComparisonProbe.scenario = name
        val rect = bounds("tap-haptic")
        val before = HapticComparisonProbe.rows.size
        if (name == "view-disabled" || name == "settings-off") device.takeScreenshot(File(output, "$name-before.png"))
        repeat(count) { index ->
            if (name.startsWith("burst-")) {
                sendTap(rect)
            } else {
                assertTrue("Another window took focus", comparisonActivity.hasWindowFocus())
                assertTrue("Click injection failed", device.click(rect.centerX(), rect.centerY()))
            }
            if (gapMillis > 0) SystemClock.sleep(gapMillis)
            if ((index + 1) % captureEvery == 0) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(100)
                snapshot("$name-${before + index}")
            }
        }
        instrumentation.waitForIdleSync()
        assertEquals("Lost UI taps in $name", before + count, HapticComparisonProbe.rows.size)
    }

    private fun snapshot(name: String) {
        File(output, "$name-vibrator.txt").writeText(device.executeShellCommand("dumpsys vibrator_manager"))
    }
}
