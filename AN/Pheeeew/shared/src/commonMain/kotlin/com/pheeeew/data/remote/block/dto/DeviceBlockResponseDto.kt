package com.pheeeew.data.remote.block.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceBlockResponseDto(
    val blockId: Long,
    val sighId: Long,
    val nickname: String,
    val memo: String? = null,
    val createdAt: String,
)
