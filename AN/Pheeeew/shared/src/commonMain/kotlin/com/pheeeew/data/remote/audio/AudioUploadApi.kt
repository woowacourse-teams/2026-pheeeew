package com.pheeeew.data.remote.audio

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

internal class AudioUploadApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun requestUploadUrl(contentLength: Long): ApiResult<AudioUploadUrlResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "/api/v1/audio-uploads",
                kind = RequestKind.WRITE,
                body = AudioUploadUrlRequestDto(AUDIO_CONTENT_TYPE, contentLength),
                replayAfterAuthentication = true,
            ),
        ) { it.body<AudioUploadUrlResponseDto>() }

    suspend fun upload(
        upload: AudioUploadUrlResponseDto,
        bytes: ByteArray,
    ): ApiResult<Unit> = requests.putSignedBinary(upload.uploadUrl, upload.headers, bytes)

    private companion object {
        const val AUDIO_CONTENT_TYPE = "audio/mp4"
    }
}
