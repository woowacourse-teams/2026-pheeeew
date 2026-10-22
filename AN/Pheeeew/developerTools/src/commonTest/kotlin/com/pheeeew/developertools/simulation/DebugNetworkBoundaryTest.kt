package com.pheeeew.developertools.simulation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebugNetworkBoundaryTest {
    @AfterTest
    fun reset() = ErrorSimulation.clear()

    @Test
    fun `업로드 URL 실패 입력은 뒤 단계의 등록 실패 조건을 소비하지 않는다`() =
        runTest {
            ErrorSimulation.setMode(ErrorSimulationScenario.AudioUploadFailed, ErrorSimulationMode.NEXT)
            ErrorSimulation.setMode(ErrorSimulationScenario.EmotionRegistrationUnavailable, ErrorSimulationMode.NEXT)
            val engine = DebugNetworkEngine(MockEngine { error("Must not send") }, "https://api.test")
            val client = HttpClient(engine)
            try {
                assertTrue(runCatching { client.post("https://api.test/api/v1/audio-uploads") }.isFailure)
                assertEquals(null, ErrorSimulation.mode(ErrorSimulationScenario.AudioUploadFailed))
                assertEquals(
                    ErrorSimulationMode.NEXT,
                    ErrorSimulation.mode(ErrorSimulationScenario.EmotionRegistrationUnavailable),
                )
                assertEquals(HttpStatusCode.ServiceUnavailable, client.post("https://api.test/api/v3/emotions").status)
                assertEquals(null, ErrorSimulation.mode(ErrorSimulationScenario.EmotionRegistrationUnavailable))
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `API origin과 method가 다른 요청은 개별 오류를 소비하지 않는다`() =
        runTest {
            ErrorSimulation.setMode(ErrorSimulationScenario.MapPins, ErrorSimulationMode.NEXT)
            val engine = DebugNetworkEngine(MockEngine { respond("ok") }, "https://api.test")
            val client = HttpClient(engine)
            try {
                assertEquals(HttpStatusCode.OK, client.get("https://other.test/api/v2/emotions/map").status)
                assertEquals(HttpStatusCode.OK, client.get("https://api.test:444/api/v2/emotions/map").status)
                assertEquals(HttpStatusCode.OK, client.put("https://api.test/api/v2/emotions/map").status)
                assertEquals(ErrorSimulationMode.NEXT, ErrorSimulation.mode(ErrorSimulationScenario.MapPins))
                assertTrue(runCatching { client.get("https://api.test/api/v2/emotions/map") }.isFailure)
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `동시 요청에서도 다음 1회 조건은 한 번만 소비한다`() =
        runTest {
            ErrorSimulation.setMode(ErrorSimulationScenario.MapPins, ErrorSimulationMode.NEXT)
            val failures =
                (1..64)
                    .map {
                        async(Dispatchers.Default) { ErrorSimulation.shouldFail(ErrorSimulationScenario.MapPins) }
                    }.awaitAll()
            assertEquals(1, failures.count { it })
        }

    @Test
    fun `같은 연결 관찰자는 조건 해제 시 실제 연결을 따르며 실제 오프라인 성공을 강제하지 않는다`() =
        runTest {
            val platform = TestPlatformFactory()
            val actual = DebugAppServiceFactory(platform).createConnectivityObserver()
            val values = mutableListOf<Boolean>()
            backgroundScope.launch { actual.isConnected.collect { values += it } }
            runCurrent()
            assertTrue(values.last())
            ErrorSimulation.enableDeveloperTools()
            ErrorSimulation.setOffline(true)
            runCurrent()
            assertFalse(values.last())
            platform.connected.value = false
            ErrorSimulation.setOffline(false)
            runCurrent()
            assertFalse(values.last())
            platform.connected.value = true
            runCurrent()
            assertTrue(values.last())
        }

    @Test
    fun `signed PUT 전 지연 중 오프라인 전환은 실제 전송을 막고 조건 초기화 후 복귀한다`() =
        runTest {
            ErrorSimulation.enableDeveloperTools()
            ErrorSimulation.setNetworkProfile(5_000, false)
            var sent = 0
            val engine =
                DebugNetworkEngine(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Put, request.method)
                        sent++
                        respond("")
                    },
                )
            val client = HttpClient(engine)
            try {
                val request = async { runCatching { client.put("https://storage.test/audio") } }
                delay(20)
                ErrorSimulation.setOffline(true)
                assertTrue(request.await().isFailure)
                assertEquals(0, sent)
                ErrorSimulation.clear()
                assertEquals(HttpStatusCode.OK, client.put("https://storage.test/audio").status)
                assertEquals(1, sent)
            } finally {
                client.close()
                engine.close()
            }
        }
}
