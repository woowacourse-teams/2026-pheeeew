package com.pheeeew.feature.screens.press

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.pressevent.PressEventEntryScreen
import com.pheeeew.feature.screens.pressevent.PressEventEntryStatus
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PressEventButtonVisibilityTest {
    @Test
    fun eventEntryErrorsShowSpecificMessageAndConfirmGoesBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        val status = mutableStateOf(PressEventEntryStatus.Disabled)
        val backs = AtomicInteger()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    AppTheme { PressEventEntryScreen(status.value, onBack = { backs.incrementAndGet() }) }
                }
            }
            val cases =
                listOf(
                    Triple(PressEventEntryStatus.Disabled, "현재 진행중인 이벤트가 없어요.", "다음 이벤트를 기다려주세요"),
                    Triple(PressEventEntryStatus.Offline, "진행 중인 이벤트를 불러오지 못했어요", "네트워크 연결을 확인해주세요"),
                    Triple(PressEventEntryStatus.Failed, "진행 중인 이벤트를 불러오지 못했어요", "다음에 다시 시도해주세요"),
                )
            cases.forEachIndexed { index, (state, title, message) ->
                instrumentation.runOnMainSync { status.value = state }
                assertNotNull(device.wait(Until.findObject(By.text(title)), 5_000))
                assertNotNull(device.wait(Until.findObject(By.text(message)), 5_000))
                assertNotNull(device.wait(Until.findObject(By.text("확인")), 5_000)).click()
                device.waitForIdle()
                assertEquals(index + 1, backs.get())
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun eventButtonTracksVisibilityWithoutRecreatingScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        val visible = mutableStateOf(false)
        val clicks = AtomicInteger()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    AppTheme {
                        PressScreen(
                            uiState = PressUiState(),
                            onEmotionTap = { true },
                            onOpenRanking = {},
                            onOpenEvent = { clicks.incrementAndGet() },
                            isEventButtonVisible = visible.value,
                        )
                    }
                }
            }
            val eventButton = By.textContains("이벤트")
            device.waitForIdle()
            assertTrue(device.wait(Until.gone(eventButton), 5_000))
            instrumentation.runOnMainSync { visible.value = true }
            assertNotNull(device.wait(Until.findObject(eventButton), 5_000)).click()
            device.waitForIdle()
            assertEquals(1, clicks.get())
            instrumentation.runOnMainSync { visible.value = false }
            assertTrue(device.wait(Until.gone(eventButton), 5_000))
            instrumentation.runOnMainSync { visible.value = true }
            assertNotNull(device.wait(Until.findObject(eventButton), 5_000))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
