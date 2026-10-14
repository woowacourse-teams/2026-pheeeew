package com.pheeeew.feature.screens.group.mapper

import com.pheeeew.data.remote.group.dto.GroupPreviewResponseDto
import com.pheeeew.data.remote.group.dto.GroupResponseDto
import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import com.pheeeew.data.remote.group.mapper.GroupResponseMapper
import com.pheeeew.feature.screens.group.model.GroupJoinType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupSummaryUiMapperTest {
    @Test
    fun `real group preserves admission policy and preview leaves it unknown`() {
        val stamp = GroupStampResponseDto("기록", "#FFFFFF", "#112233", "CIRCLE")
        GroupJoinType.entries.forEach { type ->
            val group =
                GroupResponseMapper.toDomain(
                    GroupResponseDto(
                        groupId = ID,
                        name = "기록모임",
                        inviteCode = "ABC123",
                        role = "MEMBER",
                        joinType = type.name,
                        memberCount = 3,
                        stamp = stamp,
                    ),
                )
            assertEquals(type, group.toSummaryUiModel().joinType)
            assertEquals(type, type.toDomainJoinType().toUiJoinType())
        }
        val preview =
            GroupResponseMapper.toDomain(
                GroupPreviewResponseDto(groupId = ID, name = "기록모임", memberCount = 3, stamp = stamp),
            )
        assertNull(preview.toSummaryUiModel().joinType)
    }

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
    }
}
