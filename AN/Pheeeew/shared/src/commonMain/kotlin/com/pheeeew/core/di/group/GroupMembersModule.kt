package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.api.GroupMembersApi
import com.pheeeew.data.repository.group.GroupMembersRepositoryImpl
import com.pheeeew.feature.screens.group.adapter.ApiGroupMembersSource
import com.pheeeew.feature.screens.group.members.GroupMembersSource

fun createGroupMembersSource(apiClient: ApiClient): GroupMembersSource =
    ApiGroupMembersSource(GroupMembersRepositoryImpl(GroupMembersApi(apiClient.requests)))
