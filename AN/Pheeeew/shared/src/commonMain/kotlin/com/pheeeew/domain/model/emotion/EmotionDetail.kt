package com.pheeeew.domain.model.emotion

import com.pheeeew.domain.model.group.GroupStamp

data class EmotionDetail(
    val id: Long,
    val state: EmotionState,
    val nickname: String,
    val createdAt: String,
    val memo: String?,
    val audio: EmotionPlayback?,
    val groupStamp: GroupStamp?,
    val groupId: String?,
    val isMine: Boolean,
    val reactions: List<EmotionReaction>,
)

data class EmotionPlayback(
    val url: String,
    val expiresAt: String,
)

enum class EmotionReactionType { HEART, LAUGH, CRY, DIZZY, RAGE, SKULL }

data class EmotionReaction(
    val type: EmotionReactionType,
    val count: Long,
    val selected: Boolean,
)

sealed interface EmotionDetailResult {
    data class Success(
        val detail: EmotionDetail,
    ) : EmotionDetailResult

    data object NotFound : EmotionDetailResult

    data object Unauthorized : EmotionDetailResult

    data object Unavailable : EmotionDetailResult

    data object InvalidResponse : EmotionDetailResult
}
