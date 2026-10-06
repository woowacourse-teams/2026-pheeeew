package com.pheeeew.domain.model.group

/** 그룹 감정 버튼의 상태별 집계입니다. 집계 기간은 이를 포함하는 응답이 정의합니다. */
enum class GroupPressState {
    FRUSTRATED,
    IRRITATED,
    EXHAUSTED,
    DISCOURAGED,
    ANGRY,
}

data class GroupPressCounts(
    val counts: Map<GroupPressState, Long>,
    val total: Long,
) {
    init {
        require(counts.keys == GroupPressState.entries.toSet()) {
            "감정별 집계를 모두 포함해야 합니다."
        }
        require(counts.values.all { it >= 0L }) { "감정 횟수는 음수일 수 없습니다." }
        require(total >= 0L) { "전체 횟수는 음수일 수 없습니다." }
    }
}

data class GroupDetail(
    val group: Group,
    val todayPresses: GroupPressCounts,
    val weeklyScore: Long,
    val weeklyRank: Int?,
) {
    init {
        require(weeklyScore >= 0L) { "이번 주 점수는 음수일 수 없습니다." }
        require(weeklyRank == null || weeklyRank > 0) { "순위는 양수이거나 미집계여야 합니다." }
    }
}
