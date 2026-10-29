package com.pheeeew.developertools.data.simulation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DebugNetworkEngineTest {
    @AfterTest
    fun reset() = ErrorSimulation.clear()

    @Test
    fun `오프라인은 여러 조회와 쓰기 요청을 실제 엔진 호출 전에 차단한다`() =
        runTest {
            ErrorSimulation.enableDeveloperTools()
            ErrorSimulation.setOffline(true)
            var sent = 0
            val engine =
                DebugNetworkEngine(
                    MockEngine {
                        sent++
                        respond("ok")
                    },
                    "https://example.test",
                )
            val client = HttpClient(engine)
            try {
                for (path in listOf("/api/v4/emotions/map", "/api/v4/emotions/map/regions", "/api/v4/emotions/1")) {
                    assertFailsWith<IOException> { client.get("https://example.test$path") }
                }
                assertFailsWith<IOException> { client.post("https://example.test/api/v4/emotions") }
                assertFailsWith<IOException> { client.put("https://storage.test/recording") }
                assertEquals(0, sent)
                ErrorSimulation.setOffline(false)
                assertEquals("ok", client.get("https://example.test/api/v4/emotions/map").bodyAsText())
                assertEquals(1, sent)
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `다음 1회 실패는 해당 경로에만 적용되고 다른 조건은 유지된다`() =
        runTest {
            ErrorSimulation.setMode(ErrorSimulationScenario.MapPins, ErrorSimulationMode.NEXT)
            ErrorSimulation.setMode(ErrorSimulationScenario.RegionClusters, ErrorSimulationMode.ALWAYS)
            var sent = 0
            val engine =
                DebugNetworkEngine(
                    MockEngine {
                        sent++
                        respond("ok")
                    },
                    "https://example.test",
                )
            val client = HttpClient(engine)
            try {
                assertFailsWith<IOException> { client.get("https://example.test/api/v4/emotions/map") }
                assertEquals("ok", client.get("https://example.test/api/v4/emotions/map").bodyAsText())
                repeat(2) {
                    assertFailsWith<IOException> {
                        client.get(
                            "https://example.test/api/v4/emotions/map/regions",
                        )
                    }
                }
                assertEquals(1, sent)
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `상세 접근 불가는 실제 요청 없이 기존 404 처리 경로로 전달한다`() =
        runTest {
            ErrorSimulation.setMode(ErrorSimulationScenario.EmotionDetailNotFound, ErrorSimulationMode.NEXT)
            val engine =
                DebugNetworkEngine(
                    MockEngine { error("A simulated response must not reach the server") },
                    "https://example.test",
                )
            val client = HttpClient(engine)
            try {
                assertEquals(HttpStatusCode.NotFound, client.get("https://example.test/api/v4/emotions/1").status)
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `추가 지연 이후 정상 응답을 전달한다`() =
        runTest {
            ErrorSimulation.enableDeveloperTools()
            ErrorSimulation.setNetworkProfile(50, false)
            val engine = DebugNetworkEngine(MockEngine { respond("ok") })
            val client = HttpClient(engine) { install(HttpTimeout) { requestTimeoutMillis = 2_000 } }
            try {
                assertEquals("ok", client.get("https://example.test/api/v4/emotions/map").bodyAsText())
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `응답 없음과 제한 시간을 넘는 지연은 기존 타임아웃으로 실패하고 전송하지 않는다`() =
        runTest {
            ErrorSimulation.enableDeveloperTools()
            ErrorSimulation.setNetworkProfile(0, true)
            var sent = 0
            val engine =
                DebugNetworkEngine(
                    MockEngine {
                        sent++
                        respond("ok")
                    },
                    "https://example.test",
                )
            val client = HttpClient(engine) { install(HttpTimeout) { requestTimeoutMillis = 100 } }
            try {
                assertFailsWith<HttpRequestTimeoutException> { client.post("https://example.test/write") }
                ErrorSimulation.setNetworkProfile(5_000, false)
                assertFailsWith<HttpRequestTimeoutException> { client.post("https://example.test/write") }
                assertEquals(0, sent)
            } finally {
                client.close()
                engine.close()
            }
        }

    @Test
    fun `지연 중 취소한 요청은 해제 후에도 전송되지 않는다`() =
        runTest {
            ErrorSimulation.enableDeveloperTools()
            ErrorSimulation.setNetworkProfile(5_000, false)
            var sent = 0
            val engine =
                DebugNetworkEngine(
                    MockEngine {
                        sent++
                        respond("ok")
                    },
                    "https://example.test",
                )
            val client = HttpClient(engine)
            try {
                val request = async { client.get("https://example.test/api/v4/emotions/map") }
                delay(20)
                request.cancel()
                request.join()
                ErrorSimulation.clear()
                assertEquals(0, sent)
            } finally {
                client.close()
                engine.close()
            }
        }
}
