@file:Suppress("ktlint:standard:filename")

package com.pheeeew.legacy.di

import com.pheeeew.legacy.core.network.ApiConfig
import com.pheeeew.legacy.core.network.createPlatformHttpClient
import com.pheeeew.legacy.data.local.device.AccessTokenStore
import com.pheeeew.legacy.data.local.device.DeviceAttestationProvider
import com.pheeeew.legacy.data.local.device.IosAppAttestProvider
import com.pheeeew.legacy.data.local.device.IosKeychainDeviceTokenStorage
import com.pheeeew.legacy.data.local.device.NoOpDeviceAttestationProvider
import com.pheeeew.legacy.data.remote.device.api.KtorDeviceRegistrationApi
import com.pheeeew.legacy.data.repository.DeviceRegistrationRepositoryImpl
import com.pheeeew.legacy.domain.model.device.DevicePlatform
import com.pheeeew.legacy.domain.repository.DeviceRegistrationRepository
import com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase
import platform.DeviceCheck.DCAppAttestService
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

data class IosDeviceRegistrationDependencies(
    val repository: DeviceRegistrationRepository,
    val ensureRegistered: EnsureDeviceRegisteredUseCase,
)

@OptIn(ExperimentalNativeApi::class)
fun createIosDeviceRegistrationDependencies(
    config: ApiConfig,
    accessTokenStore: AccessTokenStore,
): IosDeviceRegistrationDependencies {
    val tokenStorage = IosKeychainDeviceTokenStorage()
    val isDebugBuild = Platform.isDebugBinary
    val attestationProvider: DeviceAttestationProvider =
        if (DCAppAttestService.sharedService.isSupported()) {
            IosAppAttestProvider(tokenStorage)
        } else {
            NoOpDeviceAttestationProvider(DevicePlatform.IOS)
        }
    val repository =
        DeviceRegistrationRepositoryImpl(
            api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
            tokenStorage = tokenStorage,
            attestationProvider = attestationProvider,
            accessTokenStore = accessTokenStore,
        )
    return IosDeviceRegistrationDependencies(
        repository = repository,
        ensureRegistered =
            EnsureDeviceRegisteredUseCase(
                repository = repository,
                forceRegistrationOnce = isDebugBuild,
            ),
    )
}
