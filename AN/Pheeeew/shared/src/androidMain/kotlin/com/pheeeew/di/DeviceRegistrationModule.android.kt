package com.pheeeew.di

import android.content.Context
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createPlatformHttpClient
import com.pheeeew.data.local.device.AndroidDeviceAttestationProvider
import com.pheeeew.data.local.device.AndroidEncryptedDeviceTokenStorage
import com.pheeeew.data.local.device.AndroidPlayIntegrityAttestationProvider
import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.remote.device.api.KtorDeviceRegistrationApi
import com.pheeeew.data.repository.DeviceRegistrationRepositoryImpl
import com.pheeeew.domain.usecase.EnsureDeviceRegisteredUseCase
import com.pheeeew.domain.repository.DeviceRegistrationRepository

data class AndroidDeviceRegistrationDependencies(
    val repository: DeviceRegistrationRepository,
    val ensureRegistered: EnsureDeviceRegisteredUseCase,
)

fun createAndroidDeviceRegistrationDependencies(
    context: Context,
    config: ApiConfig,
    accessTokenStore: AccessTokenStore,
): AndroidDeviceRegistrationDependencies {
    val repository = DeviceRegistrationRepositoryImpl(
        api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
        tokenStorage = AndroidEncryptedDeviceTokenStorage(context.applicationContext),
        attestationProvider = AndroidDeviceAttestationProvider(),
        accessTokenStore = accessTokenStore,
    )
    return AndroidDeviceRegistrationDependencies(repository, EnsureDeviceRegisteredUseCase(repository))
}

fun createAndroidDeviceRegistrationWithPlayIntegrityDependencies(
    context: Context,
    config: ApiConfig,
    cloudProjectNumber: Long,
    accessTokenStore: AccessTokenStore,
): AndroidDeviceRegistrationDependencies {
    val repository = DeviceRegistrationRepositoryImpl(
        api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
        tokenStorage = AndroidEncryptedDeviceTokenStorage(context.applicationContext),
        attestationProvider = AndroidPlayIntegrityAttestationProvider(context, cloudProjectNumber),
        accessTokenStore = accessTokenStore,
    )
    return AndroidDeviceRegistrationDependencies(repository, EnsureDeviceRegisteredUseCase(repository))
}
