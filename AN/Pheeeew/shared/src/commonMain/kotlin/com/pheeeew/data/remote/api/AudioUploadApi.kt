package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.AudioUploadUrlRequestDto
import com.pheeeew.data.remote.dto.AudioUploadUrlResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
internal class AudioUploadApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun requestUploadUrl(contentLength: Long): ApiResult<AudioUploadUrlResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = ApiEndpointPaths.AUDIO_UPLOAD_URL,
                kind = RequestKind.WRITE,
                body = AudioUploadUrlRequestDto(AUDIO_CONTENT_TYPE, contentLength),
                replayAfterAuthentication = true,
                monitoringEndpoint = "emotion_audio_upload_url",
            ),
        ) { it.body<AudioUploadUrlResponseDto>() }

    suspend fun upload(
        upload: AudioUploadUrlResponseDto,
        bytes: ByteArray,
    ): ApiResult<Unit> =
        requests.putSignedBinary(upload.uploadUrl, upload.headers, bytes, monitoringEndpoint = "emotion_audio_upload")

    private companion object {
        const val AUDIO_CONTENT_TYPE = "audio/mp4"
    }
}
