package com.pheeeew.core.di

import com.pheeeew.data.repository.CollectionRepositoryImpl
import com.pheeeew.domain.repository.CollectionRepository
import dev.zacsweers.metro.Provides

internal interface CollectionBindings {
    @Provides fun provideCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository = impl
}
