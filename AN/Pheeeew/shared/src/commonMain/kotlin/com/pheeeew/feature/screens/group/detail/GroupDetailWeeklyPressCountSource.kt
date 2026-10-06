package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupId

/** Loads the current week's group-wide press count independently from daily detail snapshots. */
fun interface GroupDetailWeeklyPressCountSource {
    suspend fun load(groupId: GroupId): GroupDetailWeeklyPressCountResult

    companion object {
        val Unavailable = GroupDetailWeeklyPressCountSource { GroupDetailWeeklyPressCountResult.Unavailable }
    }
}

sealed interface GroupDetailWeeklyPressCountResult {
    data class Loaded(
        val total: Long,
    ) : GroupDetailWeeklyPressCountResult {
        init {
            require(total >= 0L) { "이번 주 입력 횟수는 음수일 수 없습니다." }
        }
    }

    data object Unavailable : GroupDetailWeeklyPressCountResult
}
