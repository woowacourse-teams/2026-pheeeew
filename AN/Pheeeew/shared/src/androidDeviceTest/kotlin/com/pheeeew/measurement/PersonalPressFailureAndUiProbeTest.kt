package com.pheeeew.measurement

import android.content.Intent
import android.content.pm.ApplicationInfo
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

/** Explicit dev-only experiment. Restores device connectivity and removes one-shot fault control. */
@RunWith(AndroidJUnit4::class)
class PersonalPressFailureAndUiProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val names = listOf("답답", "짜증", "지침", "좌절", "분노")

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(descriptor)
                .bufferedReader()
                .use { it.readText().trim() }
        }

    private fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("personalPressFailureProbe") == "true")
        val app = instrumentation.context.packageManager.getApplicationInfo("com.pheeeew", 0)
        check(app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        instrumentation.context.startActivity(
            Intent().setClassName("com.pheeeew", "com.pheeeew.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        check(device.wait(Until.hasObject(By.text("프레스")), 15_000))
        device.findObjects(By.text("프레스")).last().click()
        check(device.wait(Until.hasObject(By.descStartsWith("답답 표현하기")), 15_000))
        SystemClock.sleep(6_000)
    }

    private fun total(): Long =
        names.sumOf { name ->
            val desc = assertNotNull(device.findObject(By.descStartsWith("$name 표현하기"))).contentDescription
            Regex("([0-9,]+)번")
                .find(desc)!!
                .groupValues[1]
                .replace(",", "")
                .toLong()
        }

    private fun inputs(
        count: Int,
        interval: Long,
        mixed: Boolean = false,
    ) {
        val centers = names.map { assertNotNull(device.findObject(By.descStartsWith("$it 표현하기"))).visibleCenter }
        val started = SystemClock.uptimeMillis()
        repeat(count) { index ->
            val wait = started + index * interval - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
            val point = centers[if (mixed) index % 5 else 0]
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
    }

    @Test
    fun failureAndUiMeasurements() {
        prepare()
        for (mode in listOf("400", "429", "500", "timeout", "lost_response")) {
            val before = total()
            Log.i("PressMeasurement", "failure_start mode=$mode baseline=$before")
            shell("run-as com.pheeeew touch files/press-probe-fault-$mode")
            try {
                inputs(5, 100)
                SystemClock.sleep(1_200)
                inputs(2, 100)
                SystemClock.sleep(15_000)
                val delta = total() - before
                val expected = if (mode == "lost_response") 7L else 2L
                check(delta == expected) { "$mode delta=$delta expected=$expected" }
                Log.i("PressMeasurement", "failure_end mode=$mode requested=7 delta=$delta")
            } finally {
                shell("run-as com.pheeeew rm -f files/press-probe-fault-$mode")
            }
        }
        val wifi = shell("settings get global wifi_on")
        val data = shell("settings get global mobile_data")
        val beforeOffline = total()
        Log.i("PressMeasurement", "failure_start mode=offline baseline=$beforeOffline")
        try {
            shell("svc wifi disable")
            shell("svc data disable")
            SystemClock.sleep(3_000)
            inputs(10, 100)
            SystemClock.sleep(1_000)
            check(total() - beforeOffline == 10L)
            Log.i("PressMeasurement", "offline_inputs_end requested=10")
        } finally {
            if (wifi == "1") shell("svc wifi enable")
            if (data == "1") shell("svc data enable")
            Log.i("PressMeasurement", "connectivity_restored wifi=$wifi data=$data")
        }
        SystemClock.sleep(15_000)
        check(total() - beforeOffline == 10L)
        Log.i("PressMeasurement", "failure_end mode=offline requested=10 delta=${total() - beforeOffline}")
        for ((pattern, count, interval) in listOf(
            Triple("isolated", 30, 900L),
            Triple("same", 100, 100L),
            Triple("mixed", 100, 100L),
        )) {
            val before = total()
            Log.i("PressMeasurement", "animation_start pattern=$pattern requested=$count baseline=$before")
            inputs(count, interval, pattern == "mixed")
            Log.i("PressMeasurement", "animation_inputs_end pattern=$pattern")
            SystemClock.sleep(3_000)
            check(total() - before == count.toLong())
            Log.i("PressMeasurement", "animation_end pattern=$pattern delta=${total() - before}")
        }
    }
}
