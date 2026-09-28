package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.repository.EmotionMapRepository

class FindEmotionMapSnapshotUseCase(
    private val repository: EmotionMapRepository,
) {
    operator fun invoke(
        bounds: EmotionMapBounds,
        groupId: String? = null,
    ): EmotionMapSnapshot? {
        if (!bounds.isValid()) return null
        return repository.findSnapshot(bounds, groupId)
    }
}
