package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.press.EmotionPressApi
import com.pheeeew.data.repository.press.PressRepositoryImpl
import com.pheeeew.domain.repository.press.PressRepository

fun createPressRepository(apiClient: ApiClient): PressRepository =
    PressRepositoryImpl(EmotionPressApi(apiClient.requests))
