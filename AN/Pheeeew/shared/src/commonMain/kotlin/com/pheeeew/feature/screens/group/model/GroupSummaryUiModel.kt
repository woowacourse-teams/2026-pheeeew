package com.pheeeew.feature.screens.group.model

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel

/** 그룹 목록과 검색 결과에서 공통으로 쓰는 표시 모델입니다. 화면별 로딩·오류 상태는 각 화면이 관리합니다. */
data class GroupSummaryUiModel(
    val id: GroupId,
    val name: String,
    val memberCount: Long,
    val weeklyStampCount: Long,
    val stamp: StampAppearanceUiModel,
) {
    init {
        require(memberCount >= 0) { "멤버 수는 음수일 수 없습니다." }
        require(weeklyStampCount >= 0L) { "이번 주 스탬프 수는 음수일 수 없습니다." }
    }
}
