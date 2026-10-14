package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.data.remote.group.dto.GroupPreviewResponseDto
import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import com.pheeeew.data.remote.group.mapper.GroupResponseMapper
import com.pheeeew.domain.repository.group.GroupInviteLookupResult
import com.pheeeew.domain.repository.group.GroupJoinRepository
import com.pheeeew.domain.repository.group.GroupJoinRepositoryResult
import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.join.GroupJoinResult
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiGroupJoinActionsTest {
    @Test
    fun `another groups code never sends a membership write`() =
        runTest {
            var writes = 0
            val repository =
                object : GroupJoinRepository {
                    override suspend fun findByInviteCode(normalizedCode: String) =
                        GroupInviteLookupResult.Found(preview(OTHER))

                    override suspend fun join(normalizedCode: String): GroupJoinRepositoryResult {
                        writes += 1
                        return GroupJoinRepositoryResult.Joined(DomainGroupId.parse(OTHER)!!)
                    }
                }
            val action = ApiJoinGroupAction(repository, emptyGroups)
            assertEquals(GroupJoinResult.Rejected, action.join(GroupId(ID), "ABC123"))
            assertEquals(0, writes)
        }

    @Test
    fun `success for an unexpected target is unknown rather than navigation to another group`() =
        runTest {
            var writes = 0
            val repository =
                object : GroupJoinRepository {
                    override suspend fun findByInviteCode(normalizedCode: String) =
                        GroupInviteLookupResult.Found(preview(ID))

                    override suspend fun join(normalizedCode: String): GroupJoinRepositoryResult {
                        writes += 1
                        return GroupJoinRepositoryResult.Joined(DomainGroupId.parse(OTHER)!!)
                    }
                }
            assertEquals(
                GroupJoinResult.OutcomeUnknown,
                ApiJoinGroupAction(repository, emptyGroups).join(GroupId(ID), "ABC123"),
            )
            assertEquals(1, writes)
        }

    private val emptyGroups =
        object : GroupListSource {
            override suspend fun loadGroups() = GroupListResult.Success(emptyList())
        }

    private fun preview(id: String) =
        GroupResponseMapper.toDomain(
            GroupPreviewResponseDto(
                groupId = id,
                name = "모임",
                memberCount = 2,
                stamp = GroupStampResponseDto("기록", "#FFFFFF", "#112233", "CIRCLE"),
            ),
        )

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
        const val OTHER = "10000000-0000-0000-0000-000000000002"
    }
}
