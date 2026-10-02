package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.GroupRankingApi
import com.pheeeew.data.repository.GroupRankingRepositoryImpl
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingViewModel
import com.pheeeew.feature.screens.ranking.stamp.data.ApiWeeklyRankingSource

fun createWeeklyRankingViewModel(apiClient: ApiClient): WeeklyRankingViewModel =
    WeeklyRankingViewModel(
        monitoring = apiClient.monitoring,
        source =
            ApiWeeklyRankingSource(
                GroupRankingRepositoryImpl(GroupRankingApi(apiClient.requests)),
            ),
    )
