package com.pheeeew.feature.screens.group.detail

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import org.junit.runner.RunWith
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Device-only exploratory profile. The tap callback updates local state and never calls the API. */
@RunWith(AndroidJUnit4::class)
class GroupDetailTapFrameProfileTest {
    @Test
    fun rapidLocalTapsCaptureDetailRecompositionsFrameTimingAndPssDelta() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var frameListener: Window.OnFrameMetricsAvailableListener
        var frameListenerRegistered = false
        val frameSamples = Collections.synchronizedList(mutableListOf<FrameSample>())
        val rootRecompositions = AtomicInteger()
        val acceptedTapTimestamps = ArrayDeque<Long>()
        val feedbackLatencies = mutableListOf<Long>()
        var detail by mutableStateOf(
            fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart),
        )
        var acceptedTapCount by mutableIntStateOf(0)
        scenario.onActivity { currentActivity ->
            frameListener = frameListener(currentActivity, frameSamples)
            currentActivity.window.addOnFrameMetricsAvailableListener(
                frameListener,
                Handler(Looper.getMainLooper()),
            )
            frameListenerRegistered = true
            currentActivity.setContent {
                SideEffect {
                    rootRecompositions.incrementAndGet()
                    val renderedAt = SystemClock.elapsedRealtimeNanos()
                    while (acceptedTapTimestamps.isNotEmpty()) {
                        feedbackLatencies += renderedAt - acceptedTapTimestamps.removeFirst()
                    }
                }
                GroupDetailReadyContent(
                    detail = detail,
                    hasRefreshError = false,
                    canTapEmotion = true,
                    pressStatus = GroupPressStatus.Idle,
                    onInviteClick = {},
                    onRetry = {},
                    onEmotionTap = { emotion ->
                        acceptedTapTimestamps.addLast(SystemClock.elapsedRealtimeNanos())
                        acceptedTapCount++
                        detail =
                            detail.copy(
                                emotionCounts =
                                    detail.emotionCounts.map { count ->
                                        if (count.kind == emotion) count.copy(count = count.count + 1L) else count
                                    },
                                todayTotal = detail.todayTotal + 1L,
                            )
                        true
                    },
                    onResolvePressOutcome = {},
                )
            }
        }
        instrumentation.waitForIdleSync()
        val uiAutomation = instrumentation.uiAutomation
        awaitEmotionButton()
        val root = requireNotNull(uiAutomation.rootInActiveWindow) { "Active accessibility root is missing" }
        val button = requireNotNull(findEmotionButton(root)) { "Emotion button is not accessible" }
        val buttonBounds = Rect()
        button.getBoundsInScreen(buttonBounds)
        check(!buttonBounds.isEmpty) { "Emotion button has no screen bounds: $button" }
        button.recycle()
        root.recycle()
        repeat(WARMUP_TAP_COUNT) {
            injectTap(uiAutomation, buttonBounds)
            SystemClock.sleep(INPUT_INTERVAL_MILLIS)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(FINAL_FRAME_DRAIN_MILLIS)
        frameSamples.clear()
        feedbackLatencies.clear()
        rootRecompositions.set(0)
        val pssBeforeKb = activityPssKb()
        val startNs = SystemClock.elapsedRealtimeNanos()

        try {
            repeat(TAP_COUNT) {
                injectTap(uiAutomation, buttonBounds)
                SystemClock.sleep(INPUT_INTERVAL_MILLIS)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(FINAL_FRAME_DRAIN_MILLIS)
            val elapsedNs = SystemClock.elapsedRealtimeNanos() - startNs
            scenario.onActivity { currentActivity ->
                currentActivity.window.removeOnFrameMetricsAvailableListener(frameListener)
            }
            frameListenerRegistered = false
            val pssAfterKb = activityPssKb()
            SystemClock.sleep(FINAL_MEMORY_SETTLE_MILLIS)
            val pssSettledKb = activityPssKb()

            val sortedFrames = frameSamples.sortedBy { it.totalNs }
            assertEquals(WARMUP_TAP_COUNT + TAP_COUNT, acceptedTapCount)
            assertEquals(TAP_COUNT, feedbackLatencies.size)
            assertEquals(TAP_COUNT, rootRecompositions.get())
            assertTrue(sortedFrames.isNotEmpty(), "FrameMetrics listener captured no frames")

            val p50Ms = sortedFrames[sortedFrames.size / 2].totalNs / NANOS_PER_MILLI
            val p95Ms = sortedFrames[(sortedFrames.size * 95 / 100) - 1].totalNs / NANOS_PER_MILLI
            val sortedFeedback = feedbackLatencies.sorted()
            val feedbackP50Ms = sortedFeedback[sortedFeedback.size / 2] / NANOS_PER_MILLI
            val feedbackP95Ms = sortedFeedback[(sortedFeedback.size * 95 / 100) - 1] / NANOS_PER_MILLI
            val deadlineMisses = frameSamples.count { it.totalNs > it.deadlineNs }
            println(
                "GROUP_DETAIL_DEVICE_PROFILE warmup_taps=$WARMUP_TAP_COUNT " +
                    "taps=$TAP_COUNT frame_count=${frameSamples.size} " +
                    "detail_root_recompositions=${rootRecompositions.get()} frame_p50_ms=$p50Ms frame_p95_ms=$p95Ms " +
                    "deadline_misses=$deadlineMisses ui_commit_p50_ms=$feedbackP50Ms " +
                    "ui_commit_p95_ms=$feedbackP95Ms " +
                    "elapsed_ms=${elapsedNs / NANOS_PER_MILLI} " +
                    "pss_before_kb=$pssBeforeKb pss_after_kb=$pssAfterKb pss_delta_kb=${pssAfterKb - pssBeforeKb} " +
                    "pss_settled_kb=$pssSettledKb pss_settled_delta_kb=${pssSettledKb - pssBeforeKb}",
            )
        } finally {
            if (frameListenerRegistered) {
                scenario.onActivity { currentActivity ->
                    currentActivity.window.removeOnFrameMetricsAvailableListener(frameListener)
                }
            }
            scenario.close()
        }
    }

    private fun activityPssKb(): Int {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss
    }

    private fun frameListener(
        activity: ComponentActivity,
        samples: MutableList<FrameSample>,
    ): Window.OnFrameMetricsAvailableListener {
        val displayManager = activity.getSystemService(DisplayManager::class.java)
        val display = requireNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY))
        val nominalDeadlineNs = (1_000_000_000.0 / display.refreshRate).toLong()
        return Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val deadline =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    metrics.getMetric(FrameMetrics.DEADLINE).takeIf { it > 0L } ?: nominalDeadlineNs
                } else {
                    nominalDeadlineNs
                }
            if (total > 0L) samples += FrameSample(total, deadline)
        }
    }

    private fun awaitEmotionButton() {
        val deadline = SystemClock.elapsedRealtime() + ACCESSIBILITY_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val currentRoot = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            if (currentRoot != null) {
                val found = findEmotionButton(currentRoot)
                found?.recycle()
                currentRoot.recycle()
                if (found != null) return
            }
            SystemClock.sleep(ACCESSIBILITY_POLL_INTERVAL_MILLIS)
        }
        error("Emotion button did not appear in the accessibility tree")
    }

    private fun findEmotionButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val description = "분노 표현하기"
        if (root.contentDescription?.toString()?.contains(description) == true) {
            return root
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val found = findEmotionButton(child)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun injectTap(
        uiAutomation: android.app.UiAutomation,
        bounds: Rect,
    ) {
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 1L, MotionEvent.ACTION_UP, x, y, 0)
        try {
            check(uiAutomation.injectInputEvent(down, false)) { "Touch down injection failed" }
            check(uiAutomation.injectInputEvent(up, false)) { "Touch up injection failed" }
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private data class FrameSample(
        val totalNs: Long,
        val deadlineNs: Long,
    )

    private companion object {
        const val TAP_COUNT = 100
        const val WARMUP_TAP_COUNT = 20
        const val INPUT_INTERVAL_MILLIS = 5L
        const val FINAL_FRAME_DRAIN_MILLIS = 500L
        const val FINAL_MEMORY_SETTLE_MILLIS = 2_000L
        const val ACCESSIBILITY_TIMEOUT_MILLIS = 5_000L
        const val ACCESSIBILITY_POLL_INTERVAL_MILLIS = 50L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
