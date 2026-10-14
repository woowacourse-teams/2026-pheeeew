package com.pheeeew.data.repository.group

import com.pheeeew.core.di.group.createGroupDependencies
import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupMembersApi
import com.pheeeew.domain.repository.group.GroupMembersResult
import com.pheeeew.feature.screens.group.members.GroupMembersPageResult
import com.pheeeew.feature.screens.group.model.GroupId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class GroupMembersRepositoryImplTest {
    @Test
    fun `app injects authenticated source preserving duplicate rows and opaque cursor`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/api/v2/groups/$ID/members", request.url.encodedPath)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals("20", request.url.parameters["size"])
                        if (calls++ == 0) {
                            assertNull(request.url.parameters["cursor"])
                        } else {
                            assertEquals(" opaque+cursor/= ", request.url.parameters["cursor"])
                        }
                        respond(page, HttpStatusCode.OK, headers)
                    },
                    ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val source = createGroupDependencies(client).membersSource
                val first = assertIs<GroupMembersPageResult.Success>(source.loadPage(GroupId(ID), null)).page
                assertEquals(listOf("같은 이름", "같은 이름", "익명"), first.items.map { it.displayNickname })
                assertEquals(
                    "OWNER",
                    first.items
                        .first()
                        .role.name,
                )
                source.loadPage(GroupId(ID), first.nextCursor)
                assertEquals(GroupMembersPageResult.NotFound, source.loadPage(GroupId("invalid"), null))
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `maps status codes without treating authentication as a member list`() =
        runTest {
            val cases =
                listOf(
                    Triple(403, "GROUP-008", GroupMembersResult.Forbidden),
                    Triple(404, "GROUP-001", GroupMembersResult.NotFound),
                    Triple(400, "GROUP-010", GroupMembersResult.InvalidCursor),
                    Triple(401, "AUTH-001", GroupMembersResult.Unauthorized),
                    Triple(403, "OTHER", GroupMembersResult.Unavailable),
                    Triple(404, "OTHER", GroupMembersResult.Unavailable),
                    Triple(500, "OTHER", GroupMembersResult.Unavailable),
                )
            for ((status, code, expected) in cases) {
                val client =
                    createApiClient(
                        MockEngine { respond("{\"code\":\"$code\"}", HttpStatusCode.fromValue(status), headers) },
                        ApiConfig("https://api.example.test"),
                        accessTokenProvider = { AccessToken("token") },
                    )
                try {
                    assertEquals(
                        expected,
                        GroupMembersRepositoryImpl(GroupMembersApi(client.requests)).loadPage(id, null),
                    )
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `rejects missing fields invalid roles dates and inconsistent pages`() =
        runTest {
            val invalid =
                listOf(
                    "{\"items\":[],\"hasNext\":false}",
                    "{\"items\":[],\"hasNext\":true,\"nextCursor\":\"next\"}",
                    "{\"items\":[],\"hasNext\":false,\"nextCursor\":\"next\"}",
                    page.replace("OWNER", "NONE"),
                    page.replace("2026-10-11T09:00:00Z", "invalid"),
                    page.replace("\"nickname\":null,", ""),
                    page.replace(" opaque+cursor/= ", ""),
                )
            for (body in invalid) {
                val client =
                    createApiClient(
                        MockEngine { respond(body, HttpStatusCode.OK, headers) },
                        ApiConfig("https://api.example.test"),
                        accessTokenProvider = { AccessToken("token") },
                    )
                try {
                    assertEquals(
                        GroupMembersResult.Unavailable,
                        GroupMembersRepositoryImpl(GroupMembersApi(client.requests)).loadPage(id, null),
                    )
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `preserves cancellation`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { throw CancellationException("cancelled") },
                    ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                assertFailsWith<CancellationException> {
                    GroupMembersRepositoryImpl(GroupMembersApi(client.requests)).loadPage(id, null)
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun `missing session clears authentication without issuing a request`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    MockEngine {
                        calls++
                        respond(page, HttpStatusCode.OK, headers)
                    },
                    ApiConfig("https://api.example.test"),
                    accessTokenProvider = null,
                )
            try {
                assertEquals(
                    GroupMembersResult.Unauthorized,
                    GroupMembersRepositoryImpl(GroupMembersApi(client.requests)).loadPage(id, null),
                )
                assertEquals(0, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `transport errors retain the ordinary failure category`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { throw IllegalStateException("connection lost") },
                    ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                assertEquals(
                    GroupMembersResult.Unavailable,
                    GroupMembersRepositoryImpl(GroupMembersApi(client.requests)).loadPage(id, null),
                )
            } finally {
                client.close()
            }
        }

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
        val id = requireNotNull(DomainGroupId.parse(ID))
        val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val page =
            """{"items":[{"nickname":"같은 이름","role":"OWNER","joinedAt":"2026-10-11T09:00:00Z"},{"nickname":"같은 이름","role":"MEMBER","joinedAt":"2026-10-11T09:00:00Z"},{"nickname":null,"role":"MEMBER","joinedAt":"2026-10-11T09:00:00Z"}],"hasNext":true,"nextCursor":" opaque+cursor/= "}"""
    }
}
