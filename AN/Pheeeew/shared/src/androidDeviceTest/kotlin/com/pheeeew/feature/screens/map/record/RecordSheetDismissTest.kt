package com.pheeeew.feature.screens.map.record

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheet
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.record_discard_title
import java.io.File
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises real Dialog windows and IME without starting the map or contacting the API. */
class RecordSheetDismissTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun cancelAllowsEditingAndRepeatedBackAndSwipe() =
        withSheet { activity ->
            val originalTop = node("Edit probe 0").visibleBounds.top
            repeat(4) { index ->
                when (index) {
                    1 -> {
                        val bounds = node("Edit probe $index").visibleBounds
                        device.swipe(bounds.centerX(), bounds.centerY(), bounds.centerX(), device.displayHeight - 1, 20)
                    }

                    3 -> {
                        val description = runBlocking { getString(Res.string.record_discard_title) }
                        // The outside scrim shares this label; the handle is lower on screen.
                        device.findObjects(By.desc(description)).maxBy { it.visibleBounds.top }.click()
                    }

                    else -> {
                        device.pressBack()
                    }
                }
                node("Stay").click()
                val restored = node("Edit probe $index")
                assertTrue(
                    abs(restored.visibleBounds.top - originalTop) <= 3,
                    "After cancel the sheet must return to its original vertical position",
                )
                restored.click()
                node("Edit probe ${index + 1}")
                assertFalse(activity.isFinishing)
            }
        }

    @Test
    fun outsideTapWhileTypingOnlyDismissesKeyboard() =
        withSheet {
            node("draft").click()
            assertTrue(waitForIme(visible = true), "The memo must open the IME before testing dismissal")
            device.waitForIdle()
            device.click(device.displayWidth / 2, device.displayHeight / 8)
            assertTrue(waitForIme(visible = false))
            assertFalse(device.hasObject(By.text("Discard draft")))
            // A new outside tap, after the IME has closed, may request confirmation.
            device.click(device.displayWidth / 2, device.displayHeight / 8)
            node("Stay").click()
            node("Edit probe 0").click()
            node("Edit probe 1")
        }

    private fun withSheet(block: (ComponentActivity) -> Unit) {
        val accessibility = instrumentation.uiAutomation.serviceInfo
        val originalFlags = accessibility.flags
        accessibility.flags =
            accessibility.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = accessibility
        val intent =
            Intent(instrumentation.targetContext, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        var confirmationVisible by remember { mutableStateOf(false) }
                        var editCount by remember { mutableStateOf(0) }
                        var memo by remember { mutableStateOf("draft") }
                        RecordBottomSheet(
                            selectedEmotion = EmotionTypeUiModel.ANGRY,
                            inputMode = RecordInputModeUiModel.Memo,
                            memo = memo,
                            isAnonymous = false,
                            onAnonymousChange = {},
                            onDismissRequest = { confirmationVisible = true },
                            onInputModeChange = {},
                            onMemoChange = { memo = it },
                            onNext = {},
                            feedbackContent = {
                                Button(onClick = { editCount++ }) { Text("Edit probe $editCount") }
                            },
                            isDismissConfirmationVisible = confirmationVisible,
                            dismissConfirmationContent = {
                                if (confirmationVisible) {
                                    ConfirmDialog(
                                        title = "Discard draft",
                                        content = "Unsaved memo",
                                        confirmText = "Leave",
                                        cancelText = "Stay",
                                        onConfirm = { activity.finish() },
                                        onCancel = { confirmationVisible = false },
                                    )
                                }
                            },
                        )
                    }
                }
            }
            node("Edit probe 0")
            // Wait until the initial sheet expansion has settled before starting a gesture.
            device.waitForIdle()
            block(activity)
        } catch (error: Throwable) {
            device.takeScreenshot(
                File(instrumentation.targetContext.externalCacheDir, "sheet-failure-${SystemClock.uptimeMillis()}.png"),
            )
            throw error
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            accessibility.flags = originalFlags
            instrumentation.uiAutomation.serviceInfo = accessibility
        }
    }

    private fun node(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(text)), 5_000)) { "Missing UI: $text" }

    private fun waitForIme(visible: Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val imeWindow =
                instrumentation.uiAutomation.windows.any {
                    it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                }
            if (imeWindow == visible) return true
            SystemClock.sleep(50)
        }
        return false
    }
}
