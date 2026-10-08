package com.pheeeew.feature.screens.group.detail

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlin.time.Clock

data class GroupDetailDependencies(
    val source: GroupDetailSource,
    val pressGroupEmotionAction: PressGroupEmotionAction,
    val leaveGroupAction: LeaveGroupAction,
    val errorReporter: GroupDetailErrorReporter,
    val operationKeyAllocator: GroupOperationKeyAllocator,
    val requestPolicy: GroupDetailRequestPolicy = GroupDetailRequestPolicy(),
    val monitoring: Monitoring = NoOpMonitoring,
    val activityClock: () -> Long = {
        Clock.System
            .now()
            .toEpochMilliseconds()
    },
    val emotionRankingSource: GroupDetailEmotionRankingSource = GroupDetailEmotionRankingSource.Unavailable,
    val weeklyPressCountSource: GroupDetailWeeklyPressCountSource = GroupDetailWeeklyPressCountSource.Unavailable,
)
