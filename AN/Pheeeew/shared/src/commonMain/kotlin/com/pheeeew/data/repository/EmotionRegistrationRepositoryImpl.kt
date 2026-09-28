package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.emotion.EmotionRegistrationApi
import com.pheeeew.data.remote.emotion.toRequestDto
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.EmotionRegistrationRepository

internal class EmotionRegistrationRepositoryImpl(
    private val api: EmotionRegistrationApi,
) : EmotionRegistrationRepository {
    override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult {
        val request = registration.toRequestDto() ?: return EmotionRegistrationResult.AudioUnavailable
        return when (val result = api.register(request)) {
            is ApiResult.Success -> {
                if (result.value.id > 0) {
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
