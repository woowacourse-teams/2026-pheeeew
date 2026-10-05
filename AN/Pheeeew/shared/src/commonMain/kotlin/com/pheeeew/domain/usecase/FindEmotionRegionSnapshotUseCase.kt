package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.repository.EmotionRegionMapRepository

class FindEmotionRegionSnapshotUseCase(private val repository: EmotionRegionMapRepository) {
    operator fun invoke(bounds: EmotionMapBounds, level: EmotionRegionLevel, groupId: String? = null): List<EmotionRegion>? {
        if (!bounds.isValid()) return null
        return repository.findSnapshot(bounds, level, groupId)
    }
}
