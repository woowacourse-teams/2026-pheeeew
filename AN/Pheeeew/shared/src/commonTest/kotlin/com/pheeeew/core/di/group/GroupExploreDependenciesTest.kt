package com.pheeeew.core.di.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePageResult
import com.pheeeew.feature.screens.group.model.GroupJoinType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GroupExploreDependenciesTest {
    @Test
    fun `app factory injects authenticated API source and preserves server values and cursor`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("/api/v2/groups/explore", request.url.encodedPath)
                            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                            assertEquals("20", request.url.parameters["size"])
                            if (calls++ == 0) {
                                assertNull(request.url.parameters["keyword"])
                                assertNull(request.url.parameters["cursor"])
                            } else {
                                assertEquals("%_산책", request.url.parameters["keyword"])
                                assertEquals(" opaque+cursor/= ", request.url.parameters["cursor"])
                            }
                            respond(page, HttpStatusCode.OK, headers)
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val source = createGroupDependencies(client).exploreSource
                val result = assertIs<GroupExplorePageResult.Success>(source.loadPage("   ", null)).page
                assertEquals(listOf(ID, SECOND_ID), result.items.map { it.group.id.value })
                assertEquals(listOf(GroupJoinType.PUBLIC, GroupJoinType.PRIVATE), result.items.map { it.joinType })
                assertEquals(listOf(true, false), result.items.map { it.isJoined })
                assertEquals(
                    "서버 그룹",
                    result.items
                        .first()
                        .group.name,
                )
                assertEquals(
                    7L,
                    result.items
                        .first()
                        .group.memberCount,
                )
                assertEquals(
                    0xFF112233L,
                    result.items
                        .first()
                        .group.stamp.fillArgb,
                )
                assertNull(
                    result.items
                        .first()
                        .group.weeklyStampCount,
                )
                assertNull(
                    result.items
                        .first()
                        .group.description,
                )
                assertEquals(" opaque+cursor/= ", result.nextCursor)
                source.loadPage("  %_산책  ", result.nextCursor)
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
        const val SECOND_ID = "10000000-0000-0000-0000-000000000002"
        val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val page =
            """{"items":[{"groupId":"$ID","name":"서버 그룹","description":null,"joined":true,"memberCount":7,"joinType":"PUBLIC","stamp":{"text":"휴식","textColor":"#FFFFFF","backgroundColor":"#112233","frame":"CIRCLE"}},{"groupId":"$SECOND_ID","name":"비공개 그룹","joined":false,"memberCount":2,"joinType":"PRIVATE","stamp":{"text":"기록","textColor":"#FFFFFF","backgroundColor":"#112233","frame":"CIRCLE"}}],"hasNext":true,"nextCursor":" opaque+cursor/= "}"""
    }
}
