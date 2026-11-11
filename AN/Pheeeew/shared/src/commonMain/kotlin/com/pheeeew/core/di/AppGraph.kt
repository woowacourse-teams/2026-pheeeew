package com.pheeeew.core.di

import com.pheeeew.core.audio.AudioFactories
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.data.repository.PressEventConfigRepository
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.domain.repository.EmotionAudioRepository
import com.pheeeew.domain.repository.OnboardingStore
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

interface AppGraph {
    val apiDependencies: ApiDependencies
    val monitoringOwner: AppMonitoring
    val onboardingStore: OnboardingStore
    val appSettingsLauncher: AppSettingsLauncher
    val audioFactories: AudioFactories
    val versionRepository: AppVersionRepository
    val pressEventConfig: PressEventConfigRepository

    @AppVersion val appVersion: String
}

interface UiGraph {
    val viewModelFactory: MetroViewModelFactory
    val locationDependencies: LocationDependencies
    val moderation: EmotionModerationDependencies
    val audioRepository: EmotionAudioRepository
}
