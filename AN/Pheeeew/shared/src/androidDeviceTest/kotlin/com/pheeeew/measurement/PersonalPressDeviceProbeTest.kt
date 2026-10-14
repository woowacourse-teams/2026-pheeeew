package com.pheeeew.measurement

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

/** Run only against the dev debug app on a dedicated device. Makes 603 real press inputs. */
@RunWith(AndroidJUnit4::class)
class PersonalPressDeviceProbeTest {
    @Test
    fun measurePersonalPress() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("personalPressProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.context.packageManager.getApplicationInfo("com.pheeeew", 0)
        check(app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) { "Requires debug app" }
        val device = UiDevice.getInstance(instrumentation)
        val intent =
            Intent()
                .setClassName("com.pheeeew", "com.pheeeew.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.context.startActivity(intent)
        check(device.wait(Until.hasObject(By.text("프레스")), 15_000))
        device.findObjects(By.text("프레스")).last().click()
        check(device.wait(Until.hasObject(By.descStartsWith("답답 표현하기")), 15_000))
        SystemClock.sleep(6_000)
        val centers =
            listOf("답답", "짜증", "지침", "좌절", "분노").map { name ->
                val button = assertNotNull(device.findObject(By.descStartsWith("$name 표현하기")))
                button.visibleCenter
            }
        for (pattern in listOf("single", "same", "mixed")) {
            repeat(3) { repetition ->
                val count = if (pattern == "single") 1 else 100

                fun displayedTotal(): Long =
                    listOf("답답", "짜증", "지침", "좌절", "분노").sumOf { name ->
                        val desc = assertNotNull(device.findObject(By.descStartsWith("$name 표현하기"))).contentDescription
                        Regex("([0-9,]+)번")
                            .find(desc)!!
                            .groupValues[1]
                            .replace(",", "")
                            .toLong()
                    }
                val before = displayedTotal()
                Log.i("PressMeasurement", "run_start pattern=$pattern repetition=${repetition + 1} requested=$count")
                val start = SystemClock.uptimeMillis()
                repeat(count) { index ->
                    val wait = start + index * 100L - SystemClock.uptimeMillis()
                    if (wait > 0) SystemClock.sleep(wait)
                    val point = centers[if (pattern == "mixed") index % 5 else 0]
                    val down = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event =
                            MotionEvent.obtain(
                                down,
                                SystemClock.uptimeMillis(),
                                action,
                                point.x.toFloat(),
                                point.y.toFloat(),
                                0,
                            )
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try {
                            check(instrumentation.uiAutomation.injectInputEvent(event, true))
                            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(10)
                        } finally {
                            event.recycle()
                        }
                    }
                }
                val wait = start + count * 100L - SystemClock.uptimeMillis()
                if (wait > 0) SystemClock.sleep(wait)
                Log.i(
                    "PressMeasurement",
                    "input_end pattern=$pattern repetition=${repetition + 1} elapsed=${SystemClock.uptimeMillis() - start}",
                )
                // Fixed observation interval, with remaining/unknown state retained in logcat.
                SystemClock.sleep(15_000)
                check(
                    displayedTotal() - before == count.toLong(),
                ) { "Actual accepted input count differs from requested count" }
                Log.i("PressMeasurement", "run_end pattern=$pattern repetition=${repetition + 1}")
            }
        }
    }
}
