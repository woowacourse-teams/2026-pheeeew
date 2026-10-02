package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.group.PressRankingApi
import com.pheeeew.data.repository.PressRankingRepositoryImpl
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import com.pheeeew.feature.screens.ranking.press.data.ApiPressRankingSource

internal fun createPressRankingViewModel(apiClient: ApiClient): PressRankingViewModel =
    PressRankingViewModel(
        monitoring = apiClient.monitoring,
        source = ApiPressRankingSource(PressRankingRepositoryImpl(PressRankingApi(apiClient.requests))),
    )
