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

/** The actual onboarding screen, with no API/session/recording dependencies. */
class OnboardingFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun fourSlidesThenStartOnMap() {
        device.wakeUp()
        val writes = AtomicInteger()
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
                                writes.incrementAndGet()
                            })
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
            assertEquals(0, writes.get())
            node("시작하기").click()
            node("지도 화면")
            assertFalse(device.hasObject(By.text("첫 사용 안내를 마쳤어요")))
            assertEquals(1, writes.get())
            screenshot("final-onboarding-started-map")
        } catch (error: Throwable) {
            screenshot("final-onboarding-failure")
            throw error
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun node(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(text)), 7000)) {
            "Missing UI: $text"
        }

    private fun screenshot(name: String) {
        device.takeScreenshot(File(instrumentation.targetContext.externalCacheDir, "$name.png"))
    }
}
