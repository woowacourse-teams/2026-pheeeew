package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.MapFilterSettings

interface MapFilterSettingsRepository {
    suspend fun read(): MapFilterSettings

    suspend fun save(settings: MapFilterSettings)
}
