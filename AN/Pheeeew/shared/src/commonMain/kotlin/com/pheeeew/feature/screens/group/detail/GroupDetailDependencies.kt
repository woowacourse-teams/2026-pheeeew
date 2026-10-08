package com.pheeeew.feature.screens.group.detail

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.repository.emotion.EmotionRepository
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

data class GroupDetailDependencies(
    val source: GroupDetailSource,
    val emotionRepository: EmotionRepository,
    val leaveGroupAction: LeaveGroupAction,
    val errorReporter: GroupDetailErrorReporter,
    val operationKeyAllocator: GroupOperationKeyAllocator,
    val requestPolicy: GroupDetailRequestPolicy = GroupDetailRequestPolicy(),
    val monitoring: Monitoring = NoOpMonitoring,
)
