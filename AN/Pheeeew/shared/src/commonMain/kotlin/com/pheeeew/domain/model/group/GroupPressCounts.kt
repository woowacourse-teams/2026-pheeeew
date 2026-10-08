package com.pheeeew.domain.model.group

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
            "오늘의 감정별 집계를 모두 포함해야 합니다."
        }
        require(counts.values.all { it >= 0L }) { "감정 횟수는 음수일 수 없습니다." }
        require(total >= 0L) { "오늘 전체 횟수는 음수일 수 없습니다." }
    }
}
