package com.pheeeew.feature.screens.group.detail.model

/** Selects the most pressed emotion, keeping the previous one while it remains tied for first. */
internal fun List<EmotionCountUiModel>.dominantEmotionSummary(
    previousSummary: GroupDetailCopyKey? = null,
): GroupDetailCopyKey {
    val highestCount = maxOfOrNull { it.count } ?: return GroupDetailCopyKey.SummaryNeutral
    if (highestCount == 0L) return GroupDetailCopyKey.SummaryNeutral

    val leaders = filter { it.count == highestCount }
    val previousLeader = previousSummary?.toEmotionKind()
    val selectedEmotion = leaders.firstOrNull { it.kind == previousLeader }?.kind ?: leaders.first().kind
    return selectedEmotion.toSummaryKey()
}

internal fun GroupDetailUiModel.withDominantEmotionSummary(
    previousSummary: GroupDetailCopyKey? = null,
): GroupDetailUiModel =
    copy(
        presentation =
            presentation.copy(
                summaryMessage = emotionCounts.dominantEmotionSummary(previousSummary),
            ),
    )

private fun GroupDetailCopyKey.toEmotionKind(): EmotionKind? =
    when (this) {
        GroupDetailCopyKey.SummaryBlocked -> EmotionKind.Blocked

        GroupDetailCopyKey.SummaryAnnoyed -> EmotionKind.Annoyed

        GroupDetailCopyKey.SummaryTired -> EmotionKind.Tired

        GroupDetailCopyKey.SummaryDefeated -> EmotionKind.Defeated

        GroupDetailCopyKey.SummaryAngry -> EmotionKind.Angry

        GroupDetailCopyKey.FirstStartHeroTitle,
        GroupDetailCopyKey.FirstStartHeroSubtitle,
        GroupDetailCopyKey.ActiveHeroTitle,
        GroupDetailCopyKey.ActiveHeroSubtitle,
        GroupDetailCopyKey.NeutralHeroTitle,
        GroupDetailCopyKey.NeutralHeroSubtitle,
        GroupDetailCopyKey.SummaryNeutral,
        -> null
    }

private fun EmotionKind.toSummaryKey(): GroupDetailCopyKey =
    when (this) {
        EmotionKind.Blocked -> GroupDetailCopyKey.SummaryBlocked
        EmotionKind.Annoyed -> GroupDetailCopyKey.SummaryAnnoyed
        EmotionKind.Tired -> GroupDetailCopyKey.SummaryTired
        EmotionKind.Defeated -> GroupDetailCopyKey.SummaryDefeated
        EmotionKind.Angry -> GroupDetailCopyKey.SummaryAngry
    }
