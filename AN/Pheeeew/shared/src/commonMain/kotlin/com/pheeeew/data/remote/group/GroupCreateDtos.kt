package com.pheeeew.data.remote.group

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GroupCreateRequestDto(
    val name: String,
    /** JsonNull is intentional: the create contract asks for an explicit null for an empty description. */
    val description: JsonElement,
    val stamp: GroupStampRequestDto,
)

@Serializable
data class GroupStampRequestDto(
    val text: String,
    val textColor: String,
    val backgroundColor: String,
    val frame: String,
)

/** The create flow only trusts the server-issued identifier; detail is fetched from its own endpoint. */
@Serializable
data class GroupCreateResponseDto(
    val groupId: String,
)
