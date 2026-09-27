package com.pheeeew.domain.model.emotion

import com.pheeeew.domain.model.group.GroupStamp
import kotlin.time.Instant

data class EmotionBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west.isFinite() && east.isFinite() && south.isFinite() && north.isFinite())
        require(west in -180.0..180.0 && east in -180.0..180.0)
        require(south in -90.0..90.0 && north in -90.0..90.0 && south < north && west != east)
    }
}

enum class EmotionState { FRUSTRATED, IRRITATED, EXHAUSTED, DISCOURAGED, ANGRY }

enum class EmotionContentType { NONE, MEMO, AUDIO }

enum class EmotionReaction { HEART, LAUGH, CRY, DIZZY, RAGE, SKULL }

data class ReactionCount(
    val type: EmotionReaction,
    val count: Long,
    val selected: Boolean,
)

data class EmotionAudio(
    val url: String,
    val expiresAt: Instant,
)

data class Emotion(
    val id: Long,
    val state: EmotionState,
    val nickname: String,
    val createdAt: Instant,
    val isMine: Boolean,
    val contentType: EmotionContentType,
    val memo: String?,
    val reactions: List<ReactionCount>,
    val groupStamp: GroupStamp?,
    val audio: EmotionAudio?,
)

data class EmotionPage(
    val items: List<Emotion>,
    val nextCursor: String?,
)
