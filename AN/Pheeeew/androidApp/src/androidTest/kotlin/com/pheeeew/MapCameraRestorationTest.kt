package com.pheeeew

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapCameraRestorationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun cameraAndCompassSurvivePressTabRoundTrips() {
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        val initial = awaitMap(activity)
        awaitInitialLocationCamera(initial.second)
        onMain {
            initial.second.cameraPosition =
                CameraPosition.Builder(initial.second.cameraPosition).bearing(0.0).build()
        }
        SystemClock.sleep(1000)
        onMain { compassPosition(initial.first) }
        onMain {
            initial.second.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(LatLng(37.51, 127.04))
                    .zoom(13.0)
                    .bearing(63.0)
                    .tilt(25.0)
                    .build()
        }
        SystemClock.sleep(1000)
        val expected = onMain { initial.second.cameraPosition }
        val compassPosition = onMain { compassPosition(initial.first) }
        for (releaseMap in listOf(false, true)) {
            clickText("프레스")
            if (releaseMap) {
                SystemClock.sleep(1000)
                onMain {
                    activity.application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                }
                awaitMapReleased(activity)
            } else {
                SystemClock.sleep(31000)
                assertSame(
                    "Map stays mounted while another tab is open",
                    initial.first,
                    onMain { findMap(activity.window.decorView) },
                )
            }
            clickText("지도")
            val restored = awaitMap(activity)
            if (releaseMap) {
                assertNotSame("Memory pressure releases the hidden map", initial.first, restored.first)
            } else {
                assertSame("Tab round trip retains the map", initial.first, restored.first)
            }
            SystemClock.sleep(1000)
            val actual = onMain { restored.second.cameraPosition }
            val scenario = if (releaseMap) "released map" else "retained map"
            assertEquals("$scenario latitude", expected.target!!.latitude, actual.target!!.latitude, 0.00001)
            assertEquals("$scenario longitude", expected.target!!.longitude, actual.target!!.longitude, 0.00001)
            assertEquals("$scenario zoom", expected.zoom, actual.zoom, 0.001)
            assertEquals("$scenario bearing", expected.bearing, actual.bearing, 0.001)
            assertEquals("$scenario tilt", expected.tilt, actual.tilt, 0.001)
            assertEquals("$scenario compass position", compassPosition, onMain { compassPosition(restored.first) })
        }
    }

    private fun awaitMapReleased(activity: Activity) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain { findMap(activity.window.decorView) == null }) return
            SystemClock.sleep(100)
        }
        error("Hidden map did not leave composition after memory pressure")
    }

    private fun awaitMap(activity: Activity): Pair<MapView, MapLibreMap> {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            val result =
                onMain {
                    val view = findMap(activity.window.decorView)
                    var map: MapLibreMap? = null
                    view?.getMapAsync { if (it.style?.isFullyLoaded == true) map = it }
                    map?.let { view!! to it }
                }
            if (result != null) return result
            SystemClock.sleep(100)
        }
        error("Map did not become ready within 30 seconds")
    }

    private fun awaitInitialLocationCamera(map: MapLibreMap) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            val target = onMain { map.cameraPosition.target }
            if (target != null &&
                (
                    kotlin.math.abs(target.latitude - 37.4409230460675) > 0.00001 ||
                        kotlin.math.abs(target.longitude - 127.147538132656) > 0.00001
                )
            ) {
                return
            }
            SystemClock.sleep(100)
        }
        error("Initial camera did not move from fallback to the acquired location within 30 seconds")
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findMap(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun compassPosition(map: MapView): List<Int> {
        val compass = map.findViewWithTag<View>("compassView")
        assertNotNull(compass)
        assertTrue("Map compass must be visible", compass.isShown && compass.alpha > 0f)
        val position = IntArray(2)
        compass.getLocationOnScreen(position)
        return position.toList()
    }

    private fun clickText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10000
        var node: AccessibilityNodeInfo? = null
        while (node == null && SystemClock.uptimeMillis() < deadline) {
            node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
            if (node == null) SystemClock.sleep(100)
        }
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("Clickable navigation item: $text", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun findText(
        node: AccessibilityNodeInfo?,
        text: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) {
            findText(node.getChild(index), text)?.let { return it }
        }
        return null
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
