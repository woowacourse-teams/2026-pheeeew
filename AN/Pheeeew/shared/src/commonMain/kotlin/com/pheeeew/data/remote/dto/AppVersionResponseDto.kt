package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppVersionResponseDto(
    val minSupportedVersion: String,
    val latestVersion: String,
    val storeUrl: String,
)
