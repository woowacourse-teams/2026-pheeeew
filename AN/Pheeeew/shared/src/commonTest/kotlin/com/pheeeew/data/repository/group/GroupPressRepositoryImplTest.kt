package com.pheeeew.data.repository.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupPressApi
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupPressState
import com.pheeeew.domain.repository.group.GroupPressResult
import com.pheeeew.domain.repository.group.GroupWeeklyPressCountResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupPressRepositoryImplTest {
    @Test
    fun `주간 집계 API 응답의 감정 상태와 합계를 검증해 반환한다`() =
        runTest {
            val client =
                createApiClient(
                    engine = MockEngine { respond(WEEKLY_PRESS_COUNTS, headers = jsonHeaders()) },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    GroupPressRepositoryImpl(GroupPressApi(client.requests))
                        .findWeekly(GroupId.parse(GROUP_ID)!!)

                assertEquals(GroupWeeklyPressCountResult.Loaded(12L), result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `잘못된 주간 집계 계약은 사용할 수 없음으로 처리한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            respond(
                                """{"counts":{"ANGRY":-1},"total":-1}""",
                                headers = jsonHeaders(),
                            )
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    GroupPressRepositoryImpl(GroupPressApi(client.requests))
                        .findWeekly(GroupId.parse(GROUP_ID)!!)

                assertEquals(GroupWeeklyPressCountResult.Unavailable, result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `429 Retry-After 초를 밀리초로 변환해 반환한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            respond(
                                content = "too many requests",
                                status = HttpStatusCode.TooManyRequests,
                                headers = headersOf(HttpHeaders.RetryAfter, "2"),
                            )
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    GroupPressRepositoryImpl(GroupPressApi(client.requests)).press(
                        GroupId.parse(GROUP_ID)!!,
                        GroupPressState.ANGRY,
                    )

                assertEquals(GroupPressResult.RateLimited(2_000), result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `429 Retry-After가 없으면 repository는 null을 전달해 정책 기본값 사용`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { respond("too many requests", status = HttpStatusCode.TooManyRequests) },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    GroupPressRepositoryImpl(GroupPressApi(client.requests)).press(
                        GroupId.parse(GROUP_ID)!!,
                        GroupPressState.ANGRY,
                    )

                val rateLimited = assertIs<GroupPressResult.RateLimited>(result)
                assertEquals(null, rateLimited.retryAfterMillis)
            } finally {
                client.close()
            }
        }

    private companion object {
        const val GROUP_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        const val WEEKLY_PRESS_COUNTS =
            """{"counts":{"FRUSTRATED":1,"IRRITATED":2,"EXHAUSTED":3,"DISCOURAGED":2,"ANGRY":4},"total":12}"""
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
