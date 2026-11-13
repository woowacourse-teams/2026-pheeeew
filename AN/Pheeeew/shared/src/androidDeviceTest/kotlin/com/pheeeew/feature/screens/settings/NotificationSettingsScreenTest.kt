package com.pheeeew.feature.screens.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.NotificationPreferences
import com.pheeeew.core.notification.NotificationPreferencesStore
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingsScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun rowsExposeNamedSwitchesAndDispatchOneChangePerTap() {
        val state =
            mutableStateOf(
                NotificationSettingsUiState(
                    preferences = NotificationPreferences(false, false),
                    authorization = NotificationAuthorization.AUTHORIZED,
                    busy = false,
                ),
            )
        val changes = LinkedBlockingQueue<String>()
        withScreen {
            NotificationSettingsScreen(
                state = state.value,
                onBackClick = {},
                onToggleAll = { enabled ->
                    changes.offer("all:$enabled")
                    state.value = state.value.copy(preferences = NotificationPreferences(enabled, enabled))
                },
                onToggle = { kind, enabled ->
                    changes.offer("$kind:$enabled")
                    val current = requireNotNull(state.value.preferences)
                    state.value =
                        state.value.copy(
                            preferences =
                                when (kind) {
                                    NotificationKind.COLLECTION -> current.copy(collectionEnabled = enabled)
                                    NotificationKind.RANK_DROP -> current.copy(rankDropEnabled = enabled)
                                },
                        )
                },
                onRetry = {},
            )
        }.use {
            listOf("전체 알림", "컬렉션 발급 알림", "순위 알림").forEach { title ->
                val row = switchRow(title)
                assertTrue(row.isCheckable, "Expected named switch: $title (${row.className})")
                assertFalse(row.isChecked)
            }
            assertEquals(3, device.findObjects(By.pkg("com.pheeeew.shared.test").checkable(true)).size)
            switchRow("컬렉션 발급 알림").click()
            assertEquals("COLLECTION:true", changes.poll(5, TimeUnit.SECONDS))
            device.waitForIdle()
            assertTrue(switchRow("컬렉션 발급 알림").isChecked)
            assertFalse(switchRow("순위 알림").isChecked)
            switchRow("전체 알림").click()
            assertEquals("all:true", changes.poll(5, TimeUnit.SECONDS))
            device.waitForIdle()
            assertTrue(switchRow("전체 알림").isChecked)
            assertTrue(switchRow("순위 알림").isChecked)
            assertTrue(changes.isEmpty())
        }
    }

    @Test
    fun closingNotificationScreenKeepsParentSaveAndStateAlive() {
        val gate = CompletableDeferred<Unit>()
        val saved = LinkedBlockingQueue<NotificationPreferences>()
        val store =
            object : NotificationPreferencesStore {
                private var preferences = NotificationPreferences(false, false)

                override suspend fun read() = preferences

                override suspend fun update(
                    kind: NotificationKind,
                    enabled: Boolean,
                ): NotificationPreferences {
                    gate.await()
                    preferences =
                        when (kind) {
                            NotificationKind.COLLECTION -> preferences.copy(collectionEnabled = enabled)
                            NotificationKind.RANK_DROP -> preferences.copy(rankDropEnabled = enabled)
                        }
                    saved.offer(preferences)
                    return preferences
                }
            }
        val model = NotificationSettingsState(Permissions(), store)
        val visible = mutableStateOf(true)
        withScreen {
            val parentScope = rememberCoroutineScope()
            if (visible.value) {
                NotificationSettingsRoute(model, parentScope, onBackClick = { visible.value = false })
            } else {
                TextButton(onClick = { visible.value = true }) { Text("알림 다시 열기") }
            }
        }.use {
            switchRow("컬렉션 발급 알림").click()
            device.waitForIdle()
            assertTrue(switchRow("컬렉션 발급 알림").isChecked)
            checkNotNull(device.wait(Until.findObject(By.desc("뒤로가기")), 5_000)).click()
            node("알림 다시 열기")
            gate.complete(Unit)
            assertEquals(NotificationPreferences(true, false), saved.poll(5, TimeUnit.SECONDS))
            node("알림 다시 열기").click()
            assertTrue(switchRow("컬렉션 발급 알림").isChecked)
            assertFalse(switchRow("순위 알림").isChecked)
        }
    }

    @Test
    fun successfulSettingsOpenClearsOldFailureOnNextDialog() {
        val permissions = Permissions().apply { status = NotificationAuthorization.DENIED }
        withScreen {
            NotificationPermissionRow(permissions, onOpen = {})
        }.use {
            node("알림 설정").click()
            node("기기 설정 열기").click()
            node("기기 설정을 열지 못했어요. 다시 시도해 주세요.")
            instrumentation.runOnMainSync { permissions.canOpen = true }
            node("기기 설정 열기").click()
            device.waitForIdle()
            node("알림 설정").click()
            node("기기 설정 열기")
            assertFalse(device.hasObject(By.text("기기 설정을 열지 못했어요. 다시 시도해 주세요.")))
        }
    }

    private fun withScreen(content: @Composable () -> Unit): AutoCloseable {
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        instrumentation.runOnMainSync {
            activity.actionBar?.hide()
            activity.setContent {
                SettingsTheme {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
                }
            }
        }
        return AutoCloseable {
            device.dumpWindowHierarchy(File(instrumentation.targetContext.externalCacheDir, "settings-ui.xml"))
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun switchRow(title: String): UiObject2 =
        checkNotNull(
            device.wait(Until.findObject(By.checkable(true).enabled(true).hasDescendant(By.text(title))), 5_000),
        ) {
            "Missing named switch row: $title"
        }

    private fun node(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(text)), 5_000)) { "Missing UI: $text" }

    private class Permissions : NotificationPermissionController {
        var status = NotificationAuthorization.AUTHORIZED
        var canOpen = false

        override suspend fun authorization() = status

        override suspend fun canRequestAuthorization() = false

        override suspend fun requestAuthorization() = Unit

        override suspend fun openSettings() = canOpen
    }
}
