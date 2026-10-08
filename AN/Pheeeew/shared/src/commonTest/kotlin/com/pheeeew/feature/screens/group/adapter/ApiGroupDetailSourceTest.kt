package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.data.remote.group.dto.GroupDetailResponseDto
import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import com.pheeeew.data.remote.group.mapper.GroupDetailResponseMapper
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.repository.group.GroupDetailLookupResult
import com.pheeeew.domain.repository.group.GroupDetailRepository
import com.pheeeew.domain.repository.group.GroupLeaveResult
import com.pheeeew.feature.screens.group.detail.GroupDetailLoadResult
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.detail.model.toReadyUiModel
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiGroupDetailSourceTest {
    @Test
    fun `v3 response metrics reach ready ui model for nonmembers`() =
        runTest {
            val domainDetail = GroupDetailResponseMapper.toDomain(response)
            val repository =
                object : GroupDetailRepository {
                    override suspend fun findById(groupId: DomainGroupId) = GroupDetailLookupResult.Found(domainDetail)

                    override suspend fun leave(groupId: DomainGroupId) = GroupLeaveResult.Left
                }

            val loaded =
                assertIs<GroupDetailLoadResult.Loaded>(
                    ApiGroupDetailSource(repository).load(GroupId(GROUP_ID)),
                ).detail
            val feedState = GroupMoodFeedUiState.LoadFailed(isRetrying = false)
            val ready = loaded.toReadyUiModel(feedState)

            assertEquals(GroupRole.NONE, ready.role)
            assertEquals(42L, ready.weeklyStampCount)
            assertEquals(3, ready.weeklyStampRank)
            assertEquals(318L, ready.weeklyEmotionPressCount)
            assertEquals(5, ready.weeklyEmotionPressRank)
            assertEquals(feedState, ready.feed)
        }

    private companion object {
        const val GROUP_ID = "0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
        val response =
            GroupDetailResponseDto(
                groupId = GROUP_ID,
                name = "한숨모임",
                description = "퇴근하고 한 번씩",
                inviteCode = "ABCD1234",
                role = "NONE",
                memberCount = 7L,
                stamp = GroupStampResponseDto("버티자", "#FFFFFF", "#4A90D9", "SCALLOP"),
                weeklyStampCount = 42L,
                weeklyStampRank = 3,
                weeklyEmotionPressCount = 318L,
                weeklyEmotionPressRank = 5,
            )
    }
}
