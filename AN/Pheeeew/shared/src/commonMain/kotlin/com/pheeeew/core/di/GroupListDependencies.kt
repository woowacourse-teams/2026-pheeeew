package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.GroupListApi
import com.pheeeew.data.repository.GroupListRepositoryImpl
import com.pheeeew.feature.screens.group.data.ApiGroupListSource
import com.pheeeew.feature.screens.group.home.GroupListSource

/** Builds the home list adapter from the app-owned authenticated client. */
fun createGroupListSource(apiClient: ApiClient): GroupListSource =
    ApiGroupListSource(GroupListRepositoryImpl(GroupListApi(apiClient.requests)))
