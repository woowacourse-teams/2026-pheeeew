package com.pheeeew.data.remote.audio

import kotlinx.serialization.Serializable

@Serializable
internal data class AudioUploadUrlRequestDto(
    val contentType: String,
    val contentLength: Long,
)

@Serializable
internal data class AudioUploadUrlResponseDto(
    val uploadId: String,
    val uploadUrl: String,
    val expiresAt: String,
    val headers: Map<String, List<String>>,
)
