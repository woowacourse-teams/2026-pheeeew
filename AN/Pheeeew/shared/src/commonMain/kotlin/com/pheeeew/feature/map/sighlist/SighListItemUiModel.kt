package com.pheeeew.feature.map.sighlist

import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.feature.map.star.StarAgePolicy
import com.pheeeew.feature.map.star.StarAgeStage
import kotlin.time.Instant

data class SighListItemUiModel(
    val id: Long,
    val nickname: String,
    val relativeTime: String,
    val memo: String,
    val starStage: StarAgeStage,
    val liked: Boolean = false,
    val likeCount: Long = 0L,
)

internal fun Sigh.toSighListItemUiModel(now: Instant): SighListItemUiModel =
    SighListItemUiModel(
        id = id,
        nickname = nickname,
        relativeTime = createdAt.toRelativeTime(now),
        memo = memo?.takeIf(String::isNotBlank) ?: "남긴 메모가 없어요",
        starStage = StarAgePolicy.stageOf(createdAt, now),
        liked = liked,
        likeCount = likeCount,
    )

private fun Instant.toRelativeTime(now: Instant): String {
    val elapsed = (now - this).coerceAtLeast(kotlin.time.Duration.ZERO)
    val minutes = elapsed.inWholeMinutes
    val hours = elapsed.inWholeHours
    val days = elapsed.inWholeDays
    return when {
        minutes < 1 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        hours < 24 -> "${hours}시간 전"
        days < 30 -> "${days}일 전"
        else -> "${days / 30}개월 전"
    }
}
