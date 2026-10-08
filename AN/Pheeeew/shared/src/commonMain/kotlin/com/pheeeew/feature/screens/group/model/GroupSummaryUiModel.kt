package com.pheeeew.feature.screens.group.model

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel

data class GroupSummaryUiModel(
    val id: GroupId,
    val name: String,
    val memberCount: Long,
    val weeklyStampCount: Long?,
    val stamp: StampAppearanceUiModel,
    val description: String? = null,
) {
    init {
        require(memberCount >= 0) { "멤버 수는 음수일 수 없습니다." }
        require(weeklyStampCount == null || weeklyStampCount >= 0L) { "이번 주 스탬프 수는 음수일 수 없습니다." }
    }
}
