package com.pheeeew.di

import android.content.Context
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createPlatformHttpClient
import com.pheeeew.data.local.device.AndroidDeviceAttestationProvider
import com.pheeeew.data.local.device.AndroidEncryptedDeviceTokenStorage
import com.pheeeew.data.local.device.AndroidPlayIntegrityAttestationProvider
import com.pheeeew.data.remote.device.api.KtorDeviceRegistrationApi
import com.pheeeew.data.repository.DeviceRegistrationRepositoryImpl
import com.pheeeew.domain.usecase.EnsureDeviceRegisteredUseCase

fun createAndroidEnsureDeviceRegisteredUseCase(
    context: Context,
    config: ApiConfig,
): EnsureDeviceRegisteredUseCase {
    val repository = DeviceRegistrationRepositoryImpl(
        api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
        tokenStorage = AndroidEncryptedDeviceTokenStorage(context.applicationContext),
        attestationProvider = AndroidDeviceAttestationProvider(),
    )
    return EnsureDeviceRegisteredUseCase(repository)
}

fun createAndroidEnsureDeviceRegisteredWithPlayIntegrityUseCase(
    context: Context,
    config: ApiConfig,
    cloudProjectNumber: Long,
): EnsureDeviceRegisteredUseCase {
    val repository = DeviceRegistrationRepositoryImpl(
        api = KtorDeviceRegistrationApi(createPlatformHttpClient(config)),
        tokenStorage = AndroidEncryptedDeviceTokenStorage(context.applicationContext),
        attestationProvider = AndroidPlayIntegrityAttestationProvider(context, cloudProjectNumber),
    )
    return EnsureDeviceRegisteredUseCase(repository)
}
