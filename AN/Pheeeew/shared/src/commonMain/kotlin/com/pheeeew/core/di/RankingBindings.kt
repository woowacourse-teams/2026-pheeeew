package com.pheeeew.core.di

import com.pheeeew.data.repository.PressRankingRepositoryImpl
import com.pheeeew.domain.repository.PressRankingRepository
import dev.zacsweers.metro.Provides

internal interface RankingBindings {
    @Provides fun providePressRankingRepository(impl: PressRankingRepositoryImpl): PressRankingRepository = impl
}
