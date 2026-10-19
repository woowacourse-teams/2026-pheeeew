package com.pheeeew.data.repository

import com.pheeeew.core.di.group.createGroupDependencies
import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.api.GroupMembershipApi
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.repository.GroupPublicJoinResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.pheeeew.feature.screens.group.model.GroupId as FeatureGroupId

class GroupPublicJoinRepositoryImplTest {
    @Test
    fun `authenticated public join sends no body and validates target identifier`() =
        runTest {
            assertEquals(GroupPublicJoinResult.Joined, result(HttpStatusCode.Created, """{"groupId":"$ID"}"""))
            assertEquals(GroupPublicJoinResult.OutcomeUnknown, result(HttpStatusCode.Created, "{}"))
            assertEquals(
                GroupPublicJoinResult.OutcomeUnknown,
                result(
                    HttpStatusCode.Created,
                    """{"groupId":"10000000-0000-0000-0000-000000000002"}""",
                ),
            )
        }

    @Test
    fun `public join errors use specific codes and preserve unknown writes`() =
        runTest {
            assertEquals(
                GroupPublicJoinResult.AlreadyMember,
                result(HttpStatusCode.Conflict, """{"code":"GROUP-007"}"""),
            )
            assertEquals(
                GroupPublicJoinResult.PrivateGroup,
                result(HttpStatusCode.Conflict, """{"code":"GROUP-009"}"""),
            )
            assertEquals(GroupPublicJoinResult.Unavailable, result(HttpStatusCode.Conflict, """{"code":"OTHER"}"""))
            assertEquals(GroupPublicJoinResult.NotFound, result(HttpStatusCode.NotFound, """{"code":"GROUP-001"}"""))
            assertEquals(GroupPublicJoinResult.Unavailable, result(HttpStatusCode.Unauthorized, "{}"))
            assertEquals(GroupPublicJoinResult.OutcomeUnknown, result(HttpStatusCode.BadGateway, "{}"))
        }

    @Test
    fun `lost response is unknown and cancellation propagates without replay`() =
        runTest {
            var requests = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            requests += 1
                            throw IllegalStateException("response lost")
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                assertEquals(
                    GroupPublicJoinResult.OutcomeUnknown,
                    GroupPublicJoinRepositoryImpl(GroupMembershipApi(client.requests)).join(GroupId.parse(ID)!!),
                )
                assertEquals(1, requests)
            } finally {
                client.close()
            }
            val cancelled =
                createApiClient(
                    engine = MockEngine { throw CancellationException("cancelled") },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                assertFailsWith<CancellationException> {
                    GroupPublicJoinRepositoryImpl(GroupMembershipApi(cancelled.requests)).join(GroupId.parse(ID)!!)
                }
            } finally {
                cancelled.close()
            }
        }

    @Test
    fun `app factory provides real public action and target code dependencies`() =
        runTest {
            var posts = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            if (request.method == HttpMethod.Post) {
                                posts += 1
                                assertEquals("/api/v2/groups/$ID/members/me", request.url.encodedPath)
                                respond(
                                    """{"groupId":"$ID"}""",
                                    HttpStatusCode.Created,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            } else {
                                assertEquals("/api/v2/groups/search", request.url.encodedPath)
                                respond(
                                    """{"groupId":"$ID","name":"모임","memberCount":2,"stamp":{"text":"기록","textColor":"#FFFFFF","backgroundColor":"#112233","frame":"CIRCLE"}}""",
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            }
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val dependencies =
                    createGroupDependencies(client)
                        .detail
                assertEquals(
                    GroupPublicJoinResult.Joined,
                    dependencies.publicJoinAction.join(
                        FeatureGroupId(ID),
                    ),
                )
                kotlin.test.assertIs<com.pheeeew.feature.screens.group.join.GroupLookupResult.Found>(
                    kotlin.test
                        .assertNotNull(dependencies.codeJoin)
                        .lookupGroupAction
                        .find("ABC123"),
                )
                assertEquals(1, posts)
            } finally {
                client.close()
            }
        }

    private suspend fun result(
        status: HttpStatusCode,
        body: String,
    ): GroupPublicJoinResult {
        val client =
            createApiClient(
                engine =
                    MockEngine { request ->
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("/api/v2/groups/$ID/members/me", request.url.encodedPath)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals(0, request.body.toByteArray().size)
                        respond(
                            body,
                            status,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                config = ApiConfig("https://api.example.test"),
                accessTokenProvider = { AccessToken("token") },
            )
        return try {
            GroupPublicJoinRepositoryImpl(GroupMembershipApi(client.requests)).join(GroupId.parse(ID)!!)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
    }
}
