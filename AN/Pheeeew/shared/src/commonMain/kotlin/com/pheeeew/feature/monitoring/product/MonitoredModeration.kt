package com.pheeeew.feature.monitoring.product

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.domain.repository.EmotionModerationRepository
import com.pheeeew.domain.repository.EmotionModerationResult

class MonitoredModeration(
    private val delegate: EmotionModerationRepository,
    monitoring: Monitoring,
) : EmotionModerationRepository {
    private val telemetry = ProductMonitoring(monitoring, "map")

    private suspend fun perform(
        name: String,
        id: Long,
        action: String,
        block: suspend () -> EmotionModerationResult,
    ) = telemetry
        .operation(
            name,
            labels("entry_key" to id.toString(), "entry_source" to "map", "action" to action),
        ).observe(::resultLabel, block)

    override suspend fun report(
        emotionId: Long,
        reason: String,
    ) = delegate.report(emotionId, reason)

    override suspend fun blockEmotion(emotionId: Long) =
        perform("emotion_block_finished", emotionId, "block_emotion") {
            delegate.blockEmotion(emotionId)
        }

    override suspend fun blockUser(emotionId: Long) =
        perform("emotion_block_finished", emotionId, "block_user") {
            delegate.blockUser(emotionId)
        }

    override suspend fun delete(emotionId: Long) =
        perform("emotion_delete_finished", emotionId, "delete") {
            delegate.delete(emotionId)
        }
}
