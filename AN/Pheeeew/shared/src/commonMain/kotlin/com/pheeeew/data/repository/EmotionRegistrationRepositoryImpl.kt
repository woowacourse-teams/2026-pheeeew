package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.domain.repository.AudioUploadObservation
import com.pheeeew.domain.repository.AudioUploadOutcome
import com.pheeeew.domain.repository.AudioUploadStage
import kotlin.time.TimeSource
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

    override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult =
        register(registration) { }

    override suspend fun register(
        registration: EmotionRegistration,
        onAudioUploadFinished: (AudioUploadObservation) -> Unit,
    ): EmotionRegistrationResult {
        val audioUploadId = when (val content = registration.content) {
            is EmotionRegistrationContent.Audio -> {
                val started = TimeSource.Monotonic.markNow()
                var outcome = AudioUploadOutcome.FAILED
                var stage = AudioUploadStage.FILE
                var cacheReused = false
                try {
                    val cached = uploadedAudio?.takeIf {
                        it.requestId == registration.requestId && it.filePath == content.filePath
                    }
                    if (cached != null) {
                        cacheReused = true
                        outcome = AudioUploadOutcome.SUCCESS
                        stage = AudioUploadStage.NONE
                        cached.uploadId
                    } else {
                        val bytes = try {
                            withContext(Dispatchers.Default) { readRecordingAudioFile(content.filePath) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        } ?: return EmotionRegistrationResult.AudioUnavailable
                        if (bytes.isEmpty() || bytes.size.toLong() > MAX_AUDIO_UPLOAD_BYTES) {
                            return EmotionRegistrationResult.AudioUnavailable
                        }
                        stage = AudioUploadStage.URL_REQUEST
                        val upload = when (val result = audioUploadApi.requestUploadUrl(bytes.size.toLong())) {
                            is ApiResult.Success -> result.value
                            is ApiResult.Failure -> {
                                outcome = result.reason.uploadOutcome()
                                return EmotionRegistrationResult.AudioUploadFailed
                            }
                        }
                        if (upload.uploadId.isBlank()) return EmotionRegistrationResult.AudioUploadFailed
                        stage = AudioUploadStage.UPLOAD
                        when (val result = audioUploadApi.upload(upload, bytes)) {
                            is ApiResult.Success -> Unit
                            is ApiResult.Failure -> {
                                outcome = result.reason.uploadOutcome()
                                return EmotionRegistrationResult.AudioUploadFailed
                            }
                        }
                        outcome = AudioUploadOutcome.SUCCESS
                        stage = AudioUploadStage.NONE
                        UploadedAudio(registration.requestId, content.filePath, upload.uploadId)
                            .also { uploadedAudio = it }.uploadId
                    }
                } catch (cancelled: CancellationException) {
                    outcome = AudioUploadOutcome.CANCELLED
                    throw cancelled
                } catch (_: Exception) {
                    outcome = AudioUploadOutcome.UNKNOWN
                    return EmotionRegistrationResult.AudioUploadFailed
                } finally {
                    val observation = AudioUploadObservation(
                        outcome, stage, cacheReused, started.elapsedNow().inWholeMilliseconds,
                    )
                    runCatching { onAudioUploadFinished(observation) }
                }
            }
            else -> { uploadedAudio = null; null }
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
                if (result.reason.certainty() == MutationCertainty.NOT_APPLIED) {
                    EmotionRegistrationResult.Rejected
                } else {
                    EmotionRegistrationResult.Unavailable
                }
            }
        }
    }
}

private fun NetworkFailure.certainty(): MutationCertainty = when (this) {
    is NetworkFailure.SessionUnavailable -> mutationCertainty
    is NetworkFailure.SessionProviderFailed -> mutationCertainty
    is NetworkFailure.HttpStatus -> mutationCertainty
    is NetworkFailure.Transport -> mutationCertainty
    is NetworkFailure.Unexpected -> mutationCertainty
    is NetworkFailure.Contract -> mutationCertainty
}

private fun NetworkFailure.uploadOutcome(): AudioUploadOutcome =
    if (certainty() == MutationCertainty.NOT_APPLIED) AudioUploadOutcome.FAILED else AudioUploadOutcome.UNKNOWN
