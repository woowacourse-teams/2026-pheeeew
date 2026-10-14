package com.pheeeew.groupdetailbenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@OptIn(ExperimentalMetricApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class GroupDetailMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun aStatisticUpdatesAt250MillisMeasureProductionDetailFramesAndMemory() {
        measureStatisticUpdatesAtCadence(INTER_TAP_INTERVAL_250_MILLIS)
    }

    @Test
    fun bStatisticUpdatesAt150MillisMeasureProductionDetailFramesAndMemory() {
        measureStatisticUpdatesAtCadence(INTER_TAP_INTERVAL_150_MILLIS)
    }

    private fun measureStatisticUpdatesAtCadence(interTapIntervalMillis: Long) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics =
                listOf(
                    FrameTimingMetric(),
                    MemoryUsageMetric(
                        mode = MemoryUsageMetric.Mode.Max,
                        subMetrics =
                            listOf(
                                MemoryUsageMetric.SubMetric.RssAnon,
                                MemoryUsageMetric.SubMetric.RssFile,
                                MemoryUsageMetric.SubMetric.RssShmem,
                                MemoryUsageMetric.SubMetric.Gpu,
                            ),
                    ),
                ),
            compilationMode = CompilationMode.Partial(),
            iterations = ITERATIONS,
            setupBlock = {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                // Macrobenchmark devices may have idled between runs; taps need an awake display.
                device.wakeUp()
                pressHome()
                launchBenchmarkActivity()
                val warmupButton = device.wait(Until.findObject(RETRY_BUTTON), WAIT_FOR_SCREEN_MILLIS)
                check(warmupButton != null) { "Group detail retry button did not appear for warmup" }
                device.waitForIdle()

                val warmupBounds = warmupButton.visibleBounds
                check(!warmupBounds.isEmpty) { "Warmup retry button has no visible bounds" }
                tapAtCadence(
                    device = device,
                    tapX = warmupBounds.centerX(),
                    tapY = warmupBounds.centerY(),
                    tapCount = WARMUP_TAPS,
                    intervalMillis = interTapIntervalMillis,
                )
                awaitPressCount(device, INITIAL_PRESS_COUNT + WARMUP_TAPS, "warmup")

                // Warm Compose and the local update path before each measured iteration, then
                // recreate the fixture Activity so every measurement begins at the same count.
                launchBenchmarkActivity()
                val resetButton = device.wait(Until.findObject(RETRY_BUTTON), WAIT_FOR_SCREEN_MILLIS)
                check(resetButton != null) { "Group detail retry button did not reappear after warmup" }
                awaitPressCount(device, INITIAL_PRESS_COUNT, "reset")
                device.waitForIdle()
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val button = device.findObject(RETRY_BUTTON)
            check(button != null) { "Group detail retry button is missing during measurement" }
            // Avoid a repeated accessibility-tree lookup before every measured tap.
            val bounds = button.visibleBounds
            check(!bounds.isEmpty) { "Group detail retry button has no visible bounds" }
            tapAtCadence(
                device = device,
                tapX = bounds.centerX(),
                tapY = bounds.centerY(),
                tapCount = TAPS_PER_ITERATION,
                intervalMillis = interTapIntervalMillis,
            )
            device.waitForIdle()
            awaitPressCount(device, INITIAL_PRESS_COUNT + TAPS_PER_ITERATION, "measurement")
        }
    }

    private fun MacrobenchmarkScope.launchBenchmarkActivity() {
        val intent = Intent()
        intent.setClassName(
            TARGET_PACKAGE,
            "com.pheeeew.groupdetailprofile.GroupDetailBenchmarkActivity",
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivityAndWait(intent)
    }

    private fun tapAtCadence(
        device: UiDevice,
        tapX: Int,
        tapY: Int,
        tapCount: Int,
        intervalMillis: Long,
    ) {
        val startTimeMillis = SystemClock.uptimeMillis()
        repeat(tapCount) { index ->
            check(device.click(tapX, tapY)) { "Retry button tap was not injected" }
            val nextTapTimeMillis = startTimeMillis + ((index + 1) * intervalMillis)
            val delayMillis = nextTapTimeMillis - SystemClock.uptimeMillis()
            if (delayMillis > 0) SystemClock.sleep(delayMillis)
        }
    }

    private fun awaitPressCount(
        device: UiDevice,
        expectedCount: Int,
        phase: String,
    ) {
        val expectedButton =
            device.wait(
                Until.findObject(By.text("(${expectedCount}번)")),
                WAIT_FOR_SCREEN_MILLIS,
            )
        check(expectedButton != null) {
            val currentCount = device.findObject(RETRY_BUTTON)?.contentDescription
            "Local fixture did not reflect expected press count during $phase; current button=$currentCount"
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.pheeeew.groupdetailprofile.benchmark"
        const val ITERATIONS = 5
        const val TAPS_PER_ITERATION = 40
        const val WARMUP_TAPS = 10
        const val INTER_TAP_INTERVAL_150_MILLIS = 150L
        const val INTER_TAP_INTERVAL_250_MILLIS = 250L
        const val INITIAL_PRESS_COUNT = 98
        const val WAIT_FOR_SCREEN_MILLIS = 10_000L
        val RETRY_BUTTON = By.text("다시 불러오기")
    }
}
