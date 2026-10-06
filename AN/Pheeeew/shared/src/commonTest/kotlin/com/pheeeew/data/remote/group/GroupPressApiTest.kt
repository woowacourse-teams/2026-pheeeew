package com.pheeeew.data.remote.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupPressApi
import com.pheeeew.data.remote.group.dto.GroupPressCountResponseDto
import com.pheeeew.domain.model.group.GroupPressState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupPressApiTest {
    @Test
    fun `현재 그룹의 주간 감정 집계를 조회한다`() =
        runTest {
            var requestedPath = ""
            var authorization: String? = null
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPath = request.url.encodedPath
                            authorization = request.headers[HttpHeaders.Authorization]
                            assertEquals(HttpMethod.Get, request.method)
                            respond(PRESS_COUNTS, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    assertIs<ApiResult.Success<GroupPressCountResponseDto>>(
                        GroupPressApi(client.requests).findWeekly(GROUP_ID),
                    )

                assertEquals("/api/v2/groups/$GROUP_ID/presses", requestedPath)
                assertEquals("Bearer app-access-token", authorization)
                assertEquals(12L, result.value.total)
                assertEquals(4L, result.value.counts.getValue("ANGRY"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `현재 백엔드 단건 계약의 state 본문으로 전송하고 집계 응답을 읽는다`() =
        runTest {
            var requestedPath = ""
            var requestBody = ""
            var authorization: String? = null
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPath = request.url.encodedPath
                            requestBody = request.body.toByteArray().decodeToString()
                            authorization = request.headers[HttpHeaders.Authorization]
                            assertEquals(HttpMethod.Post, request.method)
                            respond(PRESS_COUNTS, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    assertIs<ApiResult.Success<GroupPressCountResponseDto>>(
                        GroupPressApi(client.requests).press(
                            GROUP_ID,
                            GroupPressState.ANGRY,
                        ),
                    )

                assertEquals("/api/v2/groups/$GROUP_ID/presses", requestedPath)
                assertEquals("Bearer app-access-token", authorization)
                assertEquals(
                    """{"state":"ANGRY"}""",
                    requestBody,
                )
                assertEquals(12L, result.value.total)
                assertEquals(4L, result.value.counts.getValue("ANGRY"))
            } finally {
                client.close()
            }
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private companion object {
        const val GROUP_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        const val PRESS_COUNTS =
            """{"counts":{"FRUSTRATED":1,"IRRITATED":2,"EXHAUSTED":3,"DISCOURAGED":2,"ANGRY":4},"total":12}"""
    }
}
