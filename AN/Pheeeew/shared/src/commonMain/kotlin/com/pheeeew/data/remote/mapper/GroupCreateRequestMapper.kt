package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.GroupCreateRequestDto
import com.pheeeew.data.remote.dto.GroupStampRequestDto
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.repository.GroupCreateCommand
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object GroupCreateRequestMapper {
    fun toRequestDto(command: GroupCreateCommand): GroupCreateRequestDto =
        GroupCreateRequestDto(
            name = command.name,
            description = command.description?.let(::JsonPrimitive) ?: JsonNull,
            stamp = command.stamp.toRequestDto(),
            joinType = command.joinType.name,
        )

    private fun GroupStamp.toRequestDto(): GroupStampRequestDto =
        GroupStampRequestDto(
            text = text,
            textColor = textColor.toServerValue(),
            backgroundColor = backgroundColor.toServerValue(),
            frame = frame.name,
        )
}
