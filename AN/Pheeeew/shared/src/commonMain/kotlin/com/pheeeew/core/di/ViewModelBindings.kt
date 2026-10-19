package com.pheeeew.core.di

import com.pheeeew.feature.screens.group.create.GroupCreateViewModel
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.home.GroupHomeViewModel
import com.pheeeew.feature.screens.group.members.GroupMembersViewModel
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailViewModel
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.press.PressViewModel
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingViewModel
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
        weekly: () -> WeeklyRankingViewModel,
        pressRanking: () -> PressRankingViewModel,
        home: () -> GroupHomeViewModel,
        create: () -> GroupCreateViewModel,
        mapFactory: () -> MapViewModel.Factory,
        groupDetailFactory: () -> GroupDetailViewModel.Factory,
        membersFactory: () -> GroupMembersViewModel.Factory,
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
                WeeklyRankingViewModel::class to weekly,
                PressRankingViewModel::class to pressRanking,
                GroupHomeViewModel::class to home,
                GroupCreateViewModel::class to create,
            ),
            mapOf(
                MapViewModel.Factory::class to mapFactory,
                GroupDetailViewModel.Factory::class to groupDetailFactory,
                GroupMembersViewModel.Factory::class to membersFactory,
            ),
        )
}
