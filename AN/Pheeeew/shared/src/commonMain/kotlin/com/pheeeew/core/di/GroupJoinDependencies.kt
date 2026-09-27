package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.GroupJoinApi
import com.pheeeew.data.repository.GroupJoinRepositoryImpl
import com.pheeeew.feature.screens.group.data.ApiJoinGroupAction
import com.pheeeew.feature.screens.group.data.ApiLookupGroupAction
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.join.GroupJoinErrorReporter
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

/** Builds the join feature ports from the app-owned authenticated client. */
fun createGroupJoinDependencies(
    apiClient: ApiClient,
    groupListSource: GroupListSource,
    errorReporter: GroupJoinErrorReporter,
    operationKeyAllocator: GroupOperationKeyAllocator,
): GroupJoinDependencies {
    val repository = GroupJoinRepositoryImpl(GroupJoinApi(apiClient.requests))
    return GroupJoinDependencies(
        lookupGroupAction = ApiLookupGroupAction(repository),
        joinGroupAction = ApiJoinGroupAction(repository, groupListSource),
        errorReporter = errorReporter,
        operationKeyAllocator = operationKeyAllocator,
    )
}
