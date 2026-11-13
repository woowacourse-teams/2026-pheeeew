package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.repository.EmotionMapRepository

class FindEmotionMapSnapshotUseCase(
    private val repository: EmotionMapRepository,
) {
    operator fun invoke(bounds: EmotionMapBounds): EmotionMapSnapshot? {
        if (!bounds.isValid()) return null
        return repository.findSnapshot(bounds)
    }

    fun withFilter(
        bounds: EmotionMapBounds,
        filter: EmotionSearchFilter,
    ): EmotionMapSnapshot? = if (!bounds.isValid()) null else repository.findFilteredSnapshot(bounds, filter)
}
