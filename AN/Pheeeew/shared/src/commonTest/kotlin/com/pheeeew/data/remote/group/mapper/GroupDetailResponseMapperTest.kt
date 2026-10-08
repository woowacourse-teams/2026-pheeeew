package com.pheeeew.data.remote.group.mapper

import com.pheeeew.data.remote.group.dto.GroupDetailResponseDto
import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import com.pheeeew.domain.model.group.GroupRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupDetailResponseMapperTest {
    @Test
    fun `maps v3 metrics and every membership role`() {
        listOf("OWNER", "MEMBER", "NONE").forEach { role ->
            val detail = GroupDetailResponseMapper.toDomain(response(role))

            assertEquals(role, detail.group.role.name)
            assertEquals(42L, detail.weeklyStampCount)
            assertEquals(3, detail.weeklyStampRank)
            assertEquals(318L, detail.weeklyEmotionPressCount)
            assertEquals(5, detail.weeklyEmotionPressRank)
        }
    }

    @Test
    fun `zero counts require null ranks`() {
        val detail =
            GroupDetailResponseMapper.toDomain(
                response("NONE").copy(
                    weeklyStampCount = 0L,
                    weeklyStampRank = null,
                    weeklyEmotionPressCount = 0L,
                    weeklyEmotionPressRank = null,
                ),
            )

        assertEquals(null, detail.weeklyStampRank)
        assertEquals(null, detail.weeklyEmotionPressRank)
    }

    @Test
    fun `invalid counts and ranks fail the response contract`() {
        assertFailsWith<GroupContractException> {
            GroupDetailResponseMapper.toDomain(response("MEMBER").copy(weeklyStampCount = -1L))
        }
        assertFailsWith<GroupContractException> {
            GroupDetailResponseMapper.toDomain(response("MEMBER").copy(weeklyStampRank = 0))
        }
        assertFailsWith<GroupContractException> {
            GroupDetailResponseMapper.toDomain(response("MEMBER").copy(weeklyStampCount = 0L))
        }
        assertFailsWith<GroupContractException> {
            GroupDetailResponseMapper.toDomain(response("MEMBER").copy(weeklyEmotionPressRank = -1))
        }
    }

    private fun response(role: String) =
        GroupDetailResponseDto(
            groupId = GROUP_ID,
            name = "한숨모임",
            description = "퇴근하고 한 번씩",
            inviteCode = "ABCD1234",
            role = role,
            memberCount = 7L,
            stamp = GroupStampResponseDto("버티자", "#FFFFFF", "#4A90D9", "SCALLOP"),
            weeklyStampCount = 42L,
            weeklyStampRank = 3,
            weeklyEmotionPressCount = 318L,
            weeklyEmotionPressRank = 5,
        )

    private companion object {
        const val GROUP_ID = "0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
    }
}
