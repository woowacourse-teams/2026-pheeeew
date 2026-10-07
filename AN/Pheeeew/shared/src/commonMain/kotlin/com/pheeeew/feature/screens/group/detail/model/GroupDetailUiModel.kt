package com.pheeeew.feature.screens.group.detail.model

import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

data class GroupDetailUiModel(
    val group: GroupSummaryUiModel,
    val role: GroupRole,
    val inviteCode: String,
    val weeklyStampCount: Long,
    val weeklyStampRank: Int?,
    val weeklyEmotionPressCount: Long,
    val weeklyEmotionPressRank: Int?,
) {
    init {
        require(inviteCode.isNotBlank()) { "초대코드는 비어 있을 수 없습니다." }
        require(weeklyStampCount >= 0L) { "이번 주 스탬프 수는 음수일 수 없습니다." }
        require(weeklyStampRank == null || weeklyStampRank > 0) { "스탬프 순위는 양수이거나 미집계여야 합니다." }
        require(weeklyStampCount > 0L || weeklyStampRank == null) { "스탬프 수가 0이면 순위는 미집계여야 합니다." }
        require(weeklyEmotionPressCount >= 0L) { "이번 주 프레스 수는 음수일 수 없습니다." }
        require(weeklyEmotionPressRank == null || weeklyEmotionPressRank > 0) {
            "프레스 순위는 양수이거나 미집계여야 합니다."
        }
        require(weeklyEmotionPressCount > 0L || weeklyEmotionPressRank == null) {
            "프레스 수가 0이면 순위는 미집계여야 합니다."
        }
    }
}
