package com.pheeeew.data.remote.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupPressApi
import com.pheeeew.data.remote.group.dto.GroupPressCountResponseDto
import com.pheeeew.domain.model.group.GroupPressBatch
import com.pheeeew.domain.model.group.GroupPressIncrement
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
import kotlin.test.assertTrue

class GroupPressApiTest {
    @Test
    fun `감정별 횟수를 정렬해 presses 목록으로 전송하고 기존 집계 응답을 읽는다`() =
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
                            GroupPressBatch(
                                listOf(
                                    GroupPressIncrement(GroupPressState.IRRITATED, 3),
                                    GroupPressIncrement(GroupPressState.ANGRY, 2),
                                ),
                            ),
                        ),
                    )

                assertEquals("/api/v2/groups/$GROUP_ID/presses", requestedPath)
                assertEquals("Bearer app-access-token", authorization)
                assertEquals(
                    """{"presses":[{"state":"ANGRY","count":2},{"state":"IRRITATED","count":3}]}""",
                    requestBody,
                )
                assertEquals(12L, result.value.total)
                assertEquals(4L, result.value.counts.getValue("ANGRY"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `요청 배치는 빈 목록 중복 감정 및 총량 초과를 거부한다`() {
        assertTrue(runCatching { GroupPressBatch(emptyList()) }.isFailure)
        assertTrue(
            runCatching {
                GroupPressBatch(
                    listOf(
                        GroupPressIncrement(GroupPressState.ANGRY, 1),
                        GroupPressIncrement(GroupPressState.ANGRY, 2),
                    ),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                GroupPressBatch(listOf(GroupPressIncrement(GroupPressState.ANGRY, 101)))
            }.isFailure,
        )
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private companion object {
        const val GROUP_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        const val PRESS_COUNTS =
            """{"counts":{"FRUSTRATED":1,"IRRITATED":2,"EXHAUSTED":3,"DISCOURAGED":2,"ANGRY":4},"total":12}"""
    }
}
