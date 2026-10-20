package com.pheeeew.core.di

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.network.NetworkTimeouts
import com.pheeeew.data.repository.RecordingAudioReader
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.AudioUploadObservation
import com.pheeeew.domain.repository.AudioUploadOutcome
import com.pheeeew.domain.repository.AudioUploadStage
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmotionRegistrationGraphTest {
    @Test
    fun `파일 실패는 제품 DI로 생성한 저장소의 FILE 단계에서 관측된다`() =
        runTest {
            withRepository(
                MockEngine {
                    error("Must not send HTTP")
                },
                RecordingAudioReader { null },
            ) { repository ->
                val observations = mutableListOf<AudioUploadObservation>()
                assertEquals(
                    EmotionRegistrationResult.AudioUnavailable,
                    repository.register(input()) { observations += it },
                )
                assertEquals(1, observations.size)
                assertEquals(AudioUploadStage.FILE, observations.single().failureStage)
                assertEquals(AudioUploadOutcome.FAILED, observations.single().outcome)
            }
        }

    @Test
    fun `업로드 URL 연결 실패는 제품 저장소의 URL_REQUEST 단계에서 처리한다`() =
        runTest {
            withRepository(MockEngine { throw IOException("Connection failed") }) { repository ->
                val observations = mutableListOf<AudioUploadObservation>()
                assertEquals(
                    EmotionRegistrationResult.AudioUploadFailed,
                    repository.register(input()) { observations += it },
                )
                assertEquals(AudioUploadStage.URL_REQUEST, observations.single().failureStage)
                assertEquals(AudioUploadOutcome.UNKNOWN, observations.single().outcome)
            }
        }

    @Test
    fun `연결 실패와 서버 오류는 제품 저장소의 동일한 불확실성 처리로 전달한다`() =
        runTest {
            var connectionFailure = true
            withRepository(
                MockEngine {
                    if (connectionFailure) {
                        connectionFailure = false
                        throw IOException("Connection failed")
                    }
                    respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders())
                },
            ) { repository ->
                assertEquals(EmotionRegistrationResult.Unavailable, repository.register(memo()))
                assertEquals(EmotionRegistrationResult.Unavailable, repository.register(memo()))
            }
        }

    @Test
    fun `signed PUT 실패는 실제 UPLOAD 단계에서 관측하고 인증 헤더를 보내지 않는다`() =
        runTest {
            val observations = mutableListOf<AudioUploadObservation>()
            var puts = 0
            withRepository(
                MockEngine { request ->
                    if (request.method == HttpMethod.Put) {
                        puts++
                        assertEquals(null, request.headers[HttpHeaders.Authorization])
                        assertEquals("storage.test", request.url.host)
                        respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders())
                    } else {
                        respond(uploadResponse(), headers = jsonHeaders())
                    }
                },
            ) { repository ->
                assertEquals(
                    EmotionRegistrationResult.AudioUploadFailed,
                    repository.register(input()) { observations += it },
                )
                assertEquals(1, puts)
                assertEquals(AudioUploadStage.UPLOAD, observations.single().failureStage)
                assertEquals(AudioUploadOutcome.UNKNOWN, observations.single().outcome)
            }
        }

    @Test
    fun `등록 재시도는 제품 저장소의 업로드 캐시와 동일한 요청 본문을 유지한다`() =
        runTest {
            var reads = 0
            var uploads = 0
            val bodies = mutableListOf<String>()
            val observations = mutableListOf<AudioUploadObservation>()
            withRepository(
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Put -> {
                            uploads++
                            respond("", HttpStatusCode.OK)
                        }

                        request.url.encodedPath == "/api/v1/audio-uploads" -> {
                            respond(uploadResponse(), headers = jsonHeaders())
                        }

                        else -> {
                            bodies += (request.body as TextContent).text
                            if (bodies.size == 1) {
                                respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders())
                            } else {
                                respond("{\"id\":42}", headers = jsonHeaders())
                            }
                        }
                    }
                },
                RecordingAudioReader {
                    reads++
                    byteArrayOf(1, 2, 3)
                },
            ) { repository ->
                val registration = input()
                assertEquals(
                    EmotionRegistrationResult.Unavailable,
                    repository.register(registration) { observations += it },
                )
                assertIs<EmotionRegistrationResult.Success>(repository.register(registration) { observations += it })
                assertEquals(1, reads)
                assertEquals(1, uploads)
                assertEquals(2, observations.size)
                assertTrue(observations.last().cacheReused)
                assertEquals(AudioUploadStage.NONE, observations.last().failureStage)
                assertEquals(2, bodies.size)
                assertEquals(bodies[0], bodies[1])
                val body = bodies.last()
                for (value in listOf(
                    registration.requestId,
                    "upload-1",
                    "group-1",
                    "\"anonymous\":true",
                    "\"latitude\":37.0",
                    "\"longitude\":127.0",
                )) {
                    assertTrue(value in body)
                }
            }
        }

    @Test
    fun `업로드 중 취소는 기존 callback으로 한 번 관측하고 등록하지 않는다`() =
        runTest {
            val reached = CompletableDeferred<Unit>()
            val observations = mutableListOf<AudioUploadObservation>()
            withRepository(
                MockEngine { request ->
                    if (request.method == HttpMethod.Put) {
                        reached.complete(Unit)
                        awaitCancellation()
                    } else {
                        respond(uploadResponse(), headers = jsonHeaders())
                    }
                },
            ) { repository ->
                val job = async { repository.register(input()) { observations += it } }
                reached.await()
                job.cancelAndJoin()
                assertTrue(job.isCancelled)
                assertEquals(1, observations.size)
                assertEquals(AudioUploadOutcome.CANCELLED, observations.single().outcome)
                assertEquals(AudioUploadStage.UPLOAD, observations.single().failureStage)
            }
        }

    @Test
    fun `파일 취소를 AudioUnavailable로 삼키지 않는다`() =
        runTest {
            withRepository(
                MockEngine {
                    error("Unexpected HTTP")
                },
                RecordingAudioReader { throw CancellationException("cancelled") },
            ) { repository ->
                val observations = mutableListOf<AudioUploadObservation>()
                val failure = runCatching { repository.register(input()) { observations += it } }.exceptionOrNull()
                assertIs<CancellationException>(failure)
                assertEquals(AudioUploadOutcome.CANCELLED, observations.single().outcome)
                assertEquals(AudioUploadStage.FILE, observations.single().failureStage)
            }
        }

    @Test
    fun `응답 없음도 제품 등록 처리의 제한 시간과 callback을 거친다`() =
        runTest {
            withRepository(
                MockEngine { awaitCancellation() },
                timeouts = NetworkTimeouts(100, 100, 100),
            ) { repository ->
                val observations = mutableListOf<AudioUploadObservation>()
                assertEquals(
                    EmotionRegistrationResult.AudioUploadFailed,
                    repository.register(input()) { observations += it },
                )
                assertEquals(AudioUploadStage.URL_REQUEST, observations.single().failureStage)
                assertTrue(observations.single().durationMs >= 80)
            }
        }

    private suspend fun withRepository(
        engine: HttpClientEngine,
        reader: RecordingAudioReader = RecordingAudioReader { byteArrayOf(1, 2, 3) },
        timeouts: NetworkTimeouts = NetworkTimeouts(),
        block: suspend (EmotionRegistrationRepository) -> Unit,
    ) {
        val services =
            object : AppServiceFactory() {
                override fun createApiEngine(): HttpClientEngine = engine

                override fun createConnectivityObserver(): ConnectivityObserver = error("Not used")

                override fun createRecordingAudioReader(): RecordingAudioReader = reader
            }
        val client = services.createApiClient(ApiConfig("https://api.test", timeouts), { AccessToken("test-token") })
        try {
            val graph = createGraphFactory<RegistrationTestGraph.Factory>().create(client, services)
            block(graph.repository)
        } finally {
            client.close()
        }
    }

    private fun input() =
        EmotionRegistration(
            "request-1",
            EmotionState.ANGRY,
            GeoCoordinate(37.0, 127.0),
            0.0,
            "group-1",
            EmotionRegistrationContent.Audio("test-recording"),
            anonymous = true,
        )

    private fun memo() = input().copy(content = EmotionRegistrationContent.Memo("memo"))

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun uploadResponse() =
        """{"uploadId":"upload-1","uploadUrl":"https://storage.test/audio","expiresAt":"2099-01-01T00:00:00Z","headers":{"Content-Type":["audio/mp4"],"Content-Length":["3"]}}"""
}

/** Uses the same EmotionBindings and constructor injection as the product UI graphs. */
@DependencyGraph(ActivityRetainedScope::class)
internal interface RegistrationTestGraph : EmotionBindings {
    val repository: EmotionRegistrationRepository

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides client: ApiClient,
            @Provides services: AppServiceFactory,
        ): RegistrationTestGraph
    }

    @Provides fun requests(client: ApiClient): ApiRequestExecutor = client.requests

    @Provides fun monitoring(client: ApiClient): Monitoring = client.monitoring
}
