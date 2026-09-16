@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.repository

import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.local.device.DeviceAttestation
import com.pheeeew.data.local.device.DeviceAttestationProvider
import com.pheeeew.data.local.device.DeviceTokenStorage
import com.pheeeew.data.remote.device.api.DeviceRegistrationApi
import com.pheeeew.data.remote.device.dto.DeviceChallengeResponseDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationResponseDto
import com.pheeeew.data.remote.device.dto.RefreshTokenRequestDto
import com.pheeeew.data.remote.device.dto.RefreshTokenResponseDto
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.device.AccessToken
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.RefreshToken
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceRegistrationRepositoryTest {
    @Test
    fun `등록 네트워크 실패 시 같은 requestId로 한 번 재시도한다`() =
        runTest {
            val api = FakeDeviceRegistrationApi()
            val repository =
                DeviceRegistrationRepositoryImpl(
                    api = api,
                    tokenStorage = FakeDeviceTokenStorage(),
                    attestationProvider = FakeDeviceAttestationProvider(),
                    accessTokenStore = FakeAccessTokenStore(),
                )

            repository.register()

            assertEquals(2, api.registrationRequests.size)
            assertEquals(
                api.registrationRequests[0].requestId,
                api.registrationRequests[1].requestId,
            )
            assertEquals(null, api.registrationRequests[1].attestation)
        }

    private class FakeDeviceRegistrationApi : DeviceRegistrationApi {
        val registrationRequests = mutableListOf<DeviceRegistrationRequestDto>()

        override suspend fun getChallenge() = DeviceChallengeResponseDto("challenge", 60)

        override suspend fun register(request: DeviceRegistrationRequestDto): DeviceRegistrationResponseDto {
            registrationRequests += request
            if (registrationRequests.size == 1) {
                throw ApiException.Network("NETWORK", "connection lost")
            }
            return DeviceRegistrationResponseDto("access", "refresh", 3600)
        }

        override suspend fun refresh(request: RefreshTokenRequestDto) = RefreshTokenResponseDto("access", 3600)
    }

    private class FakeDeviceAttestationProvider : DeviceAttestationProvider {
        override suspend fun create(challenge: String) =
            DeviceAttestation(
                platform = DevicePlatform.IOS,
                token = "token",
                challenge = challenge,
                keyId = "key-id",
            )
    }

    private class FakeDeviceTokenStorage : DeviceTokenStorage {
        private var refreshToken: RefreshToken? = null

        override suspend fun getRefreshToken() = refreshToken

        override suspend fun saveRefreshToken(refreshToken: RefreshToken) {
            this.refreshToken = refreshToken
        }

        override suspend fun clear() {
            refreshToken = null
        }
    }

    private class FakeAccessTokenStore : AccessTokenStore {
        override var accessToken: AccessToken? = null

        override fun save(accessToken: AccessToken) {
            this.accessToken = accessToken
        }

        override fun clear() {
            accessToken = null
        }
    }
}
