package com.pheeeew.feature.screens.group.detail.model

import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

data class EmotionCountUiModel(
    val kind: EmotionKind,
    val count: Long,
) {
    init {
        require(count >= 0L) { "감정 횟수는 음수일 수 없습니다." }
    }
}

sealed interface GroupRankUiModel {
    data object Unranked : GroupRankUiModel

    data object Unavailable : GroupRankUiModel

    data class Ranked(
        val value: Int,
    ) : GroupRankUiModel {
        init {
            require(value > 0) { "순위는 양수여야 합니다." }
        }
    }
}

enum class GroupDetailPresentationKind {
    FirstStart,
    Active,
}

data class GroupDetailPresentationUiModel(
    val kind: GroupDetailPresentationKind,
    val heroTitle: GroupDetailCopyKey,
    val heroSubtitle: GroupDetailCopyKey,
    val summaryMessage: GroupDetailCopyKey? = null,
) {
    init {
        val expectedTitle =
            if (kind == GroupDetailPresentationKind.FirstStart) {
                GroupDetailCopyKey.FirstStartHeroTitle
            } else {
                GroupDetailCopyKey.ActiveHeroTitle
            }
        val expectedSubtitle =
            if (kind == GroupDetailPresentationKind.FirstStart) {
                GroupDetailCopyKey.FirstStartHeroSubtitle
            } else {
                GroupDetailCopyKey.ActiveHeroSubtitle
            }
        require(heroTitle == expectedTitle && heroSubtitle == expectedSubtitle) {
            "상세 표시 상태와 헤드라인 문구가 일치해야 합니다."
        }
    }
}

/** 상세 공급자가 선택하는 문구 식별자입니다. 실제 문구는 공통 문자열 리소스에서 관리합니다. */
enum class GroupDetailCopyKey {
    FirstStartHeroTitle,
    FirstStartHeroSubtitle,
    ActiveHeroTitle,
    ActiveHeroSubtitle,
    SummaryBlocked,
    SummaryAnnoyed,
    SummaryTired,
    SummaryDefeated,
    SummaryAngry,
}

/** 상세 화면이 공급자에서 받아 표시하는 스냅샷입니다. 카운트를 다시 계산하지 않습니다. */
data class GroupDetailUiModel(
    val group: GroupSummaryUiModel,
    val emotionCounts: List<EmotionCountUiModel>,
    val todayTotal: Long,
    val weeklyScore: Long,
    val rank: GroupRankUiModel,
    val inviteCode: String,
    val presentation: GroupDetailPresentationUiModel,
) {
    init {
        require(todayTotal >= 0L) { "오늘 횟수는 음수일 수 없습니다." }
        require(weeklyScore >= 0L) { "이번 주 점수는 음수일 수 없습니다." }
        require(inviteCode.isNotBlank()) { "초대코드는 비어 있을 수 없습니다." }
        require(emotionCounts.size == EmotionKind.entries.size) { "감정 횟수는 다섯 종류여야 합니다." }
        require(emotionCounts.map { it.kind }.toSet() == EmotionKind.entries.toSet()) {
            "각 감정은 한 번씩만 포함되어야 합니다."
        }
    }
}
