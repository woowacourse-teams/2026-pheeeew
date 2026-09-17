@file:Suppress("ktlint:standard:filename")

package com.pheeeew.di

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createPlatformHttpClient
import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.local.device.DeviceAttestationProvider
import com.pheeeew.data.local.device.IosAppAttestProvider
import com.pheeeew.data.local.device.IosKeychainDeviceTokenStorage
import com.pheeeew.data.local.device.NoOpDeviceAttestationProvider
import com.pheeeew.data.remote.device.api.KtorDeviceRegistrationApi
import com.pheeeew.data.repository.DeviceRegistrationRepositoryImpl
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.repository.DeviceRegistrationRepository
import com.pheeeew.domain.usecase.EnsureDeviceRegisteredUseCase
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
        if (!isDebugBuild && DCAppAttestService.sharedService.isSupported()) {
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
