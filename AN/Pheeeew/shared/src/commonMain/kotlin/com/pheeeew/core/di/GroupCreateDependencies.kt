package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.GroupCreateApi
import com.pheeeew.data.repository.GroupCreateRepositoryImpl
import com.pheeeew.feature.screens.group.create.GroupCreateActions
import com.pheeeew.feature.screens.group.data.ApiCreateGroupAction
import com.pheeeew.feature.screens.group.data.GroupListCreateRecoveryAction

/** Builds the create feature ports from the app-owned, authenticated API client. */
fun createGroupCreateActions(apiClient: ApiClient): GroupCreateActions {
    val groupListSource = createGroupListSource(apiClient)
    return GroupCreateActions(
        create = ApiCreateGroupAction(GroupCreateRepositoryImpl(GroupCreateApi(apiClient.requests))),
        findCandidates = GroupListCreateRecoveryAction(groupListSource),
    )
}
