package com.pheeeew.data.remote.group

import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GroupResponseMapperTest {
    @Test
    fun `6자리와 8자리 서버 색상을 alpha 표현까지 왕복한다`() {
        val opaqueRgb = assertNotNull(StampColor.parseServerValue("#A1B2C3"))
        val explicitOpaqueArgb = assertNotNull(StampColor.parseServerValue("#A1B2C3FF"))
        val translucentRgba = assertNotNull(StampColor.parseServerValue("#A1B2C37F"))

        assertEquals(0xFFA1B2C3L, opaqueRgb.argb)
        assertEquals("#A1B2C3", opaqueRgb.toServerValue())
        assertEquals("#A1B2C3FF", explicitOpaqueArgb.toServerValue())
        assertEquals(0x7FA1B2C3L, translucentRgba.argb)
        assertEquals("#A1B2C37F", translucentRgba.toServerValue())
    }

    @Test
    fun `서버가 정의한 모든 stamp frame을 domain frame으로 읽는다`() {
        GroupStampFrame.entries.forEach { frame ->
            val mapped = GroupResponseMapper.toDomain(groupDto(frame = frame.name))

            assertEquals(frame, mapped.stamp.frame)
            assertEquals(Long.MAX_VALUE, mapped.memberCount)
        }
    }

    @Test
    fun `정의되지 않은 stamp frame은 contract 오류로 처리한다`() {
        assertFailsWith<GroupContractException> {
            GroupResponseMapper.toDomain(groupDto(frame = "FUTURE_FRAME"))
        }
    }

    @Test
    fun `빈 group name은 field가 포함된 contract 오류로 처리한다`() {
        val exception =
            assertFailsWith<GroupContractException> {
                GroupResponseMapper.toDomain(groupDto(name = ""))
            }

        assertEquals("name", exception.field)
    }

    @Test
    fun `빈 group preview name은 field가 포함된 contract 오류로 처리한다`() {
        val exception =
            assertFailsWith<GroupContractException> {
                GroupResponseMapper.toDomain(previewDto(name = ""))
            }

        assertEquals("name", exception.field)
    }

    @Test
    fun `음수 group member count는 field가 포함된 contract 오류로 처리한다`() {
        val exception =
            assertFailsWith<GroupContractException> {
                GroupResponseMapper.toDomain(groupDto(memberCount = -1L))
            }

        assertEquals("memberCount", exception.field)
    }

    @Test
    fun `음수 group preview member count는 field가 포함된 contract 오류로 처리한다`() {
        val exception =
            assertFailsWith<GroupContractException> {
                GroupResponseMapper.toDomain(previewDto(memberCount = -1L))
            }

        assertEquals("memberCount", exception.field)
    }

    private fun groupDto(
        frame: String = "CIRCLE",
        name: String = "test group",
        memberCount: Long = Long.MAX_VALUE,
    ) = GroupResponseDto(
        groupId = "123e4567-e89b-12d3-a456-426614174000",
        name = name,
        description = null,
        inviteCode = "invite",
        role = "OWNER",
        memberCount = memberCount,
        stamp = stampDto(frame),
    )

    private fun previewDto(
        name: String = "test group",
        memberCount: Long = Long.MAX_VALUE,
    ) = GroupPreviewResponseDto(
        groupId = "123e4567-e89b-12d3-a456-426614174000",
        name = name,
        description = null,
        memberCount = memberCount,
        stamp = stampDto("CIRCLE"),
    )

    private fun stampDto(frame: String) =
        GroupStampResponseDto(
            text = "hello",
            textColor = "#112233",
            backgroundColor = "#AABBCC80",
            frame = frame,
        )
}
