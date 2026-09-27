package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.api.GroupDetailApi
import com.pheeeew.data.repository.group.GroupDetailRepositoryImpl
import com.pheeeew.feature.screens.group.adapter.ApiDetailEmotionTapDisabled
import com.pheeeew.feature.screens.group.adapter.ApiGroupDetailSource
import com.pheeeew.feature.screens.group.adapter.ApiLeaveGroupAction
import com.pheeeew.feature.screens.group.detail.GroupDetailDependencies
import com.pheeeew.feature.screens.group.detail.GroupDetailErrorReporter
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

/** Builds API-backed group detail ports; press submission remains disabled until its own API branch. */
fun createGroupDetailDependencies(
    apiClient: ApiClient,
    errorReporter: GroupDetailErrorReporter,
    operationKeyAllocator: GroupOperationKeyAllocator,
): GroupDetailDependencies {
    val repository = GroupDetailRepositoryImpl(GroupDetailApi(apiClient.requests))
    return GroupDetailDependencies(
        source = ApiGroupDetailSource(repository),
        emotionTapAction = ApiDetailEmotionTapDisabled,
        leaveGroupAction = ApiLeaveGroupAction(repository),
        errorReporter = errorReporter,
        operationKeyAllocator = operationKeyAllocator,
        emotionTapsEnabled = false,
    )
}
