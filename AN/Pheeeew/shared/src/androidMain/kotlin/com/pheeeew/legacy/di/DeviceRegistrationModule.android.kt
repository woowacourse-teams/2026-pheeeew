@file:Suppress("ktlint:standard:filename")

package com.pheeeew.legacy.di

import android.content.Context
import com.pheeeew.legacy.core.network.ApiConfig
import com.pheeeew.legacy.core.network.createPlatformHttpClient
import com.pheeeew.legacy.data.local.device.AccessTokenStore
import com.pheeeew.legacy.data.local.device.AndroidDeviceAttestationProvider
import com.pheeeew.legacy.data.local.device.AndroidEncryptedDeviceTokenStorage
import com.pheeeew.legacy.data.local.device.AndroidPlayIntegrityAttestationProvider
import com.pheeeew.legacy.data.remote.device.api.KtorDeviceRegistrationApi
import com.pheeeew.legacy.data.repository.DeviceRegistrationRepositoryImpl
import com.pheeeew.legacy.domain.repository.DeviceRegistrationRepository
import com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase

data class AndroidDeviceRegistrationDependencies(
    val repository: com.pheeeew.legacy.domain.repository.DeviceRegistrationRepository,
    val ensureRegistered: com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase,
)

fun createAndroidDeviceRegistrationDependencies(
    context: Context,
    config: ApiConfig,
    accessTokenStore: AccessTokenStore,
): AndroidDeviceRegistrationDependencies {
    val repository =
        DeviceRegistrationRepositoryImpl(
            api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
            tokenStorage = AndroidEncryptedDeviceTokenStorage(context.applicationContext),
            attestationProvider = AndroidDeviceAttestationProvider(),
            accessTokenStore = accessTokenStore,
        )
    return AndroidDeviceRegistrationDependencies(
        repository,
        com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase(
            repository,
        ),
    )
}

fun createAndroidDeviceRegistrationWithPlayIntegrityDependencies(
    context: Context,
    config: ApiConfig,
    cloudProjectNumber: Long,
    accessTokenStore: AccessTokenStore,
): AndroidDeviceRegistrationDependencies {
    val repository =
        DeviceRegistrationRepositoryImpl(
            api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
            tokenStorage = AndroidEncryptedDeviceTokenStorage(context.applicationContext),
            attestationProvider = AndroidPlayIntegrityAttestationProvider(context, cloudProjectNumber),
            accessTokenStore = accessTokenStore,
        )
    return AndroidDeviceRegistrationDependencies(
        repository,
        com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase(
            repository,
        ),
    )
}
