package com.pheeeew.core.di.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupStampListApi
import com.pheeeew.data.repository.group.GroupStampListRepositoryImpl
import com.pheeeew.domain.repository.group.GroupPublicJoinResult
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.feature.screens.group.detail.GroupDetailLoadResult
import com.pheeeew.feature.screens.group.detail.LeaveGroupResult
import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePageResult
import com.pheeeew.feature.screens.group.members.GroupMembersPageResult
import com.pheeeew.feature.screens.group.model.GroupId
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

class GroupApiMembershipIntegrationTest {
    @Test
    fun `API join and leave synchronize mine catalog detail members and invalidated stamp cache`() =
        runTest {
            var joined = false
            var writes = 0
            val stamp = """{"text":"QA","textColor":"#222222","backgroundColor":"#9CE5D0","frame":"SQUIRCLE"}"""

            fun base() =
                """"groupId":"$ID","name":"통합 그룹","description":null,"joinType":"PUBLIC","memberCount":${if (joined) 2 else 1},"stamp":$stamp"""
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        val path = request.url.encodedPath
                        when {
                            path == "/api/v2/groups/$ID/members/me" && request.method == HttpMethod.Post -> {
                                writes++
                                joined = true
                                respond("""{"groupId":"$ID"}""", HttpStatusCode.Created, headers)
                            }

                            path == "/api/v2/groups/$ID/members/me" && request.method == HttpMethod.Delete -> {
                                writes++
                                joined = false
                                respond("", HttpStatusCode.NoContent)
                            }

                            path == "/api/v2/groups" -> {
                                respond(
                                    if (joined) """[{${base()},"inviteCode":"ABC123","role":"MEMBER"}]""" else "[]",
                                    HttpStatusCode.OK,
                                    headers,
                                )
                            }

                            path == "/api/v2/groups/explore" -> {
                                respond(
                                    """{"items":[{${base()},"joined":$joined}],"hasNext":false,"nextCursor":null}""",
                                    HttpStatusCode.OK,
                                    headers,
                                )
                            }

                            path == "/api/v3/groups/$ID" -> {
                                respond(
                                    """{${base()},"inviteCode":"ABC123","role":"${if (joined) "MEMBER" else "NONE"}","weeklyStampCount":0,"weeklyStampRank":null,"weeklyEmotionPressCount":0,"weeklyEmotionPressRank":null}""",
                                    HttpStatusCode.OK,
                                    headers,
                                )
                            }

                            path == "/api/v2/groups/$ID/members" -> {
                                if (joined) {
                                    respond(
                                        """{"items":[{"nickname":"익명","role":"OWNER","joinedAt":"2026-10-13T01:00:00Z"},{"nickname":"익명","role":"MEMBER","joinedAt":"2026-10-13T02:00:00Z"}],"hasNext":false,"nextCursor":null}""",
                                        HttpStatusCode.OK,
                                        headers,
                                    )
                                } else {
                                    respond("""{"code":"GROUP-008"}""", HttpStatusCode.Forbidden, headers)
                                }
                            }

                            path == "/api/v2/groups/stamps" -> {
                                respond(
                                    if (joined) {
                                        """[{"groupId":"$ID","name":"통합 그룹","stamp":$stamp}]"""
                                    } else {
                                        "[]"
                                    },
                                    HttpStatusCode.OK,
                                    headers,
                                )
                            }

                            else -> {
                                error("Unexpected request: $path")
                            }
                        }
                    },
                    ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val stamps = GroupStampListRepositoryImpl(GroupStampListApi(client.requests))
                val dependencies = createGroupDependencies(client, stamps.membershipChanges, stamps::invalidate)
                val id = GroupId(ID)
                assertEquals(0, assertIs<GroupStampListLoadResult.Loaded>(stamps.findMyStamps()).groups.size)
                assertEquals(GroupPublicJoinResult.Joined, dependencies.detail.publicJoinAction.join(id))
                assertEquals(
                    "MEMBER",
                    assertIs<GroupDetailLoadResult.Loaded>(dependencies.detail.source.load(id)).detail.role.name,
                )
                dependencies.invalidateSharedMembership()
                assertEquals(
                    1,
                    assertIs<GroupListResult.Success>(dependencies.groupListSource.loadGroups()).groups.size,
                )
                val catalog =
                    assertIs<GroupExplorePageResult.Success>(
                        dependencies.exploreSource.loadPage(null, null),
                    ).page.items.single()
                assertEquals(true, catalog.isJoined)
                assertEquals(2L, catalog.group.memberCount)
                assertEquals(
                    2,
                    assertIs<GroupMembersPageResult.Success>(
                        dependencies.membersSource.loadPage(id, null),
                    ).page.items.size,
                )
                assertEquals(1, assertIs<GroupStampListLoadResult.Loaded>(stamps.findMyStamps()).groups.size)
                assertEquals(LeaveGroupResult.Left, dependencies.detail.leaveGroupAction.leave(id))
                dependencies.invalidateSharedMembership()
                assertEquals(
                    0,
                    assertIs<GroupListResult.Success>(dependencies.groupListSource.loadGroups()).groups.size,
                )
                assertEquals(
                    false,
                    assertIs<GroupExplorePageResult.Success>(
                        dependencies.exploreSource.loadPage(null, null),
                    ).page.items.single().isJoined,
                )
                assertEquals(GroupMembersPageResult.Forbidden, dependencies.membersSource.loadPage(id, null))
                assertEquals(
                    "NONE",
                    assertIs<GroupDetailLoadResult.Loaded>(dependencies.detail.source.load(id)).detail.role.name,
                )
                assertEquals(0, assertIs<GroupStampListLoadResult.Loaded>(stamps.findMyStamps()).groups.size)
                assertEquals(2, writes)
            } finally {
                client.close()
            }
        }

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
        val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
