package com.pheeeew.feature.monitoring.product

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.domain.repository.BlockRepository
import com.pheeeew.domain.repository.EmotionRepository

class MonitoredBlockRepository(
    private val delegate: BlockRepository,
    monitoring: Monitoring,
) : BlockRepository {
    private val telemetry = ProductMonitoring(monitoring, "map")

    override suspend fun blockEmotion(emotionId: Long) =
        telemetry
            .operation(
                "emotion_block_finished",
                labels("entry_key" to emotionId.toString(), "entry_source" to "map", "action" to "block_emotion"),
            ).observe(::resultLabel) { delegate.blockEmotion(emotionId) }

    override suspend fun blockUser(emotionId: Long) =
        telemetry
            .operation(
                "emotion_block_finished",
                labels("entry_key" to emotionId.toString(), "entry_source" to "map", "action" to "block_user"),
            ).observe(::resultLabel) { delegate.blockUser(emotionId) }
}

class MonitoredEmotionRepository(
    private val delegate: EmotionRepository,
    monitoring: Monitoring,
) : EmotionRepository by delegate {
    private val telemetry = ProductMonitoring(monitoring, "map")

    override suspend fun delete(emotionId: Long) =
        telemetry
            .operation(
                "emotion_delete_finished",
                labels("entry_key" to emotionId.toString(), "entry_source" to "map", "action" to "delete"),
            ).observe(::resultLabel) { delegate.delete(emotionId) }
}
