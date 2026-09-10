package com.pheeeew.data.remote.sigh.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighV2PropertiesDto(
    val createdAt: String,
    val memo: String? = null,
    val nickname: String,
)
