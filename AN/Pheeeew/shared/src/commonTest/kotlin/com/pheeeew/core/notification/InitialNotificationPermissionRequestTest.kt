package com.pheeeew.core.notification

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InitialNotificationPermissionRequestTest {
    @Test
    fun `미정 상태이면 같은 UI 진입에서 한 번만 요청한다`() =
        runTest {
            val controller = FakeController()
            val request = InitialNotificationPermissionRequest()
            request.requestIfNeeded(controller)
            request.requestIfNeeded(controller)
            assertEquals(1, controller.requests)
        }

    @Test
    fun `다음 앱 진입에서도 미정 상태이면 다시 요청한다`() =
        runTest {
            val controller = FakeController()
            InitialNotificationPermissionRequest().requestIfNeeded(controller)
            InitialNotificationPermissionRequest().requestIfNeeded(controller)
            assertEquals(2, controller.requests)
        }

    @Test
    fun `거부 상태가 재요청 가능해도 자동 요청하지 않는다`() =
        runTest {
            val controller = FakeController(authorization = NotificationAuthorization.DENIED)
            InitialNotificationPermissionRequest().requestIfNeeded(controller)
            assertEquals(0, controller.requests)
        }

    @Test
    fun `미정 이외의 상태에서는 자동 요청하지 않는다`() =
        runTest {
            NotificationAuthorization.entries.filter { it != NotificationAuthorization.NOT_DETERMINED }.forEach {
                val controller = FakeController(authorization = it)
                InitialNotificationPermissionRequest().requestIfNeeded(controller)
                assertEquals(0, controller.requests, it.name)
            }
        }

    @Test
    fun `미정이어도 플랫폼이 요청할 수 없으면 요청하지 않는다`() =
        runTest {
            val controller = FakeController(canRequest = false)
            InitialNotificationPermissionRequest().requestIfNeeded(controller)
            assertEquals(0, controller.requests)
        }

    @Test
    fun `시스템 요청 도중 UI가 취소되어도 같은 진입에서 중복 요청하지 않는다`() =
        runTest {
            val controller = FakeController(response = CompletableDeferred())
            val requester = InitialNotificationPermissionRequest()
            val request = launch { requester.requestIfNeeded(controller) }
            runCurrent()
            assertEquals(1, controller.requests)
            request.cancel()
            request.join()
            requester.requestIfNeeded(controller)
            assertEquals(1, controller.requests)
        }

    private class FakeController(
        val authorization: NotificationAuthorization = NotificationAuthorization.NOT_DETERMINED,
        val canRequest: Boolean = true,
        val response: CompletableDeferred<Unit>? = null,
    ) : NotificationPermissionController {
        var requests = 0

        override suspend fun authorization() = authorization

        override suspend fun canRequestAuthorization() = canRequest

        override suspend fun requestAuthorization() {
            requests++
            response?.await()
        }

        override suspend fun openSettings() = true
    }
}
