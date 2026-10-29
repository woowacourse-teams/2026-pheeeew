package com.pheeeew.core.di

import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase

data class EmotionMapDependencies(
    val findPage: FindEmotionMapPageUseCase,
    val findSnapshot: FindEmotionMapSnapshotUseCase,
    val findRegions: FindEmotionRegionsUseCase,
    val findRegionSnapshot: FindEmotionRegionSnapshotUseCase,
    val invalidateSession: () -> Unit = {},
    val invalidateRegions: () -> Unit = {},
)
