package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.api.GroupCreateApi
import com.pheeeew.data.repository.group.GroupCreateRepositoryImpl
import com.pheeeew.feature.screens.group.adapter.ApiCreateGroupAction
import com.pheeeew.feature.screens.group.adapter.GroupListCreateRecoveryAction
import com.pheeeew.feature.screens.group.create.GroupCreateActions

/** Builds the create feature ports from the app-owned, authenticated API client. */
fun createGroupCreateActions(apiClient: ApiClient): GroupCreateActions {
    val groupListSource = createGroupListSource(apiClient)
    return GroupCreateActions(
        create = ApiCreateGroupAction(GroupCreateRepositoryImpl(GroupCreateApi(apiClient.requests))),
        findCandidates = GroupListCreateRecoveryAction(groupListSource),
    )
}
