package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.PressRankingApi
import com.pheeeew.data.remote.group.api.GroupDetailApi
import com.pheeeew.data.remote.group.api.GroupPressApi
import com.pheeeew.data.repository.PressRankingRepositoryImpl
import com.pheeeew.data.repository.group.GroupDetailRepositoryImpl
import com.pheeeew.data.repository.group.GroupPressRepositoryImpl
import com.pheeeew.feature.screens.group.adapter.ApiGroupDetailEmotionRankingSource
import com.pheeeew.feature.screens.group.adapter.ApiGroupDetailSource
import com.pheeeew.feature.screens.group.adapter.ApiGroupPressAction
import com.pheeeew.feature.screens.group.adapter.ApiLeaveGroupAction
import com.pheeeew.feature.screens.group.detail.GroupDetailDependencies
import com.pheeeew.feature.screens.group.detail.GroupDetailErrorReporter
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

/** Builds API-backed group detail, press, and leave ports from the app-owned authenticated client. */
fun createGroupDetailDependencies(
    apiClient: ApiClient,
    errorReporter: GroupDetailErrorReporter,
    operationKeyAllocator: GroupOperationKeyAllocator,
): GroupDetailDependencies {
    val repository = GroupDetailRepositoryImpl(GroupDetailApi(apiClient.requests))
    val pressRepository = GroupPressRepositoryImpl(GroupPressApi(apiClient.requests))
    return GroupDetailDependencies(
        source = ApiGroupDetailSource(repository),
        pressGroupEmotionAction = ApiGroupPressAction(pressRepository),
        leaveGroupAction = ApiLeaveGroupAction(repository),
        monitoring = apiClient.monitoring,
        errorReporter = errorReporter,
        operationKeyAllocator = operationKeyAllocator,
        emotionRankingSource =
            ApiGroupDetailEmotionRankingSource(
                PressRankingRepositoryImpl(PressRankingApi(apiClient.requests)),
            ),
    )
}
