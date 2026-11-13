package com.pheeeew.feature.screens.map.filter

import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.repository.EmotionResult

interface MapFilterListRefresher {
    fun isVisible(): Boolean

    suspend fun loadFirstPage(filter: EmotionSearchFilter): EmotionResult<EmotionPage>

    fun commitFirstPage(
        settings: MapFilterSettings,
        page: EmotionPage,
    )

    fun setAppliedSettings(settings: MapFilterSettings)
}
