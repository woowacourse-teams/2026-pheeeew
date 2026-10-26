package com.pheeeew.feature.screens.collection

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import kotlinx.coroutines.launch
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionCardDialogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun saveFailureShowsSnackbarInsideDialogWithoutRetryAction() {
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    AppTheme {
                        CollectionCardDialog(
                            card = sampleCard(),
                            imageActionError = CollectionCardActionError.SAVE,
                            imageActionErrorCardId = "capture-regression",
                            imageActionErrorId = 1,
                            onDismiss = {},
                            onSave = {},
                            onShare = {},
                        )
                    }
                }
            }

            checkNotNull(device.wait(Until.findObject(By.text("카드를 저장하지 못했어요. 다시 시도해 주세요")), 5_000))
            assertTrue(device.hasObject(By.text("카드 저장")), "Dialog should remain available after save failure")
            assertTrue(device.wait(Until.gone(By.text("다시 시도")), 1_000), "Failure feedback must not add a retry action")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun savedPngContainsOpaqueCardPixels() {
        val savedBytes = AtomicReference<ByteArray?>()
        val saved = CountDownLatch(1)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    AppTheme {
                        val scope = rememberCoroutineScope()
                        CollectionCardDialog(
                            card = sampleCard(),
                            onDismiss = {},
                            onSave = { capture ->
                                scope.launch {
                                    savedBytes.set(capture())
                                    saved.countDown()
                                }
                            },
                            onShare = {},
                        )
                    }
                }
            }
            val saveButton = checkNotNull(device.wait(Until.findObject(By.text("카드 저장")), 5_000))
            assertTrue(device.wait(Until.gone(By.text("이미지 생성 중")), 5_000))
            device.waitForIdle()
            saveButton.click()
            assertTrue(saved.await(5, TimeUnit.SECONDS), "저장 callback이 호출되어야 합니다")

            val bytes = checkNotNull(savedBytes.get())
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                assertTrue(bitmap.width > 0 && bitmap.height > 0, "PNG 크기가 유효해야 합니다")
                assertEquals(
                    255,
                    Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)),
                    "저장된 카드 중앙은 완전히 불투명해야 합니다",
                )
            } finally {
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun sampleCard() =
        CollectionCardUiModel(
            id = "capture-regression",
            period = "2026.10.26",
            title = "오늘도히유",
            emotion = EmotionType.EXHAUSTED,
            pressLabel = "지침 프레스",
            pressCount = 312,
            stampLabel = "지침 스탬프",
            stampCount = 23,
        )
}
