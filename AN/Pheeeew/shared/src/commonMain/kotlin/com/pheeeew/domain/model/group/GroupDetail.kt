package com.pheeeew.domain.model.group

/** 서버가 오늘 집계한 감정 버튼 상태입니다. */
enum class GroupPressState {
    FRUSTRATED,
    IRRITATED,
    EXHAUSTED,
    DISCOURAGED,
    ANGRY,
}

/** 이번 요청에서 더할 감정별 횟수입니다. 누적 점수를 나타내지 않습니다. */
data class GroupPressIncrement(
    val state: GroupPressState,
    val count: Int,
) {
    init {
        require(count > 0) { "감정 증가량은 양수여야 합니다." }
    }
}

/** 서버 `/presses` 목록 요청의 제한을 보장하는 값입니다. */
data class GroupPressBatch(
    val increments: List<GroupPressIncrement>,
) {
    init {
        require(increments.size in 1..GroupPressState.entries.size) { "감정 입력은 1~5종이어야 합니다." }
        require(increments.map { it.state }.toSet().size == increments.size) { "감정 상태는 중복될 수 없습니다." }
        require(increments.sumOf { it.count } <= MAX_PRESS_COUNT_PER_REQUEST) {
            "요청 하나의 총 감정 입력은 100회를 넘을 수 없습니다."
        }
    }

    companion object {
        const val MAX_PRESS_COUNT_PER_REQUEST = 100
    }
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
