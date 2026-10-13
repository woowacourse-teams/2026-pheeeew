package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.api.GroupExploreApi
import com.pheeeew.data.repository.group.GroupExploreRepositoryImpl
import com.pheeeew.feature.screens.group.adapter.ApiGroupExploreSource
import com.pheeeew.feature.screens.group.home.explore.GroupExploreSource

fun createGroupExploreSource(apiClient: ApiClient): GroupExploreSource =
    ApiGroupExploreSource(GroupExploreRepositoryImpl(GroupExploreApi(apiClient.requests)))
