package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

data class GroupDetailDependencies(
    val source: GroupDetailSource,
    val emotionTapAction: EmotionTapAction,
    val leaveGroupAction: LeaveGroupAction,
    val errorReporter: GroupDetailErrorReporter,
    val operationKeyAllocator: GroupOperationKeyAllocator,
    val requestPolicy: GroupDetailRequestPolicy = GroupDetailRequestPolicy(),
)
