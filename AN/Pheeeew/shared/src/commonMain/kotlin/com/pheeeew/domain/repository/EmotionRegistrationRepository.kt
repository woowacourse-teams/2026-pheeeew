package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult

interface EmotionRegistrationRepository {
    suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult
}
