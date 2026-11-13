package com.pheeeew.core.di

import com.pheeeew.feature.screens.collection.CollectionViewModel
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailViewModel
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.press.PressViewModel
import com.pheeeew.feature.screens.ranking.press.DailyPressRankingViewModel
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import com.pheeeew.feature.screens.settings.AppEntryViewModel
import com.pheeeew.feature.screens.settings.AppStartupViewModel
import com.pheeeew.feature.screens.settings.SettingsViewModel
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

internal interface ViewModelBindings {
    @Provides fun provideViewModelFactory(
        startup: () -> AppStartupViewModel,
        entry: () -> AppEntryViewModel,
        nearby: () -> NearbyEmotionViewModel,
        record: () -> MapRecordViewModel,
        detail: () -> EmotionDetailViewModel,
        settings: () -> SettingsViewModel,
        press: () -> PressViewModel,
        pressRanking: () -> PressRankingViewModel,
        collection: () -> CollectionViewModel,
        dailyPressRanking: () -> DailyPressRankingViewModel.Factory,
        mapFactory: () -> MapViewModel.Factory,
    ): MetroViewModelFactory =
        AppViewModelFactory(
            mapOf(
                AppStartupViewModel::class to startup,
                AppEntryViewModel::class to entry,
                NearbyEmotionViewModel::class to nearby,
                MapRecordViewModel::class to record,
                EmotionDetailViewModel::class to detail,
                SettingsViewModel::class to settings,
                PressViewModel::class to press,
                PressRankingViewModel::class to pressRanking,
                CollectionViewModel::class to collection,
            ),
            mapOf(
                MapViewModel.Factory::class to mapFactory,
                DailyPressRankingViewModel.Factory::class to dailyPressRanking,
            ),
        )
}
