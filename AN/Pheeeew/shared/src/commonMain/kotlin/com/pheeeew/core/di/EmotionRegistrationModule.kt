package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.audio.AudioUploadApi
import com.pheeeew.data.remote.emotion.EmotionRegistrationApi
import com.pheeeew.data.repository.EmotionRegistrationRepositoryImpl
import com.pheeeew.domain.repository.EmotionRegistrationRepository

fun createEmotionRegistrationRepository(client: ApiClient): EmotionRegistrationRepository =
    EmotionRegistrationRepositoryImpl(
        EmotionRegistrationApi(client.requests),
        AudioUploadApi(client.requests),
    )
