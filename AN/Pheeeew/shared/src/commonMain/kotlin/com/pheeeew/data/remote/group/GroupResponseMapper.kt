package com.pheeeew.data.remote.group

import com.pheeeew.domain.model.group.Group
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupPreview
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor

class GroupContractException(
    val field: String,
) : IllegalArgumentException("그룹 응답 계약을 확인할 수 없습니다: $field")

object GroupResponseMapper {
    fun toDomain(dto: GroupResponseDto): Group =
        Group(
            id = parseId(dto.groupId),
            name = dto.name,
            description = dto.description,
            inviteCode = dto.inviteCode,
            role = parseRole(dto.role),
            memberCount = dto.memberCount,
            stamp = dto.stamp.toDomain(),
        )

    fun toDomain(dto: GroupPreviewResponseDto): GroupPreview =
        GroupPreview(
            id = parseId(dto.groupId),
            name = dto.name,
            description = dto.description,
            memberCount = dto.memberCount,
            stamp = dto.stamp.toDomain(),
        )

    fun GroupStamp.toResponseDto(): GroupStampResponseDto =
        GroupStampResponseDto(
            text = text,
            textColor = textColor.toServerValue(),
            backgroundColor = backgroundColor.toServerValue(),
            frame = frame.name,
        )

    private fun GroupStampResponseDto.toDomain(): GroupStamp =
        GroupStamp(
            text = text,
            textColor = StampColor.parseServerValue(textColor) ?: throw GroupContractException("stamp.textColor"),
            backgroundColor =
                StampColor.parseServerValue(backgroundColor)
                    ?: throw GroupContractException("stamp.backgroundColor"),
            frame =
                GroupStampFrame.entries.firstOrNull { it.name == frame }
                    ?: throw GroupContractException("stamp.frame"),
        )

    private fun parseId(value: String): GroupId = GroupId.parse(value) ?: throw GroupContractException("groupId")

    private fun parseRole(value: String): GroupRole =
        GroupRole.entries.firstOrNull { it.name == value }
            ?: throw GroupContractException("role")
}
