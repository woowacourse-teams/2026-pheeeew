package com.pheeeew.feature.screens.settings

import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.NotificationPreferences
import com.pheeeew.core.notification.NotificationPreferencesStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingsStateTest {
    @Test
    fun `최초 기본 ON은 시스템 허용일 때만 켜짐으로 표시한다`() =
        runTest {
            val permissions = Permissions()
            val store = Store()
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            assertTrue(model.state.value.checked(NotificationKind.COLLECTION))
            permissions.status = NotificationAuthorization.DENIED
            model.refresh()
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
            assertTrue(store.saved.collectionEnabled)
        }

    @Test
    fun `앱에서 끈 설정은 재실행과 시스템 재허용 후에도 유지한다`() =
        runTest {
            val permissions = Permissions()
            val store = Store()
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            model.toggle(NotificationKind.COLLECTION, false)
            permissions.status = NotificationAuthorization.DENIED
            model.refresh()
            permissions.status = NotificationAuthorization.AUTHORIZED
            val restarted = NotificationSettingsState(permissions, store)
            restarted.refresh()
            assertFalse(restarted.state.value.checked(NotificationKind.COLLECTION))
            assertTrue(restarted.state.value.checked(NotificationKind.RANK_DROP))
        }

    @Test
    fun `차단된 토글은 바로 설정을 열고 허용 전에는 저장하지 않는다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.DENIED }
            val store = Store().apply { saved = NotificationPreferences(false, false) }
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            model.toggle(NotificationKind.COLLECTION, true)
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
            assertFalse(store.saved.collectionEnabled)
            assertEquals(1, permissions.settingsOpens)
            assertEquals(0, permissions.requests)
        }

    @Test
    fun `설정에서 허용하고 돌아오면 켜려던 토글만 저장한다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.DENIED }
            val store = Store().apply { saved = NotificationPreferences(false, false) }
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            model.toggle(NotificationKind.RANK_DROP, true)
            assertEquals(1, permissions.settingsOpens)
            assertEquals(0, permissions.requests)
            permissions.status = NotificationAuthorization.AUTHORIZED
            model.refresh()
            assertTrue(model.state.value.checked(NotificationKind.RANK_DROP))
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
        }

    @Test
    fun `허용하지 않고 복귀하면 이후 외부 권한 변경으로 앱 OFF 설정을 덮어쓰지 않는다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.DENIED }
            val store = Store().apply { saved = NotificationPreferences(false, false) }
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            model.toggle(NotificationKind.COLLECTION, true)
            model.refresh()
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
            permissions.status = NotificationAuthorization.AUTHORIZED
            model.refresh()
            assertFalse(store.saved.collectionEnabled)
        }

    @Test
    fun `미요청 토글은 시스템 요청 후 허용됐을 때 켜진다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.NOT_DETERMINED }
            val store = Store().apply { saved = NotificationPreferences(false, false) }
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            model.toggle(NotificationKind.COLLECTION, true)
            assertEquals(1, permissions.requests)
            assertEquals(0, permissions.settingsOpens)
            assertTrue(model.state.value.checked(NotificationKind.COLLECTION))
        }

    @Test
    fun `내부 기본값이 모두 ON이어도 설정에서 선택한 알림만 켜진다`() =
        runTest {
            NotificationKind.entries.forEach { selected ->
                val permissions = Permissions().apply { status = NotificationAuthorization.DENIED }
                val store = Store()
                val model = NotificationSettingsState(permissions, store)
                model.refresh()
                assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
                assertFalse(model.state.value.checked(NotificationKind.RANK_DROP))
                model.toggle(selected, true)
                // Opening Settings alone must not change saved preferences.
                assertEquals(NotificationPreferences(), store.saved)
                permissions.status = NotificationAuthorization.AUTHORIZED
                model.refresh()
                NotificationKind.entries.forEach { kind ->
                    assertEquals(kind == selected, model.state.value.checked(kind))
                    assertEquals(kind == selected, store.saved.enabled(kind))
                }
                val restarted = NotificationSettingsState(permissions, store)
                restarted.refresh()
                NotificationKind.entries.forEach { kind ->
                    assertEquals(kind == selected, restarted.state.value.checked(kind))
                }
            }
        }

    @Test
    fun `첫 실행에서 시스템 허용 후에는 기본값 모두 ON을 유지한다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.NOT_DETERMINED }
            val store = Store()
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            permissions.status = NotificationAuthorization.AUTHORIZED
            model.refresh()
            assertTrue(model.state.value.checked(NotificationKind.COLLECTION))
            assertTrue(model.state.value.checked(NotificationKind.RANK_DROP))
        }

    @Test
    fun `설정에서 허용하지 않으면 숨겨진 기존 설정을 변경하지 않는다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.DENIED }
            val store = Store()
            val model = NotificationSettingsState(permissions, store)
            model.refresh()
            model.toggle(NotificationKind.COLLECTION, true)
            model.refresh()
            assertEquals(NotificationPreferences(), store.saved)
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
            assertFalse(model.state.value.checked(NotificationKind.RANK_DROP))
        }

    @Test
    fun `저장 실패는 성공으로 표시하지 않고 재조회로 복구한다`() =
        runTest {
            val store = Store().apply { saved = NotificationPreferences(false, false) }
            val model = NotificationSettingsState(Permissions(), store)
            model.refresh()
            store.fail = true
            model.toggle(NotificationKind.COLLECTION, true)
            assertTrue(model.state.value.failed)
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
            store.fail = false
            model.refresh()
            assertFalse(model.state.value.failed)
        }

    @Test
    fun `권한 재조회 실패 시 이전 허용 상태를 현재처럼 표시하지 않는다`() =
        runTest {
            val permissions = Permissions()
            val model = NotificationSettingsState(permissions, Store())
            model.refresh()
            assertTrue(model.state.value.checked(NotificationKind.COLLECTION))
            permissions.fail = true
            model.refresh()
            assertTrue(model.state.value.failed)
            assertFalse(model.state.value.checked(NotificationKind.COLLECTION))
        }

    private class Permissions : NotificationPermissionController {
        var fail = false
        var status = NotificationAuthorization.AUTHORIZED
        var settingsOpens = 0
        var requests = 0

        override suspend fun authorization(): NotificationAuthorization {
            if (fail) error("OS query failed")
            return status
        }

        override suspend fun requestAuthorization() {
            requests++
            status = NotificationAuthorization.AUTHORIZED
        }

        override suspend fun openSettings(): Boolean {
            settingsOpens++
            return true
        }
    }

    private class Store : NotificationPreferencesStore {
        var saved = NotificationPreferences()
        var fail = false

        override suspend fun read() = saved

        override suspend fun update(
            kind: NotificationKind,
            enabled: Boolean,
        ): NotificationPreferences {
            if (fail) error("save failed")
            saved =
                when (kind) {
                    NotificationKind.COLLECTION -> saved.copy(collectionEnabled = enabled)
                    NotificationKind.RANK_DROP -> saved.copy(rankDropEnabled = enabled)
                }
            return saved
        }
    }
}
