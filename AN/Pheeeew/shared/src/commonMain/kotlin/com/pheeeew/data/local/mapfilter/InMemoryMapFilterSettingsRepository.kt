package com.pheeeew.data.local.mapfilter

import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryMapFilterSettingsRepository : MapFilterSettingsRepository {
    private val settings = MutableStateFlow(MapFilterSettings.DEFAULT)

    override suspend fun read(): MapFilterSettings = settings.value

    override suspend fun save(settings: MapFilterSettings) {
        this.settings.value = settings
    }
}
