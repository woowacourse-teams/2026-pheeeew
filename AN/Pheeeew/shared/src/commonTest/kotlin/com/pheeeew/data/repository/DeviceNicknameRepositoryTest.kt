package com.pheeeew.data.repository

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.SessionAccessException
import com.pheeeew.core.network.SessionFailureDetails
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.api.DeviceNicknameApi
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceNicknameRepositoryTest {
    @Test
    fun `미조회와 서버에서 확인한 미설정 및 조회 실패를 구분한다`() =
        runTest {
            var fail = true
            withRepository(
                MockEngine {
                    if (fail) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(
                            """{"nickname":null}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                },
            ) { repository ->
                assertFalse(repository.nicknameState.value.isConfirmed)
                assertFalse(repository.nicknameState.value.lookupFailed)
                assertEquals(DeviceNicknameLookup.Failed(DeviceSessionFailureKind.SERVER), repository.findMyNickname())
                assertFalse(repository.nicknameState.value.isConfirmed)
                assertTrue(repository.nicknameState.value.lookupFailed)
                fail = false
                assertEquals(DeviceNicknameLookup.Found(null), repository.findMyNickname())
                assertTrue(repository.nicknameState.value.isConfirmed)
                assertFalse(repository.nicknameState.value.lookupFailed)
                assertEquals(null, repository.nicknameState.value.nickname)
            }
        }

    @Test
    fun `조회 실패도 마지막 서버 확인 닉네임은 유지한다`() =
        runTest {
            var fail = false
            withRepository(
                MockEngine {
                    if (fail) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(
                            """{"nickname":"기존 이름"}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                },
            ) { repository ->
                repository.findMyNickname()
                fail = true
                repository.findMyNickname()
                assertEquals("기존 이름", repository.nicknameState.value.nickname)
                assertTrue(repository.nicknameState.value.isConfirmed)
                assertTrue(repository.nicknameState.value.lookupFailed)
                assertFalse(repository.nicknameState.value.isRefreshing)
            }
        }

    @Test
    fun `PUT 성공만으로 공유 닉네임을 바꾸지 않고 GET 확인 후 반영한다`() =
        runTest {
            var nickname = "기존 이름"
            withRepository(
                MockEngine { request ->
                    if (request.method == HttpMethod.Put) {
                        nickname = "새 이름"
                        respond("", HttpStatusCode.NoContent)
                    } else {
                        respond(
                            """{"nickname":"$nickname"}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                },
            ) { repository ->
                repository.findMyNickname()
                assertEquals(DeviceNicknameUpdateResult.UPDATED, repository.updateNickname("새 이름"))
                assertEquals("기존 이름", repository.nicknameState.value.nickname)
                repository.findMyNickname()
                assertEquals("새 이름", repository.nicknameState.value.nickname)
            }
        }

    @Test
    fun `기기 변경 전 시작한 조회 응답은 새 기기 상태를 덮어쓰지 않는다`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            withRepository(
                MockEngine {
                    started.complete(Unit)
                    finish.await()
                    respond(
                        """{"nickname":"이전 기기"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) { repository ->
                val lookup = async { repository.findMyNickname() }
                started.await()
                assertTrue(repository.nicknameState.value.isRefreshing)
                repository.invalidate()
                finish.complete(Unit)
                assertEquals(DeviceNicknameLookup.Failed(), lookup.await())
                assertFalse(repository.nicknameState.value.isConfirmed)
                assertFalse(repository.nicknameState.value.isRefreshing)
                assertFalse(repository.nicknameState.value.lookupFailed)
                assertEquals(null, repository.nicknameState.value.nickname)
            }
        }

    @Test
    fun `취소한 조회는 로딩을 종료하고 확인값을 보존한다`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var suspendResponse = false
            withRepository(
                MockEngine {
                    if (suspendResponse) {
                        started.complete(Unit)
                        finish.await()
                    }
                    respond(
                        """{"nickname":"확인한 이름"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) { repository ->
                repository.findMyNickname()
                suspendResponse = true
                val lookup = async { repository.findMyNickname() }
                started.await()
                lookup.cancel()
                lookup.join()
                assertFalse(repository.nicknameState.value.isRefreshing)
                assertTrue(repository.nicknameState.value.isConfirmed)
                assertEquals("확인한 이름", repository.nicknameState.value.nickname)
            }
        }

    @Test
    fun `인증 실패에 감싸진 네트워크 원인을 보존하고 업무 요청을 보내지 않는다`() =
        runTest {
            var requests = 0
            withRepository(
                MockEngine {
                    requests++
                    respond("", HttpStatusCode.Unauthorized)
                },
                accessTokenProvider =
                    AccessTokenProvider {
                        throw SessionAccessException(SessionFailureDetails("NETWORK"))
                    },
            ) { repository ->
                assertEquals(DeviceNicknameLookup.Failed(DeviceSessionFailureKind.NETWORK), repository.findMyNickname())
                assertEquals(0, requests)
                assertTrue(repository.nicknameState.value.lookupFailed)
            }
        }

    @Test
    fun `닉네임 조회의 연결 단절과 시간 초과를 네트워크 오류로 보존한다`() =
        runTest {
            val failures = listOf(IOException("offline"), HttpRequestTimeoutException("https://example.test", 1000))
            for (failure in failures) {
                withRepository(MockEngine { throw failure }) { repository ->
                    assertEquals(
                        DeviceNicknameLookup.Failed(DeviceSessionFailureKind.NETWORK),
                        repository.findMyNickname(),
                    )
                }
            }
        }

    private suspend fun withRepository(
        engine: MockEngine,
        accessTokenProvider: AccessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
        block: suspend (DeviceNicknameRepositoryImpl) -> Unit,
    ) {
        val client =
            createApiClient(
                engine = engine,
                config = ApiConfig("https://api-dev.pheeeew.com"),
                accessTokenProvider = accessTokenProvider,
            )
        try {
            block(DeviceNicknameRepositoryImpl(DeviceNicknameApi(client.requests)))
        } finally {
            client.close()
        }
    }
}
