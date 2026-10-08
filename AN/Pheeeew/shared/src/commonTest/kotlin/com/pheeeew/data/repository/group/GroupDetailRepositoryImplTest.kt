package com.pheeeew.data.repository.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupDetailApi
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.repository.group.GroupDetailLookupResult
import com.pheeeew.domain.repository.group.GroupLeaveResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupDetailRepositoryImplTest {
    @Test
    fun `group detail loads role none with v3 response fields`() =
        runTest {
            var requestedPath = ""
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPath = request.url.encodedPath
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                            respond(response("NONE"), headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                val result =
                    assertIs<GroupDetailLookupResult.Found>(
                        GroupDetailRepositoryImpl(GroupDetailApi(client.requests))
                            .findById(requireNotNull(GroupId.parse(GROUP_ID))),
                    ).detail

                assertEquals("/api/v3/groups/$GROUP_ID", requestedPath)
                assertEquals(GroupRole.NONE, result.group.role)
                assertEquals(42L, result.weeklyStampCount)
                assertEquals(3, result.weeklyStampRank)
                assertEquals(318L, result.weeklyEmotionPressCount)
                assertEquals(5, result.weeklyEmotionPressRank)
            } finally {
                client.close()
            }
        }

    @Test
    fun `zero counts decode with null ranks and invalid response is unavailable`() =
        runTest {
            val zeroClient = createClient(response("MEMBER", 0, null, 0, null))
            val invalidClient = createClient(response("MEMBER", -1, 1, 0, null))
            try {
                val repository = GroupDetailRepositoryImpl(GroupDetailApi(zeroClient.requests))
                val detail =
                    assertIs<GroupDetailLookupResult.Found>(
                        repository.findById(requireNotNull(GroupId.parse(GROUP_ID))),
                    ).detail
                assertEquals(0L, detail.weeklyStampCount)
                assertEquals(null, detail.weeklyStampRank)
                assertEquals(0L, detail.weeklyEmotionPressCount)
                assertEquals(null, detail.weeklyEmotionPressRank)
                assertEquals(
                    GroupDetailLookupResult.Unavailable,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(invalidClient.requests),
                    ).findById(requireNotNull(GroupId.parse(GROUP_ID))),
                )
            } finally {
                zeroClient.close()
                invalidClient.close()
            }
        }

    @Test
    fun `not found and authentication errors are unavailable or not found as specified`() =
        runTest {
            val notFound = createClient("{}", HttpStatusCode.NotFound)
            val unauthorized = createClient("{}", HttpStatusCode.Unauthorized)
            val forbidden = createClient("{}", HttpStatusCode.Forbidden)
            try {
                assertEquals(
                    GroupDetailLookupResult.NotFound,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(notFound.requests),
                    ).findById(requireNotNull(GroupId.parse(GROUP_ID))),
                )
                assertEquals(
                    GroupDetailLookupResult.Unavailable,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(unauthorized.requests),
                    ).findById(requireNotNull(GroupId.parse(GROUP_ID))),
                )
                assertEquals(
                    GroupDetailLookupResult.Unavailable,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(forbidden.requests),
                    ).findById(requireNotNull(GroupId.parse(GROUP_ID))),
                )
            } finally {
                notFound.close()
                unauthorized.close()
                forbidden.close()
            }
        }

    @Test
    fun `mismatched response group id is unavailable`() =
        runTest {
            val client = createClient(response("NONE").replace(GROUP_ID, OTHER_GROUP_ID))
            try {
                assertEquals(
                    GroupDetailLookupResult.Unavailable,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(client.requests),
                    ).findById(requireNotNull(GroupId.parse(GROUP_ID))),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `transport failure is unavailable`() =
        runTest {
            val client =
                createApiClient(
                    engine = MockEngine { throw IOException("network unavailable") },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                assertEquals(
                    GroupDetailLookupResult.Unavailable,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(client.requests),
                    ).findById(requireNotNull(GroupId.parse(GROUP_ID))),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `cancellation is propagated`() =
        runTest {
            val client =
                createApiClient(
                    engine = MockEngine { throw CancellationException("cancelled") },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                supervisorScope {
                    val result =
                        async {
                            GroupDetailRepositoryImpl(GroupDetailApi(client.requests))
                                .findById(requireNotNull(GroupId.parse(GROUP_ID)))
                        }
                    try {
                        result.await()
                        error("Expected cancellation")
                    } catch (cancelled: CancellationException) {
                        assertEquals("cancelled", cancelled.message)
                    }
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun `leave remains on v2 path`() =
        runTest {
            var requestedPath = ""
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPath = request.url.encodedPath
                            assertEquals(HttpMethod.Delete, request.method)
                            respond("", status = HttpStatusCode.NoContent)
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                assertEquals(
                    GroupLeaveResult.Left,
                    GroupDetailRepositoryImpl(
                        GroupDetailApi(client.requests),
                    ).leave(requireNotNull(GroupId.parse(GROUP_ID))),
                )
                assertEquals("/api/v2/groups/$GROUP_ID/members/me", requestedPath)
            } finally {
                client.close()
            }
        }

    private fun createClient(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = createApiClient(
        engine = MockEngine { respond(body, status = status, headers = jsonHeaders()) },
        config = ApiConfig("https://api.example.test"),
        accessTokenProvider = { AccessToken("access-token") },
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun response(
        role: String,
        stampCount: Long = 42L,
        stampRank: Int? = 3,
        pressCount: Long = 318L,
        pressRank: Int? = 5,
    ) = """
        {
          "groupId":"$GROUP_ID",
          "name":"한숨모임",
          "description":"퇴근하고 한 번씩",
          "inviteCode":"ABCD1234",
          "role":"$role",
          "memberCount":7,
          "stamp":{"text":"버티자","textColor":"#FFFFFF","backgroundColor":"#4A90D9","frame":"SCALLOP"},
          "weeklyStampCount":$stampCount,
          "weeklyStampRank":${stampRank?.toString() ?: "null"},
          "weeklyEmotionPressCount":$pressCount,
          "weeklyEmotionPressRank":${pressRank?.toString() ?: "null"}
        }
        """.trimIndent()

    private companion object {
        const val GROUP_ID = "0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
        const val OTHER_GROUP_ID = "1b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
    }
}
