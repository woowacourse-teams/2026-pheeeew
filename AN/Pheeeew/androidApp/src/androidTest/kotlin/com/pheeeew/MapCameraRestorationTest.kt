package com.pheeeew

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapCameraRestorationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun cameraAndCompassSurviveTabRoundTrips() {
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        val initial = awaitMap(activity)
        // Let the initial asynchronous location acquisition settle before moving the camera.
        SystemClock.sleep(5000)
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
        for (destination in listOf("그룹", "랭킹", "그룹", "랭킹")) {
            clickText(destination)
            SystemClock.sleep(1000)
            assertTrue("Map should leave composition", onMain { findMap(activity.window.decorView) == null })
            clickText("지도")
            val restored = awaitMap(activity)
            SystemClock.sleep(1000)
            val actual = onMain { restored.second.cameraPosition }
            assertEquals("$destination latitude", expected.target!!.latitude, actual.target!!.latitude, 0.00001)
            assertEquals("$destination longitude", expected.target!!.longitude, actual.target!!.longitude, 0.00001)
            assertEquals("$destination zoom", expected.zoom, actual.zoom, 0.001)
            assertEquals("$destination bearing", expected.bearing, actual.bearing, 0.001)
            assertEquals("$destination tilt", expected.tilt, actual.tilt, 0.001)
            assertEquals("$destination compass position", compassPosition, onMain { compassPosition(restored.first) })
        }
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
        assertTrue("Rotated map compass must be visible", compass.isShown && compass.alpha > 0f)
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
