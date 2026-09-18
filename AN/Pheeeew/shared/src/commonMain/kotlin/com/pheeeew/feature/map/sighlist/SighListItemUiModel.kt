package com.pheeeew.feature.map.sighlist

import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.feature.map.star.StarAgePolicy
import com.pheeeew.feature.map.star.StarAgeStage
import kotlin.time.Instant

internal const val EMPTY_SIGH_MEMO = "남긴 메모가 없습니다"

data class SighListItemUiModel(
    val id: Long,
    val nickname: String,
    val relativeTime: String,
    val memo: String,
    val hasMemo: Boolean = true,
    val starStage: StarAgeStage,
    val liked: Boolean = false,
    val likeCount: Long = 0L,
)

internal fun Sigh.toSighListItemUiModel(now: Instant): SighListItemUiModel {
    val normalizedMemo = memo?.takeIf(String::isNotBlank)

    return SighListItemUiModel(
        id = id,
        nickname = nickname,
        relativeTime = createdAt.toRelativeTime(now),
        memo = normalizedMemo ?: EMPTY_SIGH_MEMO,
        hasMemo = normalizedMemo != null,
        starStage = StarAgePolicy.stageOf(createdAt, now),
        liked = liked,
        likeCount = likeCount,
    )
}

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
