package com.pheeeew.data.repository

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.GroupCreateApi
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.GroupCreateCommand
import com.pheeeew.domain.repository.GroupCreateRepositoryResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupCreateRepositoryImplTest {
    @Test
    fun `POST sends authenticated create contract including explicit null description and selected stamp`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Post, request.method)
                            assertEquals("/api/v2/groups", request.url.encodedPath)
                            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                            val body = request.body.toByteArray().decodeToString()
                            assertEquals(
                                """{"name":"기록모임","description":null,"stamp":{"text":"오늘","textColor":"#11223380","backgroundColor":"#AABBCC","frame":"VOUCHER"}}""",
                                body,
                            )
                            respond(
                                """{"groupId":"10000000-0000-0000-0000-000000000001","name":"기록모임"}""",
                                status = HttpStatusCode.Created,
                                headers = jsonHeaders(),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )

            try {
                val result = GroupCreateRepositoryImpl(GroupCreateApi(client.requests)).create(command())

                assertEquals(
                    GroupCreateRepositoryResult.Created(
                        com.pheeeew.domain.model.group.GroupId
                            .parse("10000000-0000-0000-0000-000000000001")!!,
                    ),
                    result,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `only documented duplicate and validation statuses get their specific outcomes`() =
        runTest {
            assertEquals(GroupCreateRepositoryResult.DuplicateName, resultFor(HttpStatusCode.Conflict))
            assertEquals(GroupCreateRepositoryResult.InvalidInput, resultFor(HttpStatusCode.BadRequest))
            assertEquals(GroupCreateRepositoryResult.Unavailable, resultFor(HttpStatusCode.Unauthorized))
            assertEquals(GroupCreateRepositoryResult.RateLimited, resultFor(HttpStatusCode.TooManyRequests))
            assertEquals(GroupCreateRepositoryResult.OutcomeUnknown, resultFor(HttpStatusCode.BadGateway))
        }

    @Test
    fun `invalid successful identifier and malformed success response remain outcome unknown`() =
        runTest {
            assertEquals(
                GroupCreateRepositoryResult.OutcomeUnknown,
                resultFor(HttpStatusCode.Created, """{"groupId":"not-a-uuid"}"""),
            )
            assertEquals(
                GroupCreateRepositoryResult.OutcomeUnknown,
                resultFor(HttpStatusCode.Created, "{}"),
            )
        }

    private suspend fun resultFor(
        status: HttpStatusCode,
        body: String = "{}",
    ): GroupCreateRepositoryResult {
        val client =
            createApiClient(
                engine = MockEngine { respond(body, status = status, headers = jsonHeaders()) },
                config = ApiConfig("https://api.example.test"),
                accessTokenProvider = { AccessToken("access-token") },
            )
        return try {
            GroupCreateRepositoryImpl(GroupCreateApi(client.requests)).create(command())
        } finally {
            client.close()
        }
    }

    private fun command() =
        GroupCreateCommand(
            name = "기록모임",
            description = null,
            stamp =
                GroupStamp(
                    text = "오늘",
                    textColor = StampColor.parseServerValue("#11223380")!!,
                    backgroundColor = StampColor.parseServerValue("#AABBCC")!!,
                    frame = GroupStampFrame.VOUCHER,
                ),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
