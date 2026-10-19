package com.pheeeew.core.di

import com.pheeeew.core.audio.AudioFactories
import com.pheeeew.core.di.group.GroupDependencies
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.domain.repository.EmotionAudioRepository
import com.pheeeew.domain.repository.GroupStampListRepository
import com.pheeeew.domain.repository.OnboardingStore
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

interface AppGraph {
    val apiDependencies: ApiDependencies
    val monitoringOwner: AppMonitoring
    val onboardingStore: OnboardingStore
    val appSettingsLauncher: AppSettingsLauncher
    val audioFactories: AudioFactories
    val versionRepository: AppVersionRepository

    @AppVersion val appVersion: String
}

interface UiGraph {
    val viewModelFactory: MetroViewModelFactory
    val locationDependencies: LocationDependencies
    val groupDependencies: GroupDependencies
    val groupStampListRepository: GroupStampListRepository
    val moderation: EmotionModerationDependencies
    val audioRepository: EmotionAudioRepository
}
