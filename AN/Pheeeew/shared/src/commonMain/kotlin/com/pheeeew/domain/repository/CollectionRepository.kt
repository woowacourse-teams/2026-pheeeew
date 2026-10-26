package com.pheeeew.domain.repository

import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionCardReference
import com.pheeeew.domain.model.collection.CollectionHistory
import com.pheeeew.domain.model.collection.CollectionHistoryQuery
import com.pheeeew.domain.model.collection.CollectionResult
import com.pheeeew.domain.model.collection.CollectionToday
import com.pheeeew.domain.model.emotion.EmotionState

interface CollectionRepository {
    suspend fun findToday(): CollectionResult<CollectionToday>

    suspend fun claim(
        date: String,
        state: EmotionState,
    ): CollectionResult<CollectionCardReference>

    suspend fun findHistory(query: CollectionHistoryQuery): CollectionResult<CollectionHistory>

    suspend fun findCard(cardId: String): CollectionResult<CollectionCard>
}
