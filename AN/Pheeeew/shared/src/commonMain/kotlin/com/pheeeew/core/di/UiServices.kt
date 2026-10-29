package com.pheeeew.core.di

import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl

/** Shared map caches for one root UI, never for the whole process. */
class UiServices(
    val map: EmotionMapRepositoryImpl,
    val regions: EmotionRegionMapRepositoryImpl,
)
