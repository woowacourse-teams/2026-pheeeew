package com.pheeeew.groupdetailbenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class GroupDetailMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun repeatedEmotionTapsMeasureProductionDetailFramesAndMemory() {
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
                pressHome()
                val intent = Intent()
                intent.setClassName(
                    TARGET_PACKAGE,
                    "com.pheeeew.groupdetailprofile.GroupDetailBenchmarkActivity",
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivityAndWait(intent)
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val button = device.wait(Until.findObject(ANGRY_BUTTON), WAIT_FOR_SCREEN_MILLIS)
                check(button != null) { "Group detail emotion button did not appear" }
                device.waitForIdle()
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val button = device.findObject(ANGRY_BUTTON)
            check(button != null) { "Group detail emotion button is missing during measurement" }
            repeat(TAPS_PER_ITERATION) {
                val currentButton = device.findObject(ANGRY_BUTTON)
                check(currentButton != null) { "Emotion button disappeared during the measured taps" }
                currentButton.click()
            }
            device.waitForIdle()
            val expectedCount = INITIAL_ANGRY_COUNT + TAPS_PER_ITERATION
            check(device.wait(Until.findObject(By.descContains("${expectedCount}번")), WAIT_FOR_SCREEN_MILLIS) != null) {
                val currentCount = device.findObject(ANGRY_BUTTON)?.contentDescription
                "Local fake detail state did not reflect all measured taps; current button=$currentCount"
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.pheeeew.groupdetailprofile.benchmark"
        const val ITERATIONS = 5
        const val TAPS_PER_ITERATION = 40
        const val INITIAL_ANGRY_COUNT = 98
        const val WAIT_FOR_SCREEN_MILLIS = 10_000L
        val ANGRY_BUTTON = By.descContains("분노 표현하기")
    }
}
