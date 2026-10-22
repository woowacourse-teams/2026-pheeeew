package com.pheeeew

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AndroidAppConfig
import com.pheeeew.core.di.AndroidAppServiceFactory
import com.pheeeew.core.di.AppServiceFactory
import com.pheeeew.core.di.createAndroidAppGraph
import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.data.local.device.AndroidDeviceCredentialStorage
import com.pheeeew.developertools.simulation.DebugAppServiceFactory
import com.pheeeew.developertools.simulation.ErrorSimulation
import com.pheeeew.developertools.simulation.ErrorSimulationState
import com.pheeeew.developertools.simulation.LocationSimulationMode
import com.pheeeew.developertools.simulation.LocationSimulationSettings
import com.pheeeew.domain.model.device.DeviceCredentials
import com.pheeeew.feature.screens.map.MapViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real Activity, platform permission controller, map and Compose host; API traffic stays in MockEngine. */
class DeveloperToolsLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val currentActivity = AtomicReference<Activity?>()
    private val prefix = "emulator-audit-"

    @Test
    fun rotationAndRepeatedApplyKeepProcessAndReplaceOnlyUiSession() = audit(verifyRecreation = true)

    @Test
    fun liveRecoveryKeepsUiSession() = audit(verifyRecreation = false)

    private fun audit(verifyRecreation: Boolean) {
        val accessibility = instrumentation.uiAutomation.serviceInfo
        accessibility.flags = accessibility.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = accessibility
        val application = instrumentation.targetContext.applicationContext as PheeeewApplication
        val originalGraph = application.appGraph
        val callbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    state: Bundle?,
                ) {
                    if (activity is MainActivity) currentActivity.set(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    if (activity is MainActivity) currentActivity.set(activity)
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    out: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        application.registerActivityLifecycleCallbacks(callbacks)
        val isolated = IsolatedApplication(application, prefix)
        val platform = AndroidAppServiceFactory(isolated)
        val pinRequests = AtomicInteger()
        val services =
            object : AppServiceFactory() {
                override fun createApiEngine(): HttpClientEngine =
                    MockEngine { request ->
                        val path = request.url.encodedPath
                        val body =
                            when {
                                path == "/api/v2/app/version" -> {
                                    """
                                    {"minSupportedVersion":"1.0.0","latestVersion":"2.3.0",
                                     "storeUrl":"https://example.test/app"}
                                    """.trimIndent()
                                }

                                path == "/api/v2/devices/tokens" -> {
                                    """{"accessToken":"audit-access","expiresIn":3600}"""
                                }

                                path == "/api/v3/devices/me/nickname" -> {
                                    """{"nickname":"에뮬레이터검증"}"""
                                }

                                path == "/api/v4/emotions/map" -> {
                                    pinRequests.incrementAndGet()
                                    """{"features":[],"hasNext":false,"nextCursor":null}"""
                                }

                                else -> {
                                    "[]"
                                }
                            }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                override fun createConnectivityObserver(): ConnectivityObserver = platform.createConnectivityObserver()
            }
        val graph =
            createAndroidAppGraph(
                isolated,
                AndroidAppConfig(
                    DeviceSessionBuildConfig(true, "dev", "https://api-dev.pheeeew.com", "platform_only", "2.3.0+11"),
                    "com.pheeeew",
                    0L,
                    MonitoringConfig("dev", "android", "2.3.0", "11", "34", false, "", "", ""),
                    "2.3.0",
                ),
                DebugAppServiceFactory(services),
            )
        val graphField = PheeeewApplication::class.java.getDeclaredField("appGraph").apply { isAccessible = true }
        try {
            runBlocking {
                assertTrue(
                    AndroidDeviceCredentialStorage(
                        isolated,
                        "dev",
                    ).write(DeviceCredentials(refreshToken = "audit-refresh", generation = 1)),
                )
            }
            graph.onboardingStore.complete()
            onMain { graphField.set(application, graph) }
            ErrorSimulation.replaceSettings(
                ErrorSimulationState(location = LocationSimulationSettings(LocationSimulationMode.FIXED)),
            )
            instrumentation.startActivitySync(
                Intent(
                    instrumentation.targetContext,
                    MainActivity::class.java,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            awaitText("지도")
            val beforeActivity = currentActivity.get()!!
            val owner = onMain { ActivityRetainedGraphOwner.get(beforeActivity as MainActivity, graph) }
            val session = owner.uiSession
            val processSession = graph.apiDependencies.appSession
            awaitMap()
            println("AUDIT native-map-mounted")
            if (verifyRecreation) {
                onMain { beforeActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                await("Activity recreated on rotation") {
                    currentActivity.get() !== beforeActivity &&
                        currentActivity
                            .get()
                            ?.resources
                            ?.configuration
                            ?.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
                }
                awaitText("지도")
                println("AUDIT landscape-recreation-complete")
                assertSame(
                    session,
                    onMain { ActivityRetainedGraphOwner.get(currentActivity.get() as MainActivity, graph).uiSession },
                )
                val landscape = currentActivity.get()!!
                onMain { landscape.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                await("Portrait recreation") {
                    currentActivity.get() !== landscape &&
                        currentActivity
                            .get()
                            ?.resources
                            ?.configuration
                            ?.orientation ==
                        Configuration.ORIENTATION_PORTRAIT
                }
                awaitText("지도")
                repeat(3) { index ->
                    val previous = owner.uiSession
                    // Select an error through the real developer tool, not a product ViewModel shortcut.
                    click("설정 버튼")
                    click("개발자 도구")
                    click("에러 상황 재현")
                    awaitText("네트워크 상태")
                    scrollTo("지역 감정 조회 실패")
                    clickRowAction("지역 감정 조회 실패", if (index % 2 == 0) "계속 실패" else "해제")
                    scrollTo("적용")
                    click("적용")
                    awaitText("선택한 재현 조건을 적용할까요?")
                    click("취소")
                    assertSame(previous, owner.uiSession)
                    click("적용")
                    click("완료")
                    await("New UI session") { owner.uiSession !== previous }
                    awaitText("지도")
                    assertSame(processSession, graph.apiDependencies.appSession)
                    assertSame(graph, application.appGraph)
                    awaitMap()
                    println("AUDIT apply-cycle-${index + 1}-complete")
                }
                // Restore normal API inputs through the existing apply-and-reset flow first.
                click("설정 버튼")
                click("개발자 도구")
                click("에러 상황 재현")
                scrollTo("지역 감정 조회 실패")
                clickRowAction("지역 감정 조회 실패", "해제")
                scrollTo("적용")
                click("적용")
                click("완료")
                awaitText("지도")
                awaitMap()
            }
            await("Initial real product map query") { pinRequests.get() > 0 }
            val recoverySession = owner.uiSession
            click("설정 버튼")
            click("개발자 도구")
            click("에러 상황 재현")
            scrollTo("5초 후")
            click("5초 후")
            click("수동 복구")
            scrollTo("현재 화면에서 시나리오 실행")
            click("현재 화면에서 시나리오 실행")
            click("실행")
            awaitText("복구 시나리오: 강제 오프라인")
            assertSame(recoverySession, owner.uiSession)
            assertTrue(ErrorSimulation.state.value.offline)
            click("뒤로가기")
            awaitText("설정 버튼")
            val productMap =
                onMain {
                    ViewModelProvider(
                        owner.uiSession,
                        viewModelFactory {
                            initializer<MapViewModel> { error("The product graph must already own the map model") }
                        },
                    )[MapViewModel::class.java]
                }
            await("Product map observes offline") { productMap.uiModel.value.isOffline }
            println("AUDIT product-map-observes-offline")
            capture("recovery-offline")
            awaitText("인터넷 연결이 끊겼어요. 연결 상태를 확인해주세요.")
            val beforeRecovery = pinRequests.get()
            click("지금 복구")
            await("Product connectivity observer re-queries map data") { pinRequests.get() > beforeRecovery }
            await("Product map observes restored connectivity") { !productMap.uiModel.value.isOffline }
            assertSame(recoverySession, owner.uiSession)
            assertSame(processSession, graph.apiDependencies.appSession)
            assertSame(graph, application.appGraph)
            assertTrue(!ErrorSimulation.state.value.offline)
            println("AUDIT live-network-recovery-keeps-ui-session")
            capture("success")
        } catch (failure: Throwable) {
            capture("failure")
            printVisible(instrumentation.uiAutomation.rootInActiveWindow)
            throw failure
        } finally {
            onMain { currentActivity.get()?.finish() }
            instrumentation.waitForIdleSync()
            graph.apiDependencies.close()
            onMain { graphField.set(application, originalGraph) }
            application.unregisterActivityLifecycleCallbacks(callbacks)
            ErrorSimulation.clear()
            // Only fixture preferences are removed, never the user's normal app preferences.
            instrumentation.targetContext.filesDir.parentFile
                ?.resolve("shared_prefs")
                ?.listFiles()
                ?.filter {
                    it.name.startsWith(prefix)
                }?.forEach { it.delete() }
        }
    }

    private fun awaitMap() =
        await("Native MapView attached") {
            onMain {
                findMap(currentActivity.get()?.window?.decorView) !=
                    null
            }
        }

    private fun findMap(view: View?): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun awaitText(text: String) =
        await("Visible text: $text") {
            visibleNode(text) !=
                null
        }

    private fun click(text: String) {
        awaitText(text)
        var node = visibleNode(text)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("Clickable: $text", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun clickRowAction(
        title: String,
        action: String,
    ) {
        var node = findNode(instrumentation.uiAutomation.rootInActiveWindow, title)
        while (node != null && findNode(node, action) == null) node = node.parent
        val button = findNode(node, action)
        assertNotNull("$title / $action", button)
        var clickable = button
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        assertTrue(clickable!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun scrollTo(text: String) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (findNode(root, text) != null) return
            val target = findNode(root, text, visibleOnly = false)
            target?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            instrumentation.waitForIdleSync()
            if (findNode(instrumentation.uiAutomation.rootInActiveWindow, text) != null) return
            val scrollable = findScrollable(instrumentation.uiAutomation.rootInActiveWindow)
            if (scrollable?.performAction(direction) != true) {
                if (direction == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    break
                }
            }
            SystemClock.sleep(150)
            instrumentation.waitForIdleSync()
        }
        error("Could not scroll to $text")
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        for (i in node.childCount - 1 downTo 0) findScrollable(node.getChild(i))?.let { return it }
        return node.takeIf { it.isVisibleToUser && it.isScrollable }
    }

    private fun capture(name: String) {
        val path = instrumentation.targetContext.getExternalFilesDir(null)!!.resolve("emulator-audit-$name.png")
        path.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun printVisible(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.isVisibleToUser && (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank())) {
            println("AUDIT_VISIBLE text=${node.text} description=${node.contentDescription}")
        }
        for (i in 0 until node.childCount) printVisible(node.getChild(i))
    }

    private fun visibleNode(text: String): AccessibilityNodeInfo? {
        findNode(instrumentation.uiAutomation.rootInActiveWindow, text)?.let { return it }
        for (window in instrumentation.uiAutomation.windows) findNode(window.root, text)?.let { return it }
        return null
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        text: String,
        visibleOnly: Boolean = true,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if ((!visibleOnly || node.isVisibleToUser) &&
            (node.text?.toString() == text || node.contentDescription?.toString() == text)
        ) {
            return node
        }
        for (i in 0 until node.childCount) findNode(node.getChild(i), text, visibleOnly)?.let { return it }
        return null
    }

    private fun await(
        message: String,
        test: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (test()) return
            SystemClock.sleep(100)
        }
        error(message)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private class IsolatedApplication(
        base: Context,
        private val prefix: String,
    ) : Application() {
        init {
            attachBaseContext(base)
        }

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences = super.getSharedPreferences(prefix + name, mode)
    }
}
