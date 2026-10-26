package com.pheeeew.feature.screens.onboarding

import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.map.overlay.MapOverlay
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The actual onboarding and guide screens, with no API/session/recording dependencies. */
class OnboardingGuideFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun fourSlidesThreeOverviewsThenStartOnMap() {
        device.wakeUp()
        val writes = AtomicInteger()
        val guide = FeatureGuideState(true) { writes.incrementAndGet() }
        val activity =
            instrumentation.startActivitySync(
                Intent(
                    instrumentation.targetContext,
                    ComponentActivity::class.java,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.actionBar?.hide()
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent {
                    AppTheme {
                        var intro by remember { mutableStateOf(true) }
                        if (intro) {
                            OnboardingScreen(onFinished = {
                                intro = false
                                guide.start()
                            })
                        } else if (guide.pending) {
                            FeatureGuideScreen(guide)
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(Color(0xFFE8EDE2)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("지도 화면")
                                MapOverlay(
                                    onListClick = {},
                                    onSettingClick = {},
                                    onFilterClick = {},
                                    isEmotionSelectorExpanded = false,
                                    onEmotionSelectorToggle = {},
                                    onEmotionBubbleClick = {},
                                    onRefreshClick = {},
                                    onMyLocationClick = {},
                                    isRequestingLocation = false,
                                    isMapError = false,
                                )
                            }
                        }
                    }
                }
            }
            val titles =
                listOf(
                    "지금 내 마음을\n감정으로 골라요.",
                    "말하기 어려운 마음도\n편하게 남겨요.",
                    "쌓인 감정은\n꾹꾹 눌러 풀어요.",
                    "차곡차곡 모인 감정,\n나만의 카드가 돼요.",
                )
            titles.forEachIndexed { index, title ->
                node(title)
                SystemClock.sleep(650)
                screenshot("final-onboarding-${index + 1}")
                if (index < titles.lastIndex) node("다음").click()
            }
            assertFalse(device.hasObject(By.text("일일 랭킹")))
            node("사용법 살펴보기").click()
            node("주변의 감정을 목록으로")
            screenshot("final-guide-map")
            assertEquals(0, writes.get())
            swipeGuide()
            node("전체 순위가 궁금할 땐")
            SystemClock.sleep(650)
            assertEquals(1, guide.page)
            device.pressBack()
            node("주변의 감정을 목록으로")
            SystemClock.sleep(650)
            assertEquals(0, guide.page)
            swipeGuide()
            node("전체 순위가 궁금할 땐")
            screenshot("final-guide-press")
            swipeGuide()
            node("내 감정이 카드로 모여요")
            SystemClock.sleep(650)
            assertEquals(2, guide.page)
            assertFalse(device.hasObject(By.text("랭킹")))
            screenshot("final-guide-collection")
            assertEquals(0, writes.get())
            assertFalse(device.hasObject(By.clazz("android.widget.EditText")))
            node("시작하기").click()
            node("지도 화면")
            assertFalse(device.hasObject(By.text("첫 사용 안내를 마쳤어요")))
            assertFalse(guide.pending)
            assertEquals(1, writes.get())
            screenshot("final-guide-started-map")
        } catch (error: Throwable) {
            screenshot("final-onboarding-failure")
            throw error
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun swipeGuide() {
        device.swipe(
            device.displayWidth * 4 / 5,
            device.displayHeight / 2,
            device.displayWidth / 5,
            device.displayHeight / 2,
            30,
        )
    }

    private fun node(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(text)), 7000)) {
            "Missing UI: $text"
        }

    private fun screenshot(name: String) {
        device.takeScreenshot(File(instrumentation.targetContext.externalCacheDir, "$name.png"))
    }
}
