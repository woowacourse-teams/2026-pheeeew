package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.emotion.EmotionMapApi
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase

data class EmotionMapDependencies(
    val findPage: FindEmotionMapPageUseCase,
    val findSnapshot: FindEmotionMapSnapshotUseCase,
)

fun createEmotionMapDependencies(apiClient: ApiClient): EmotionMapDependencies {
    val repository: EmotionMapRepository = EmotionMapRepositoryImpl(EmotionMapApi(apiClient.requests))
    return EmotionMapDependencies(
        findPage = FindEmotionMapPageUseCase(repository),
        findSnapshot = FindEmotionMapSnapshotUseCase(repository),
    )
}
