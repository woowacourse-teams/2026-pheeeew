package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailCopyKey
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.dominantEmotionSummary
import com.pheeeew.feature.screens.group.model.GroupId

/** AN boundary for the existing single-state POST; implementations must preserve result certainty. */
fun interface PressGroupEmotionAction {
    suspend fun press(
        groupId: GroupId,
        emotion: EmotionKind,
    ): PressGroupEmotionResult
}

sealed interface PressGroupEmotionResult {
    data class Pressed(
        val snapshot: GroupPressSnapshotUiModel,
    ) : PressGroupEmotionResult

    data object MembershipChanged : PressGroupEmotionResult

    data object NotFound : PressGroupEmotionResult

    data class RateLimited(
        val retryAfterMillis: Long?,
    ) : PressGroupEmotionResult

    data object Rejected : PressGroupEmotionResult

    data object Unavailable : PressGroupEmotionResult

    data object OutcomeUnknown : PressGroupEmotionResult
}

/**
 * Server-confirmed aggregate only. The current API provides no per-input receipt, aggregation period, or version, so
 * consumers must never use this snapshot to infer whether a particular unknown operation was accepted.
 *
 * If BE later adds those guarantees, map them here through [ApiGroupPressAction] and carry per-input receipts in
 * [PressGroupEmotionResult.Pressed] or a dedicated reconciliation result.
 */
data class GroupPressSnapshotUiModel(
    val emotionCounts: List<EmotionCountUiModel>,
    val total: Long,
) {
    init {
        require(total >= 0L) { "오늘 전체 횟수는 음수일 수 없습니다." }
        require(emotionCounts.size == EmotionKind.entries.size) { "감정 횟수는 다섯 종류여야 합니다." }
        require(emotionCounts.map { it.kind }.toSet() == EmotionKind.entries.toSet()) {
            "각 감정은 한 번씩만 포함되어야 합니다."
        }
    }
}

/** Applies only the confirmed server press snapshot and its derived summary to the current detail. */
fun GroupDetailUiModel.withPressSnapshot(snapshot: GroupPressSnapshotUiModel): GroupDetailUiModel {
    val counts = snapshot.emotionCounts.toList()
    val hasPresses = counts.any { it.count > 0L }
    val kind = if (hasPresses) GroupDetailPresentationKind.Active else GroupDetailPresentationKind.Neutral
    val summary = counts.dominantEmotionSummary(presentation.summaryMessage)
    val title = if (hasPresses) GroupDetailCopyKey.ActiveHeroTitle else GroupDetailCopyKey.NeutralHeroTitle
    val subtitle = if (hasPresses) GroupDetailCopyKey.ActiveHeroSubtitle else GroupDetailCopyKey.NeutralHeroSubtitle

    return copy(
        emotionCounts = counts,
        todayTotal = snapshot.total,
        presentation =
            presentation.copy(
                kind = kind,
                heroTitle = title,
                heroSubtitle = subtitle,
                summaryMessage = summary,
            ),
    )
}
