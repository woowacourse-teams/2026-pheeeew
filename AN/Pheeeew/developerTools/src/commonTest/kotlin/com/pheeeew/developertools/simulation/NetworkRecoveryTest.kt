package com.pheeeew.developertools.simulation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkRecoveryTest {
    @AfterTest
    fun reset() = ErrorSimulation.clear()

    @Test
    fun `자동 복구는 같은 관찰자를 유지하고 실제 오프라인을 성공으로 바꾸지 않는다`() =
        runTest {
            val platform = TestPlatformFactory()
            val observer = DebugAppServiceFactory(platform).createConnectivityObserver()
            val connected = mutableListOf<Boolean>()
            backgroundScope.launch { observer.isConnected.collect { connected += it } }
            val controller = NetworkRecoveryController(backgroundScope)
            runCurrent()
            ErrorSimulation.setLocationMode(LocationSimulationMode.FIXED)
            ErrorSimulation.setNetworkProfile(2_000, true)
            assertTrue(controller.start(5_000))
            runCurrent()
            assertFalse(connected.last())
            assertEquals(0L, ErrorSimulation.state.value.networkDelayMillis)
            assertFalse(ErrorSimulation.state.value.networkNeverResponds)
            assertEquals(LocationSimulationMode.FIXED, ErrorSimulation.state.value.location.mode)
            advanceTimeBy(4_999)
            runCurrent()
            assertTrue(ErrorSimulation.state.value.offline)
            platform.connected.value = false
            advanceTimeBy(1)
            runCurrent()
            assertFalse(ErrorSimulation.state.value.offline)
            assertFalse(connected.last())
            assertNull(controller.run.value)
            platform.connected.value = true
            runCurrent()
            assertTrue(connected.last())
        }

    @Test
    fun `수동 복구와 중복 실행 및 개별 오류 조건을 구분한다`() =
        runTest {
            val controller = NetworkRecoveryController(backgroundScope)
            ErrorSimulation.setMode(ErrorSimulationScenario.MapPins, ErrorSimulationMode.ALWAYS)
            assertFalse(controller.start(null))
            ErrorSimulation.clear()
            assertTrue(controller.start(null))
            assertFalse(controller.start(5_000))
            advanceTimeBy(60_000)
            assertTrue(ErrorSimulation.state.value.offline)
            controller.restore()
            assertFalse(ErrorSimulation.state.value.offline)
            assertNull(controller.run.value)
        }

    @Test
    fun `조건 변경과 초기화 뒤 오래된 자동 복구가 최신 조건을 덮어쓰지 않는다`() =
        runTest {
            val controller = NetworkRecoveryController(backgroundScope)
            controller.start(5_000)
            ErrorSimulation.setNetworkProfile(2_000, false)
            runCurrent()
            assertNull(controller.run.value)
            advanceTimeBy(5_000)
            assertTrue(ErrorSimulation.state.value.offline)
            assertEquals(2_000L, ErrorSimulation.state.value.networkDelayMillis)
            controller.start(5_000)
            ErrorSimulation.clear()
            ErrorSimulation.setNetworkProfile(500, false)
            runCurrent()
            advanceTimeBy(5_000)
            assertEquals(500L, ErrorSimulation.state.value.networkDelayMillis)
            assertFalse(ErrorSimulation.state.value.offline)
            assertNull(controller.run.value)
            controller.start(5_000)
            controller.stop()
            ErrorSimulation.replaceSettings(ErrorSimulation.defaultState())
            ErrorSimulation.setNetworkProfile(500, false)
            advanceTimeBy(5_000)
            runCurrent()
            assertFalse(ErrorSimulation.state.value.offline)
            assertEquals(500L, ErrorSimulation.state.value.networkDelayMillis)
        }

    @Test
    fun `이미 전송한 요청은 유지하고 이후 요청만 차단한 뒤 실제 전송으로 복귀한다`() =
        runTest {
            val reached = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Unit>()
            val engine =
                DebugNetworkEngine(
                    MockEngine {
                        reached.complete(Unit)
                        response.await()
                        respond("ok")
                    },
                )
            val client = HttpClient(engine)
            val controller = NetworkRecoveryController(backgroundScope)
            try {
                val request = async { client.get("https://api.test/pins") }
                reached.await()
                controller.start(null)
                assertTrue(request.isActive)
                response.complete(Unit)
                assertEquals(HttpStatusCode.OK, request.await().status)
                assertTrue(runCatching { client.get("https://api.test/pins") }.isFailure)
                controller.restore()
                assertEquals(HttpStatusCode.OK, client.get("https://api.test/pins").status)
            } finally {
                controller.stop()
                client.close()
                engine.close()
            }
        }
}
