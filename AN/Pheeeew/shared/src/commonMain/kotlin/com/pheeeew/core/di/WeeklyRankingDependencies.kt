package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.GroupRankingApi
import com.pheeeew.data.repository.GroupRankingRepositoryImpl
import com.pheeeew.feature.screens.ranking.WeeklyRankingViewModel
import com.pheeeew.feature.screens.ranking.data.ApiWeeklyRankingSource

fun createWeeklyRankingViewModel(apiClient: ApiClient): WeeklyRankingViewModel =
    WeeklyRankingViewModel(
        ApiWeeklyRankingSource(
            GroupRankingRepositoryImpl(GroupRankingApi(apiClient.requests)),
        ),
    )
