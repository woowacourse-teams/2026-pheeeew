package com.pheeeew.domain.usecase

import com.pheeeew.domain.exception.device.DeviceRegistrationException
import com.pheeeew.domain.model.device.AuthSession
import com.pheeeew.domain.repository.DeviceRegistrationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class EnsureDeviceRegisteredUseCase(
    private val repository: DeviceRegistrationRepository,
    private val forceRegistrationOnce: Boolean = false,
) {
    private val registrationMutex = Mutex()
    private var didForceRegistration = false

    suspend operator fun invoke(): Result<AuthSession> = registrationMutex.withLock { ensureRegistered() }

    private suspend fun ensureRegistered(): Result<AuthSession> {
        if (forceRegistrationOnce && !didForceRegistration) {
            didForceRegistration = true
            repository.clearCredentials()
            return register()
        }

        val refreshToken = repository.getStoredRefreshToken()

        if (refreshToken == null) {
            return register()
        }

        return try {
            val accessTokenInfo =
                repository.refreshAccessToken(refreshToken)
            val resolvedRefreshToken = accessTokenInfo.refreshToken ?: refreshToken

            Result.success(
                AuthSession(
                    accessToken = accessTokenInfo.accessToken,
                    refreshToken = resolvedRefreshToken,
                    accessTokenExpiresAtEpochSeconds =
                        accessTokenInfo.accessTokenExpiresAtEpochSeconds,
                ),
            )
        } catch (error: DeviceRegistrationException.InvalidRefreshToken) {
            repository.clearCredentials()
            register()
        } catch (error: DeviceRegistrationException.DeviceNotFound) {
            repository.clearCredentials()
            register()
        } catch (error: DeviceRegistrationException) {
            Result.failure(error)
        }
    }

    private suspend fun register(): Result<AuthSession> =
        try {
            Result.success(repository.register())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
}
