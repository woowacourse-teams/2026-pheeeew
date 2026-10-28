package com.pheeeew.feature.screens.settings

import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationPermissionController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionStateTest {
    @Test
    fun `요청 가능 여부 조회 실패를 화면 오류로 처리하고 재시도한다`() =
        runTest {
            val controller = FakeController().apply { current = NotificationAuthorization.NOT_DETERMINED }
            val state = NotificationPermissionState(controller)
            state.refresh()
            controller.failCapability = true
            state.requestPermission()
            assertTrue(state.state.value.failed)
            assertFalse(state.state.value.loading)
            assertEquals(0, controller.requests)
            controller.failCapability = false
            state.requestPermission()
            assertEquals(NotificationAuthorization.AUTHORIZED, state.state.value.authorization)
        }

    @Test
    fun `설정 복귀 시 다시 조회해 거부에서 허용으로 변경된 상태를 반영한다`() =
        runTest {
            val controller = FakeController()
            val state = NotificationPermissionState(controller)
            state.refresh()
            assertEquals(NotificationAuthorization.DENIED, state.state.value.authorization)
            controller.current = NotificationAuthorization.AUTHORIZED
            state.refresh()
            assertEquals(NotificationAuthorization.AUTHORIZED, state.state.value.authorization)
        }

    @Test
    fun `거부한 권한은 재요청하지 않고 설정 이동을 지원한다`() =
        runTest {
            val controller = FakeController()
            val state = NotificationPermissionState(controller)
            state.refresh()
            state.requestPermission()
            assertEquals(0, controller.requests)
            assertTrue(state.openSettings())
        }

    @Test
    fun `플랫폼이 재요청을 허용하면 거부 상태에서도 시스템 요청을 실행한다`() =
        runTest {
            val controller = FakeController().apply { requestableAfterDenial = true }
            val state = NotificationPermissionState(controller)
            state.refresh()
            state.requestPermission()
            assertEquals(1, controller.requests)
            assertEquals(NotificationAuthorization.AUTHORIZED, state.state.value.authorization)
        }

    @Test
    fun `미요청 상태에서만 시스템 요청을 실행하고 결과를 다시 조회한다`() =
        runTest {
            val controller = FakeController().apply { current = NotificationAuthorization.NOT_DETERMINED }
            val state = NotificationPermissionState(controller)
            state.refresh()
            state.requestPermission()
            assertEquals(1, controller.requests)
            assertEquals(NotificationAuthorization.AUTHORIZED, state.state.value.authorization)
            assertFalse(state.state.value.loading)
        }

    @Test
    fun `조회 실패 시 이전 허용 상태를 현재 상태처럼 표시하지 않는다`() =
        runTest {
            val controller = FakeController().apply { current = NotificationAuthorization.AUTHORIZED }
            val state = NotificationPermissionState(controller)
            state.refresh()
            controller.fail = true
            state.refresh()
            assertNull(state.state.value.authorization)
            assertTrue(state.state.value.failed)
            controller.fail = false
            state.refresh()
            assertFalse(state.state.value.failed)
        }

    @Test
    fun `늦게 완료된 이전 조회가 최신 권한을 덮어쓰지 않는다`() =
        runTest {
            val oldRead = CompletableDeferred<NotificationAuthorization>()
            val controller = FakeController().apply { nextRead = oldRead }
            val state = NotificationPermissionState(controller)
            val first = launch { state.refresh() }
            runCurrent()
            controller.current = NotificationAuthorization.AUTHORIZED
            state.refresh()
            oldRead.complete(NotificationAuthorization.DENIED)
            first.join()
            assertEquals(NotificationAuthorization.AUTHORIZED, state.state.value.authorization)
        }

    private class FakeController : NotificationPermissionController {
        var current = NotificationAuthorization.DENIED
        var requests = 0
        var fail = false
        var failCapability = false
        var requestableAfterDenial = false
        var nextRead: CompletableDeferred<NotificationAuthorization>? = null

        override suspend fun authorization(): NotificationAuthorization {
            if (fail) error("unavailable")
            val deferred = nextRead
            nextRead = null
            return deferred?.await() ?: current
        }

        override suspend fun requestAuthorization() {
            requests++
            current = NotificationAuthorization.AUTHORIZED
        }

        override suspend fun canRequestAuthorization(): Boolean {
            if (failCapability) error("capability unavailable")
            return requestableAfterDenial || authorization() == NotificationAuthorization.NOT_DETERMINED
        }

        override suspend fun openSettings(): Boolean = true
    }
}
