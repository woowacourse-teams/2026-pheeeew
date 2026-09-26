package com.pheeeew.data.remote.group

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
        )

    private fun GroupStamp.toRequestDto(): GroupStampRequestDto =
        GroupStampRequestDto(
            text = text,
            textColor = textColor.toServerValue(),
            backgroundColor = backgroundColor.toServerValue(),
            frame = frame.name,
        )
}
