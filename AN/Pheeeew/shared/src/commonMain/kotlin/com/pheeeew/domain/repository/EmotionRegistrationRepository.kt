package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult

interface EmotionRegistrationRepository {
    suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult

    suspend fun register(
        registration: EmotionRegistration,
        onAudioUploadFinished: (AudioUploadObservation) -> Unit,
    ): EmotionRegistrationResult = register(registration)
}

/** Safe operation diagnostics only: no audio paths, bytes, signed URLs or exception messages. */
data class AudioUploadObservation(
    val outcome: AudioUploadOutcome,
    val failureStage: AudioUploadStage,
    val cacheReused: Boolean,
    val durationMs: Long,
)

enum class AudioUploadOutcome { SUCCESS, FAILED, UNKNOWN, CANCELLED }

enum class AudioUploadStage { NONE, FILE, URL_REQUEST, UPLOAD }
