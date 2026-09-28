package com.pheeeew.feature.screens.map.record

import com.pheeeew.core.di.emotion.createNearbyEmotionViewModel
import com.pheeeew.core.di.group.createGroupStampListRepository
import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembershipRefreshTest {
    @Test
    fun `nearby and record selectors share membership updates without waiting for cache expiry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var response = "[]"
            var requests = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals("/api/v2/groups/stamps", request.url.encodedPath)
                            requests++
                            respond(
                                response,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                val groups = createGroupStampListRepository(client)
                val nearby = createNearbyEmotionViewModel(client, groups)
                val record =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        object : EmotionRegistrationRepository {
                            override suspend fun register(registration: EmotionRegistration) =
                                EmotionRegistrationResult.Unavailable
                        },
                        groups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                nearby.openGroups()
                record.open(EmotionTypeUiModel.FRUSTRATED)
                nearby.state.first { !it.groupsLoading }
                record.uiModel.first { !it.isGroupSelectionLoading }
                assertEquals(
                    listOf("all"),
                    nearby.state.value.groups
                        .map { it.id },
                )
                assertEquals(listOf("none"), record.groupOptions.value.map { it.id })
                assertEquals(1, requests)

                // The app's membership callback invalidates the shared store and refreshes nearby options.
                response = GROUP_RESPONSE
                groups.invalidate()
                nearby.onMembershipChanged()
                record.onGroupSelectorOpen()
                nearby.state.first { !it.groupsLoading }
                record.uiModel.first { !it.isGroupSelectionLoading }
                assertEquals(
                    listOf("all", GROUP_ID),
                    nearby.state.value.groups
                        .map { it.id },
                )
                assertEquals(listOf("none", GROUP_ID), record.groupOptions.value.map { it.id })
                assertEquals(2, requests)

                record.onGroupSelectionComplete(record.groupOptions.value.last())
                nearby.completeGroup(
                    nearby.state.value.groups
                        .last(),
                )
                response = "[]"
                groups.invalidate()
                nearby.onMembershipChanged()
                record.onGroupSelectorOpen()
                nearby.state.first { !it.groupsLoading }
                record.uiModel.first { !it.isGroupSelectionLoading }
                assertEquals("all", nearby.state.value.groupId)
                assertEquals("none", record.uiModel.value.selectedGroupId)
                assertEquals(
                    listOf("all"),
                    nearby.state.value.groups
                        .map { it.id },
                )
                assertEquals(listOf("none"), record.groupOptions.value.map { it.id })
                assertEquals(3, requests)
            } finally {
                client.close()
                Dispatchers.resetMain()
            }
        }

    private companion object {
        const val GROUP_ID = "10000000-0000-0000-0000-000000000001"
        val GROUP_RESPONSE =
            """
            [{"groupId":"$GROUP_ID","name":"새 그룹",
              "stamp":{"text":"모임","textColor":"#112233","backgroundColor":"#FFE164","frame":"CIRCLE"}}]
            """.trimIndent()
    }
}
