package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.api.GroupListApi
import com.pheeeew.data.remote.group.api.GroupStampListApi
import com.pheeeew.data.repository.group.GroupListRepositoryImpl
import com.pheeeew.data.repository.group.GroupStampListRepositoryImpl
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.feature.screens.group.adapter.ApiGroupListSource
import com.pheeeew.feature.screens.group.home.GroupListSource

/** Builds the home list adapter from the app-owned authenticated client. */
fun createGroupListSource(apiClient: ApiClient): GroupListSource =
    ApiGroupListSource(GroupListRepositoryImpl(GroupListApi(apiClient.requests)))

fun createGroupStampListRepository(apiClient: ApiClient): GroupStampListRepository =
    GroupStampListRepositoryImpl(GroupStampListApi(apiClient.requests))
