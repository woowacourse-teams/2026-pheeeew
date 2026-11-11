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
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PressEventButtonVisibilityTest {
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
