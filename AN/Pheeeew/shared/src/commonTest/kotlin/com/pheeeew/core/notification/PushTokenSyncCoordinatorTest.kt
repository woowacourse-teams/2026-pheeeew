package com.pheeeew.core.notification

import com.pheeeew.core.network.ApiError
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenSyncCoordinatorTest {
    @Test
    fun `foreground Worker 재시도는 소진된 이전 결과 대신 새 전송을 시작한다`() =
        runTest {
            val f = fixture(saved = "token")
            f.api.result = failure(503)
            f.activate()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(PushTokenSyncState.FAILED, f.sync.state.value)
            f.api.result = ApiResult.Success(Unit)
            val request = f.sync.requestSynchronization()
            val result = async { f.sync.awaitSynchronization(request) }
            runCurrent()
            assertEquals(PushTokenBackgroundSyncResult.SYNCHRONIZED, result.await())
            assertEquals(4, f.api.operations.size)
        }

    @Test
    fun `새 토큰 요청은 이전 성공을 완료 결과로 사용하지 않는다`() =
        runTest {
            val f = fixture(saved = "old")
            f.activate()
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            f.api.gate = gate
            f.sync.tokenReceived("new")
            val request = f.sync.requestSynchronization()
            val result = async { f.sync.awaitSynchronization(request) }
            runCurrent()
            assertFalse(result.isCompleted)
            assertEquals(listOf("PUT:old", "PUT:new"), f.api.operations)
            gate.complete(Unit)
            runCurrent()
            assertEquals(PushTokenBackgroundSyncResult.SYNCHRONIZED, result.await())
        }

    @Test
    fun `인증 전 SDK 토큰은 보관하고 인증 완료 후에만 등록한다`() =
        runTest {
            val f = fixture()
            f.sync.tokenReceived("token-a")
            f.sync.onForeground()
            f.sync.connectivityChanged(true)
            runCurrent()
            assertEquals("token-a", f.store.saved)
            assertTrue(f.api.operations.isEmpty())
            f.sync.sessionChanged(1)
            runCurrent()
            assertEquals(listOf("PUT:token-a"), f.api.operations)
            assertEquals(PushTokenSyncState.SYNCED, f.sync.state.value)
        }

    @Test
    fun `같은 토큰도 매번 앱 활성화 때 다시 등록한다`() =
        runTest {
            val f = fixture(saved = "same")
            f.activate()
            runCurrent()
            f.sync.onBackground()
            runCurrent()
            f.sync.onForeground()
            runCurrent()
            assertEquals(listOf("PUT:same", "PUT:same"), f.api.operations)
        }

    @Test
    fun `권한 거부는 토큰이 없어도 DELETE하고 재허용은 보관한 토큰을 등록한다`() =
        runTest {
            val f = fixture()
            f.permissions.status = NotificationAuthorization.DENIED
            f.activate()
            runCurrent()
            assertEquals(listOf("DELETE"), f.api.operations)
            f.sync.tokenReceived("token-b")
            runCurrent()
            assertEquals("DELETE", f.api.operations.last())
            f.permissions.status = NotificationAuthorization.AUTHORIZED
            f.sync.permissionChanged()
            runCurrent()
            assertEquals("PUT:token-b", f.api.operations.last())
        }

    @Test
    fun `미요청과 조회 불가 상태는 토큰을 등록하거나 삭제하지 않는다`() =
        runTest {
            val f = fixture(saved = "token")
            f.permissions.status = NotificationAuthorization.NOT_DETERMINED
            f.activate()
            runCurrent()
            f.permissions.status = NotificationAuthorization.UNKNOWN
            f.sync.permissionChanged()
            runCurrent()
            assertTrue(f.api.operations.isEmpty())
            f.permissions.status = NotificationAuthorization.AUTHORIZED
            f.sync.permissionChanged()
            runCurrent()
            assertEquals(listOf("PUT:token"), f.api.operations)
        }

    @Test
    fun `토큰 발급을 기다린 후 SDK 콜백으로 등록한다`() =
        runTest {
            val f = fixture()
            f.activate()
            runCurrent()
            assertTrue(f.api.operations.isEmpty())
            f.sync.tokenReceived("latest")
            runCurrent()
            assertEquals(listOf("PUT:latest"), f.api.operations)
        }

    @Test
    fun `백그라운드 SDK 토큰 갱신은 저장하고 동기화한다`() =
        runTest {
            val f = fixture()
            f.sync.sessionChanged(3)
            f.sync.connectivityChanged(true)
            f.sync.tokenReceived("background-token")
            runCurrent()

            assertEquals("background-token", f.store.saved)
            assertEquals(listOf("PUT:background-token"), f.api.operations)
            assertEquals(listOf(3L), f.api.generations)
        }

    @Test
    fun `WorkManager 호출은 같은 조정자에서 완료 결과를 기다린다`() =
        runTest {
            val f = fixture(saved = "stored-token")
            f.sync.sessionChanged(4)
            f.sync.connectivityChanged(true)
            val result = async { f.sync.synchronizeInBackground() }
            runCurrent()

            assertEquals(PushTokenBackgroundSyncResult.SYNCHRONIZED, result.await())
            assertEquals(listOf("PUT:stored-token"), f.api.operations)
            assertEquals(listOf(4L), f.api.generations)
        }

    @Test
    fun `여러 토큰이 도착하면 최신 값만 저장하고 전송한다`() =
        runTest {
            val f = fixture()
            f.activate()
            f.sync.tokenReceived("old")
            f.sync.tokenReceived("new")
            runCurrent()
            assertEquals("new", f.store.saved)
            assertEquals(listOf("PUT:new"), f.api.operations)
        }

    @Test
    fun `네트워크 복구 시 실패 상태 대신 최신 토큰을 전송한다`() =
        runTest {
            val f = fixture(saved = "old")
            f.sync.onForeground()
            f.sync.sessionChanged(1)
            runCurrent()
            f.sync.tokenReceived("new")
            runCurrent()
            assertTrue(f.api.operations.isEmpty())
            f.sync.connectivityChanged(true)
            runCurrent()
            assertEquals(listOf("PUT:new"), f.api.operations)
        }

    @Test
    fun `전송 실패 후 지연 재시도하고 성공하면 중단한다`() =
        runTest {
            val f = fixture(saved = "token")
            f.api.result =
                ApiResult.Failure(
                    NetworkFailure.Transport(
                        com.pheeeew.core.network.TransportFailureReason.CONNECTION,
                        MutationCertainty.UNKNOWN,
                    ),
                )
            f.activate()
            runCurrent()
            assertEquals(1, f.api.operations.size)
            f.api.result = ApiResult.Success(Unit)
            advanceTimeBy(100)
            runCurrent()
            assertEquals(2, f.api.operations.size)
            assertEquals(PushTokenSyncState.SYNCED, f.sync.state.value)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(2, f.api.operations.size)
        }

    @Test
    fun `실패한 PUT 재시도 전에 권한이 꺼지면 DELETE로 전환한다`() =
        runTest {
            val f = fixture(saved = "token")
            f.api.result = failure(503)
            f.activate()
            runCurrent()
            f.permissions.status = NotificationAuthorization.DENIED
            f.api.result = ApiResult.Success(Unit)
            f.sync.permissionChanged()
            runCurrent()
            assertEquals(listOf("PUT:token", "DELETE"), f.api.operations)
        }

    @Test
    fun `400 오류는 무한 재시도하지 않는다`() =
        runTest {
            val f = fixture(saved = "token")
            f.api.result = failure(400)
            f.activate()
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, f.api.operations.size)
            assertEquals(PushTokenSyncState.FAILED, f.sync.state.value)
        }

    @Test
    fun `재시도 횟수는 제한하고 다음 활성화에서는 다시 시도한다`() =
        runTest {
            val f = fixture(saved = "token")
            f.api.result = failure(503)
            f.activate()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(3, f.api.operations.size)
            assertEquals(PushTokenSyncState.FAILED, f.sync.state.value)
            f.api.result = ApiResult.Success(Unit)
            f.sync.onForeground()
            runCurrent()
            assertEquals(4, f.api.operations.size)
        }

    @Test
    fun `백그라운드 전환은 재시도를 멈추고 복귀 후 재개한다`() =
        runTest {
            val f = fixture(saved = "token")
            f.api.result = failure(503)
            f.activate()
            runCurrent()
            f.sync.onBackground()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, f.api.operations.size)
            f.api.result = ApiResult.Success(Unit)
            f.sync.onForeground()
            runCurrent()
            assertEquals(2, f.api.operations.size)
        }

    @Test
    fun `기기 인증 세대가 변경되면 같은 FCM 토큰도 다시 연결한다`() =
        runTest {
            val f = fixture(saved = "token")
            f.activate()
            runCurrent()
            f.sync.sessionChanged(null)
            runCurrent()
            f.sync.sessionChanged(2)
            runCurrent()
            assertEquals(listOf("PUT:token", "PUT:token"), f.api.operations)
            assertEquals(listOf(1L, 2L), f.api.generations)
        }

    @Test
    fun `이전 요청 성공이 최신 토큰의 등록 완료로 처리되지 않는다`() =
        runTest {
            val f = fixture(saved = "old")
            val gate = CompletableDeferred<Unit>()
            f.api.gate = gate
            f.activate()
            runCurrent()
            f.sync.tokenReceived("new")
            runCurrent()
            assertEquals(listOf("PUT:old"), f.api.operations)
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("PUT:old", "PUT:new"), f.api.operations)
            assertEquals("new", f.store.saved)
            assertEquals(PushTokenSyncState.SYNCED, f.sync.state.value)
        }

    @Test
    fun `저장 실패 시 재시도하고 새 프로세스는 저장한 토큰으로 복구한다`() =
        runTest {
            val f = fixture()
            f.store.failWrite = true
            f.activate()
            f.sync.tokenReceived("persisted")
            runCurrent()
            assertTrue(f.api.operations.isEmpty())
            f.store.failWrite = false
            advanceTimeBy(100)
            runCurrent()
            assertEquals("persisted", f.store.saved)
            val restarted = fixture(saved = f.store.saved)
            restarted.activate()
            runCurrent()
            assertEquals(listOf("PUT:persisted"), restarted.api.operations)
        }

    private fun TestScope.fixture(saved: String? = null): Fixture {
        val permissions = Permissions()
        val api = Repository()
        val store = Store(saved)
        return Fixture(
            PushTokenSyncCoordinator(permissions, api, store, backgroundScope, listOf(100, 200)),
            permissions,
            api,
            store,
        )
    }

    private fun failure(status: Int) =
        ApiResult.Failure(
            NetworkFailure.HttpStatus(
                status,
                ApiError("TEST", null),
                mutationCertainty = MutationCertainty.NOT_APPLIED,
            ),
        )

    private class Fixture(
        val sync: PushTokenSyncCoordinator,
        val permissions: Permissions,
        val api: Repository,
        val store: Store,
    ) {
        fun activate() {
            sync.sessionChanged(1)
            sync.connectivityChanged(true)
            sync.onForeground()
        }
    }

    private class Store(
        var saved: String?,
    ) : PushTokenStore {
        var failWrite = false

        override suspend fun read() = saved

        override suspend fun write(token: String) {
            if (failWrite) error("Storage unavailable")
            saved = token
        }
    }

    private class Permissions : NotificationPermissionController {
        var status = NotificationAuthorization.AUTHORIZED

        override suspend fun authorization() = status

        override suspend fun requestAuthorization() = Unit

        override suspend fun openSettings() = true
    }

    private class Repository : PushTokenRepository {
        val operations = mutableListOf<String>()
        val generations = mutableListOf<Long>()
        var result: ApiResult<Unit> = ApiResult.Success(Unit)
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun register(
            token: String,
            expectedSessionGeneration: Long,
        ): ApiResult<Unit> {
            operations += "PUT:$token"
            generations += expectedSessionGeneration
            gate?.let {
                gate = null
                it.await()
            }
            return result
        }

        override suspend fun unregister(expectedSessionGeneration: Long): ApiResult<Unit> {
            operations += "DELETE"
            generations += expectedSessionGeneration
            return result
        }
    }
}
