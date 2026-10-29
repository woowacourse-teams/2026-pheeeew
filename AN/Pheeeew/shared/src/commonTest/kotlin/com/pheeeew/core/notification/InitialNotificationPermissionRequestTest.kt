package com.pheeeew.core.notification

import com.pheeeew.domain.repository.OnboardingStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InitialNotificationPermissionRequestTest {
    @Test
    fun `첫 요청을 닫고 앱을 다시 실행해도 자동 요청하지 않는다`() =
        runTest {
            val store = FakeStore()
            val controller = FakeController()
            InitialNotificationPermissionRequest(store).requestIfNeeded(controller)
            InitialNotificationPermissionRequest(store).requestIfNeeded(controller)
            assertEquals(1, controller.requests)
            assertTrue(store.handled)
        }

    @Test
    fun `이미 처리한 설치에서는 요청하지 않는다`() =
        runTest {
            val controller = FakeController()
            InitialNotificationPermissionRequest(FakeStore(handled = true)).requestIfNeeded(controller)
            assertEquals(0, controller.requests)
        }

    @Test
    fun `이미 허용되었거나 요청할 수 없어도 첫 실행 처리를 완료한다`() =
        runTest {
            val store = FakeStore()
            val controller = FakeController(canRequest = false)
            InitialNotificationPermissionRequest(store).requestIfNeeded(controller)
            controller.canRequest = true
            InitialNotificationPermissionRequest(store).requestIfNeeded(controller)
            assertEquals(0, controller.requests)
            assertTrue(store.handled)
        }

    @Test
    fun `시스템 요청 도중 UI가 취소되어도 다음 실행에서 반복하지 않는다`() =
        runTest {
            val store = FakeStore()
            val controller = FakeController(response = CompletableDeferred())
            val request = launch { InitialNotificationPermissionRequest(store).requestIfNeeded(controller) }
            runCurrent()
            assertEquals(1, controller.requests)
            request.cancel()
            request.join()
            InitialNotificationPermissionRequest(store).requestIfNeeded(controller)
            assertEquals(1, controller.requests)
        }

    private class FakeStore(
        var handled: Boolean = false,
    ) : OnboardingStore {
        override fun isCompleted() = false

        override fun complete() = Unit

        override fun resetCompletion() = Unit

        override fun isInitialNotificationPermissionHandled() = handled

        override fun completeInitialNotificationPermission() {
            handled = true
        }
    }

    private class FakeController(
        var canRequest: Boolean = true,
        val response: CompletableDeferred<Unit>? = null,
    ) : NotificationPermissionController {
        var requests = 0

        override suspend fun authorization() = NotificationAuthorization.NOT_DETERMINED

        override suspend fun canRequestAuthorization() = canRequest

        override suspend fun requestAuthorization() {
            requests++
            response?.await()
        }

        override suspend fun openSettings() = true
    }
}
