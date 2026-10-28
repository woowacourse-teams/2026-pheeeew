package com.pheeeew.feature.screens.collection

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import kotlinx.coroutines.launch
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun pullRefreshWorksInCoverFlowAlbumAndEmptyState() {
        val model = mutableStateOf(sampleCollection())
        val refreshes = AtomicInteger()
        val refreshEvents = LinkedBlockingQueue<Int>()
        val haptics = CopyOnWriteArrayList<HapticFeedbackType>()
        val hapticFeedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics.add(hapticFeedbackType)
                }
            }
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.actionBar?.hide()
                activity.setContent {
                    AppTheme {
                        CompositionLocalProvider(LocalHapticFeedback provides hapticFeedback) {
                            CollectionScreen(
                                uiModel = model.value,
                                onSaveCard = { _, _ -> },
                                onShareCard = { _, _ -> },
                                onRefresh = {
                                    refreshEvents.offer(refreshes.incrementAndGet())
                                    model.value = model.value.copy(isRefreshRequestActive = true)
                                },
                            )
                        }
                    }
                }
            }
            node("발급전")
            val headerTop = node("모은 카드 5장").visibleBounds.top
            pullDown {
                node("당겨서 새로고침")
                assertTrue(node("모은 카드 5장").visibleBounds.top > headerTop, "Page must follow the pull")
                assertEquals(0, refreshes.get(), "Refresh must wait until release")
            }
            assertEquals(1, refreshEvents.poll(5, TimeUnit.SECONDS), "Cover flow must dispatch pull refresh")
            assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.toList())
            val returnDeadline = SystemClock.uptimeMillis() + 2_000
            while (node("모은 카드 5장").visibleBounds.top != headerTop && SystemClock.uptimeMillis() < returnDeadline) {
                SystemClock.sleep(20)
            }
            assertEquals(headerTop, node("모은 카드 5장").visibleBounds.top, "Return must not wait for the request")
            instrumentation.runOnMainSync {
                model.value = model.value.copy(isRefreshIndicatorVisible = true)
            }
            device.waitForIdle()
            assertEquals(headerTop, node("모은 카드 5장").visibleBounds.top, "Loading must not move the page")

            pullDown()
            assertEquals(1, refreshes.get(), "An active request must block duplicate refresh")
            assertEquals(1, haptics.size)
            instrumentation.runOnMainSync {
                model.value = model.value.copy(isRefreshRequestActive = false, isRefreshIndicatorVisible = false)
            }
            device.waitForIdle()
            val pendingBounds = card("발급 전").visibleBounds
            device.swipe(
                device.displayWidth * 3 / 4,
                pendingBounds.centerY(),
                device.displayWidth / 4,
                pendingBounds.centerY(),
                30,
            )
            device.waitForIdle()
            node("지침")

            node("앨범").click()
            node("전체")
            pullDown()
            assertEquals(2, refreshEvents.poll(5, TimeUnit.SECONDS), "Album must dispatch pull refresh")
            instrumentation.runOnMainSync {
                model.value =
                    model.value.copy(
                        cards = emptyList(),
                        totalCount = 0,
                        pendingProgress = null,
                        isRefreshRequestActive = false,
                    )
            }
            node("모은 카드 0장")
            node("커버플로").click()
            device.waitForIdle()
            assertFalse(device.hasObject(By.text("전체")), "Cover flow must be selected")
            node("아직 모은 감정 카드가 없어요")
            pullDown { node("당겨서 새로고침") }
            assertEquals(3, refreshEvents.poll(5, TimeUnit.SECONDS), "Empty cover flow must dispatch pull refresh")
            assertEquals(List(3) { HapticFeedbackType.GestureThresholdActivate }, haptics.toList())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun pullDown(onPulled: () -> Unit = {}) {
        val downTime = SystemClock.uptimeMillis()
        val startY = device.displayHeight / 3f
        val endY = device.displayHeight * 4 / 5f

        fun send(
            action: Int,
            y: Float,
        ) {
            val event =
                MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, device.displayWidth / 2f, y, 0)
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, startY)
        try {
            repeat(40) { step ->
                send(MotionEvent.ACTION_MOVE, startY + (endY - startY) * (step + 1) / 40f)
                SystemClock.sleep(10)
            }
            onPulled()
        } finally {
            send(MotionEvent.ACTION_UP, endY)
        }
        device.waitForIdle()
    }

    @Test
    fun swipeAlbumSelectionActionsAndRemovedSelection() {
        val model = mutableStateOf(sampleCollection())
        val savedBytes = AtomicReference<ByteArray?>()
        val sharedBytes = AtomicReference<ByteArray?>()
        val claimedEmotion = AtomicReference<EmotionType>()
        val saved = CountDownLatch(1)
        val shared = CountDownLatch(1)
        val claimed = CountDownLatch(1)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.actionBar?.hide()
                activity.setContent {
                    AppTheme {
                        val scope = rememberCoroutineScope()
                        CollectionScreen(
                            uiModel = model.value,
                            onSaveCard = { _, capture ->
                                scope.launch {
                                    savedBytes.set(capture())
                                    saved.countDown()
                                }
                            },
                            onShareCard = { _, capture ->
                                scope.launch {
                                    sharedBytes.set(capture())
                                    shared.countDown()
                                }
                            },
                            onClaim = {
                                claimedEmotion.set(it)
                                claimed.countDown()
                            },
                        )
                    }
                }
            }
            node("모은 카드 5장")
            screenshot("coverflow")
            node("발급전")
            node("발급하기").click()
            assertTrue(claimed.await(5, TimeUnit.SECONDS))
            assertEquals(EmotionType.EXHAUSTED, claimedEmotion.get())
            val cardBounds = card("발급 전").visibleBounds
            device.swipe(
                device.displayWidth * 3 / 4,
                cardBounds.centerY(),
                device.displayWidth / 4,
                cardBounds.centerY(),
                30,
            )
            device.waitForIdle()
            node("지침")
            instrumentation.runOnMainSync { model.value = model.value.copy(cards = emptyList()) }
            node("발급전")
            instrumentation.runOnMainSync { model.value = sampleCollection() }
            val pendingCardBounds = card("발급 전").visibleBounds
            device.swipe(
                device.displayWidth * 3 / 4,
                pendingCardBounds.centerY(),
                device.displayWidth / 4,
                pendingCardBounds.centerY(),
                30,
            )
            device.waitForIdle()
            node("지침")
            node("앨범").click()
            card("답답")
            screenshot("album")
            card("지침").click()
            node("카드 저장")
            screenshot("dialog")
            node("카드 저장").click()
            assertTrue(saved.await(5, TimeUnit.SECONDS))
            assertTrue(checkNotNull(savedBytes.get()).isNotEmpty())
            node("공유").click()
            assertTrue(shared.await(5, TimeUnit.SECONDS))
            assertTrue(checkNotNull(sharedBytes.get()).isNotEmpty())
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.text("카드 저장")), 5_000))
            repeat(4) {
                if (!device.hasObject(By.descContains("분노 카드 ·"))) {
                    checkNotNull(device.findObject(By.scrollable(true))).scroll(Direction.DOWN, 1f)
                    device.waitForIdle()
                }
            }
            card("분노")
            screenshot("album-scrolled")
            card("분노").click()
            node("카드 저장")
            instrumentation.runOnMainSync { model.value = model.value.copy(cards = emptyList()) }
            node("발급 전")
            assertFalse(device.hasObject(By.text("카드 저장")))
            instrumentation.runOnMainSync { model.value = sampleCollection() }
            node("모은 카드 5장")
            assertFalse(device.hasObject(By.text("카드 저장")))
            instrumentation.runOnMainSync {
                val current = model.value
                val issued = current.cards.first().copy(id = "issued-today", title = "오늘 발급 카드")
                model.value =
                    current.copy(
                        cards = listOf(issued) + current.cards,
                        pendingProgress = null,
                        scrollToTopRequestId = current.scrollToTopRequestId + 1,
                    )
            }
            val issuedSelector = By.descContains("오늘 발급 카드")
            val issuedAlbumCard = checkNotNull(device.wait(Until.findObject(issuedSelector), 5_000))
            assertTrue(
                issuedAlbumCard.visibleBounds.top < device.displayHeight / 2,
                "Album must reset to the first card",
            )
            node("커버플로").click()
            assertTrue(device.wait(Until.gone(By.text("전체")), 5_000))
            device.waitForIdle()
            val issuedCoverCard = checkNotNull(device.wait(Until.findObject(issuedSelector), 5_000))
            val coverBounds = issuedCoverCard.visibleBounds
            assertTrue(coverBounds.contains(device.displayWidth / 2, coverBounds.centerY()))
        } catch (error: Throwable) {
            screenshot("failure")
            throw error
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun card(label: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.descContains("$label 카드")), 5_000))

    private fun node(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(text)), 5_000)) { "Missing UI: $text" }

    private fun screenshot(name: String) {
        device.waitForIdle()
        device.takeScreenshot(File(instrumentation.targetContext.externalCacheDir, "collection-$name.png"))
    }

    private fun sampleCollection() =
        CollectionScreenUiModel(
            nickname = "오늘도히유",
            pendingProgress =
                CollectionProgressUiModel(
                    emotion = EmotionType.EXHAUSTED,
                    stampCount = 2,
                    stampTarget = 1,
                    stampRemaining = 0,
                    pressCount = 100,
                    pressTarget = 100,
                    pressRemaining = 0,
                    canClaim = true,
                    stampProgressByEmotion =
                        listOf(
                            CollectionEmotionProgress(EmotionType.FRUSTRATED, 1),
                            CollectionEmotionProgress(EmotionType.IRRITATED, 0),
                            CollectionEmotionProgress(EmotionType.EXHAUSTED, 1),
                            CollectionEmotionProgress(EmotionType.DISCOURAGED, 0),
                            CollectionEmotionProgress(EmotionType.ANGRY, 0),
                        ),
                    pressProgressByEmotion =
                        listOf(
                            CollectionEmotionProgress(EmotionType.FRUSTRATED, 20),
                            CollectionEmotionProgress(EmotionType.IRRITATED, 40),
                            CollectionEmotionProgress(EmotionType.EXHAUSTED, 30),
                            CollectionEmotionProgress(EmotionType.DISCOURAGED, 5),
                            CollectionEmotionProgress(EmotionType.ANGRY, 5),
                        ),
                ),
            cards =
                listOf(
                    EmotionType.EXHAUSTED,
                    EmotionType.FRUSTRATED,
                    EmotionType.IRRITATED,
                    EmotionType.DISCOURAGED,
                    EmotionType.ANGRY,
                ).map { emotion ->
                    val label =
                        when (emotion) {
                            EmotionType.EXHAUSTED -> "지침"
                            EmotionType.FRUSTRATED -> "답답"
                            EmotionType.IRRITATED -> "짜증"
                            EmotionType.DISCOURAGED -> "좌절"
                            EmotionType.ANGRY -> "분노"
                        }
                    CollectionCardUiModel(
                        id = emotion.name,
                        period = "2026.10.18",
                        title = "오늘도히유",
                        emotion = emotion,
                        pressLabel = "$label 프레스",
                        pressCount = 312,
                        stampLabel = "$label 스탬프",
                        stampCount = 23,
                    )
                },
        )
}
