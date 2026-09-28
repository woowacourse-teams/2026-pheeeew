package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.audio.AudioUploadApi
import com.pheeeew.data.remote.emotion.EmotionRegistrationApi
import com.pheeeew.data.remote.emotion.toRequestDto
import com.pheeeew.data.repository.audio.MAX_AUDIO_UPLOAD_BYTES
import com.pheeeew.data.repository.audio.readRecordingAudioFile
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EmotionRegistrationRepositoryImpl(
    private val api: EmotionRegistrationApi,
    private val audioUploadApi: AudioUploadApi,
) : EmotionRegistrationRepository {
    private data class UploadedAudio(
        val requestId: String,
        val filePath: String,
        val uploadId: String,
    )

    private var uploadedAudio: UploadedAudio? = null

    override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult {
        val audioUploadId =
            when (val content = registration.content) {
                is EmotionRegistrationContent.Audio -> {
                    val cached =
                        uploadedAudio?.takeIf {
                            it.requestId == registration.requestId && it.filePath == content.filePath
                        }
                    if (cached != null) {
                        cached.uploadId
                    } else {
                        val bytes =
                            try {
                                withContext(Dispatchers.Default) { readRecordingAudioFile(content.filePath) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            } ?: return EmotionRegistrationResult.AudioUnavailable
                        if (bytes.isEmpty() || bytes.size.toLong() > MAX_AUDIO_UPLOAD_BYTES) {
                            return EmotionRegistrationResult.AudioUnavailable
                        }
                        val upload =
                            when (val result = audioUploadApi.requestUploadUrl(bytes.size.toLong())) {
                                is ApiResult.Success -> result.value
                                is ApiResult.Failure -> return EmotionRegistrationResult.AudioUploadFailed
                            }
                        if (upload.uploadId.isBlank()) return EmotionRegistrationResult.AudioUploadFailed
                        when (audioUploadApi.upload(upload, bytes)) {
                            is ApiResult.Success -> Unit
                            is ApiResult.Failure -> return EmotionRegistrationResult.AudioUploadFailed
                        }
                        UploadedAudio(registration.requestId, content.filePath, upload.uploadId)
                            .also { uploadedAudio = it }
                            .uploadId
                    }
                }

                else -> {
                    uploadedAudio = null
                    null
                }
            }
        val request = registration.toRequestDto(audioUploadId) ?: return EmotionRegistrationResult.AudioUnavailable
        return when (val result = api.register(request)) {
            is ApiResult.Success -> {
                if (result.value.id > 0) {
                    uploadedAudio = null
                    EmotionRegistrationResult.Success(result.value.id)
                } else {
                    EmotionRegistrationResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                EmotionRegistrationResult.Unavailable
            }
        }
    }
}
