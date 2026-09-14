package com.pheeeew.data.repository

import com.pheeeew.data.local.device.DeviceAttestationProvider
import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.local.device.DeviceTokenStorage
import com.pheeeew.data.remote.device.api.DeviceRegistrationApi
import com.pheeeew.data.remote.device.dto.DeviceAttestationDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.dto.RefreshTokenRequestDto
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.exception.device.DeviceRegistrationException
import com.pheeeew.domain.model.device.AccessToken
import com.pheeeew.domain.model.device.AccessTokenInfo
import com.pheeeew.domain.model.device.AuthSession
import com.pheeeew.domain.model.device.RefreshToken
import com.pheeeew.domain.repository.DeviceRegistrationRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DeviceRegistrationRepositoryImpl(
    private val api: DeviceRegistrationApi,
    private val tokenStorage: DeviceTokenStorage,
    private val attestationProvider: DeviceAttestationProvider,
    private val accessTokenStore: AccessTokenStore,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
) : DeviceRegistrationRepository {
    override suspend fun getStoredRefreshToken(): RefreshToken? = tokenStorage.getRefreshToken()

    override suspend fun register(): AuthSession = try {
        val challenge = api.getChallenge()
        val attestation = attestationProvider.create(challenge.challenge)
        val response = api.register(
            DeviceRegistrationRequestDto(
                requestId = Uuid.random().toString(),
                attestation = DeviceAttestationDto(
                    platform = attestation.platform.name,
                    token = attestation.token,
                    challenge = attestation.challenge,
                    keyId = attestation.keyId,
                ),
            ),
        )
        val refreshToken = RefreshToken(response.refreshToken)
        tokenStorage.saveRefreshToken(refreshToken)
        accessTokenStore.save(AccessToken(response.accessToken))
        AuthSession(
            accessToken = accessTokenStore.accessToken!!,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + response.expiresIn,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw error.toDeviceRegistrationException()
    }

    override suspend fun refreshAccessToken(refreshToken: RefreshToken): AccessTokenInfo = try {
        val response = api.refresh(RefreshTokenRequestDto(refreshToken.value))
        AccessTokenInfo(
            accessToken = AccessToken(response.accessToken).also(accessTokenStore::save),
            accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + response.expiresIn,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw error.toDeviceRegistrationException()
    }

    override suspend fun clearCredentials() {
        tokenStorage.clear()
        accessTokenStore.clear()
    }

    private fun Throwable.toDeviceRegistrationException(): DeviceRegistrationException = when (this) {
        is DeviceRegistrationException -> this
        is ApiException.Unauthorized -> when (code) {
            "DEVICE-003" -> DeviceRegistrationException.InvalidRefreshToken(this)
            "DEVICE-004" -> DeviceRegistrationException.DeviceNotFound(this)
            else -> DeviceRegistrationException.Server(this)
        }
        is ApiException.InvalidRequest -> when (code) {
            "DEVICE-005" -> DeviceRegistrationException.InvalidChallenge(this)
            else -> DeviceRegistrationException.Server(this)
        }
        is ApiException.Forbidden -> when (code) {
            "DEVICE-006" -> DeviceRegistrationException.AttestationRejected(this)
            else -> DeviceRegistrationException.Server(this)
        }
        is ApiException.Unknown -> when (code) {
            "DEVICE-007" -> DeviceRegistrationException.RetryableServer(
                retryAfterSeconds = retryAfterSeconds,
                cause = this,
            )
            else -> DeviceRegistrationException.Server(this)
        }
        is ApiException.Network -> DeviceRegistrationException.Network(this)
        else -> DeviceRegistrationException.Server(this)
    }
}
