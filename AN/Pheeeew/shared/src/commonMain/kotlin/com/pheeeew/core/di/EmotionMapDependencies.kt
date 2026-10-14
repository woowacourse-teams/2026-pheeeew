package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.emotion.EmotionMapApi
import com.pheeeew.data.remote.emotion.EmotionRegionMapApi
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.repository.EmotionRegionMapRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase

data class EmotionMapDependencies(
    val findPage: FindEmotionMapPageUseCase,
    val findSnapshot: FindEmotionMapSnapshotUseCase,
    val findRegions: FindEmotionRegionsUseCase,
    val findRegionSnapshot: FindEmotionRegionSnapshotUseCase,
)

fun createEmotionMapDependencies(apiClient: ApiClient): EmotionMapDependencies {
    val repository: EmotionMapRepository = EmotionMapRepositoryImpl(EmotionMapApi(apiClient.requests))
    val regionRepository: EmotionRegionMapRepository =
        EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(apiClient.requests))
    return EmotionMapDependencies(
        findPage = FindEmotionMapPageUseCase(repository),
        findSnapshot = FindEmotionMapSnapshotUseCase(repository),
        findRegions = FindEmotionRegionsUseCase(regionRepository),
        findRegionSnapshot = FindEmotionRegionSnapshotUseCase(regionRepository),
    )
}
