package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.emotion.EmotionDetailApi
import com.pheeeew.data.remote.emotion.EmotionReactionApi
import com.pheeeew.data.repository.EmotionRepositoryImpl
import com.pheeeew.data.repository.audio.EmotionAudioRepositoryImpl
import com.pheeeew.domain.repository.EmotionDetailRepository
import com.pheeeew.domain.repository.audio.EmotionAudioRepository

fun createEmotionDetailRepository(client: ApiClient): EmotionDetailRepository =
    EmotionRepositoryImpl(EmotionDetailApi(client.requests), EmotionReactionApi(client.requests))

fun createEmotionAudioRepository(): EmotionAudioRepository = EmotionAudioRepositoryImpl()
