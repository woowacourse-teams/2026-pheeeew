package com.pheeeew.core.di

import com.pheeeew.data.repository.GroupRankingRepositoryImpl
import com.pheeeew.data.repository.PressRankingRepositoryImpl
import com.pheeeew.domain.repository.GroupRankingRepository
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.PressRankingSource
import com.pheeeew.feature.screens.ranking.press.data.ApiPressRankingSource
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingSource
import com.pheeeew.feature.screens.ranking.stamp.data.ApiWeeklyRankingSource
import dev.zacsweers.metro.Provides

internal interface RankingBindings {
    @Provides fun provideWeeklyRepository(impl: GroupRankingRepositoryImpl): GroupRankingRepository = impl

    @Provides fun providePressRankingRepository(impl: PressRankingRepositoryImpl): PressRankingRepository = impl

    @Provides fun provideWeeklySource(impl: ApiWeeklyRankingSource): WeeklyRankingSource = impl

    @Provides fun providePressRankingSource(impl: ApiPressRankingSource): PressRankingSource = impl
}
