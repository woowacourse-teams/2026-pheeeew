package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.repository.EmotionRegionMapRepository

class FindEmotionRegionSnapshotUseCase(
    private val repository: EmotionRegionMapRepository,
) {
    operator fun invoke(
        level: EmotionRegionLevel,
        groupId: String? = null,
        filter: EmotionSearchFilter? = null,
    ): List<EmotionRegion>? = repository.findSnapshot(level, groupId, filter)
}
