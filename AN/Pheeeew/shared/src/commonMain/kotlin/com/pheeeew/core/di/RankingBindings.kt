package com.pheeeew.core.di

import com.pheeeew.data.repository.PressRankingRepositoryImpl
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.PressRankingSource
import com.pheeeew.feature.screens.ranking.press.data.ApiPressRankingSource
import dev.zacsweers.metro.Provides

internal interface RankingBindings {
    @Provides fun providePressRankingRepository(impl: PressRankingRepositoryImpl): PressRankingRepository = impl

    @Provides fun providePressRankingSource(impl: ApiPressRankingSource): PressRankingSource = impl
}
