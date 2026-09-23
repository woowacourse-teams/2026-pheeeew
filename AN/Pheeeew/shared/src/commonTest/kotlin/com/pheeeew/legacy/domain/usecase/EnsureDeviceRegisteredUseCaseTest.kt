@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.domain.usecase

import com.pheeeew.legacy.domain.exception.device.DeviceRegistrationException
import com.pheeeew.legacy.domain.model.device.AccessToken
import com.pheeeew.legacy.domain.model.device.AccessTokenInfo
import com.pheeeew.legacy.domain.model.device.AuthSession
import com.pheeeew.legacy.domain.model.device.RefreshToken
import com.pheeeew.legacy.domain.repository.DeviceRegistrationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class EnsureDeviceRegisteredUseCaseTest {
    @Test
    fun `저장된 refresh token이 없으면 기기 등록을 수행한다`() =
        runTest {
            val repository = FakeDeviceRegistrationRepository(refreshToken = null)
            val useCase = EnsureDeviceRegisteredUseCase(repository)

            val result = useCase()

            assertEquals(repository.registration, result.getOrThrow())
            assertEquals(1, repository.registerCallCount)
            assertEquals(0, repository.refreshCallCount)
        }

    @Test
    fun `저장된 refresh token이 있으면 access token을 갱신한다`() =
        runTest {
            val refreshToken = RefreshToken("stored-refresh")
            val repository = FakeDeviceRegistrationRepository(refreshToken = refreshToken)
            val useCase = EnsureDeviceRegisteredUseCase(repository)

            val session = useCase().getOrThrow()

            assertEquals(AccessToken("refreshed-access"), session.accessToken)
            assertEquals(refreshToken, session.refreshToken)
            assertEquals(1234L, session.accessTokenExpiresAtEpochSeconds)
            assertEquals(1, repository.refreshCallCount)
            assertEquals(0, repository.registerCallCount)
        }

    @Test
    fun `refresh token이 갱신되면 새 token을 session에 반영한다`() =
        runTest {
            val oldToken = RefreshToken("old-refresh")
            val newToken = RefreshToken("new-refresh")
            val repository = FakeDeviceRegistrationRepository(refreshToken = oldToken)
            repository.refreshResult =
                AccessTokenInfo(
                    accessToken = AccessToken("access"),
                    accessTokenExpiresAtEpochSeconds = 1234L,
                    refreshToken = newToken,
                )

            val session = EnsureDeviceRegisteredUseCase(repository)().getOrThrow()

            assertEquals(newToken, session.refreshToken)
        }

    @Test
    fun `invalid refresh token이면 credential을 지우고 재등록한다`() =
        runTest {
            val repository =
                FakeDeviceRegistrationRepository(
                    refreshToken = RefreshToken("invalid"),
                    refreshError = DeviceRegistrationException.InvalidRefreshToken(),
                )

            val result = EnsureDeviceRegisteredUseCase(repository)()

            assertEquals(repository.registration, result.getOrThrow())
            assertEquals(1, repository.clearCredentialsCallCount)
            assertEquals(1, repository.registerCallCount)
        }

    @Test
    fun `일반 등록 오류는 failure result로 반환한다`() =
        runTest {
            val error = DeviceRegistrationException.Network()
            val repository =
                FakeDeviceRegistrationRepository(
                    refreshToken = null,
                    registerError = error,
                )

            val failure = EnsureDeviceRegisteredUseCase(repository)().exceptionOrNull()

            assertIs<DeviceRegistrationException.Network>(failure)
        }

    @Test
    fun `등록 중 coroutine cancellation은 그대로 전파한다`() =
        runTest {
            val repository =
                FakeDeviceRegistrationRepository(
                    refreshToken = null,
                    registerError = CancellationException("cancelled"),
                )

            assertFailsWith<CancellationException> {
                EnsureDeviceRegisteredUseCase(repository)()
            }
        }

    @Test
    fun `동시 호출은 기기 등록을 중복 실행하지 않는다`() =
        runTest {
            val repository =
                FakeDeviceRegistrationRepository(
                    refreshToken = null,
                    registerDelayMillis = 10L,
                )
            val useCase = EnsureDeviceRegisteredUseCase(repository)

            listOf(1, 2)
                .map { async { useCase() } }
                .awaitAll()

            assertEquals(1, repository.registerCallCount)
            assertEquals(1, repository.refreshCallCount)
        }

    private class FakeDeviceRegistrationRepository(
        refreshToken: RefreshToken?,
        private val registerError: Throwable? = null,
        private val refreshError: Throwable? = null,
        private val registerDelayMillis: Long = 0L,
    ) : DeviceRegistrationRepository {
        private var refreshToken = refreshToken
        val registration =
            AuthSession(
                accessToken = AccessToken("registered-access"),
                refreshToken = RefreshToken("registered-refresh"),
                accessTokenExpiresAtEpochSeconds = 5678L,
            )
        var refreshResult =
            AccessTokenInfo(
                accessToken = AccessToken("refreshed-access"),
                accessTokenExpiresAtEpochSeconds = 1234L,
            )
        var registerCallCount = 0
        var refreshCallCount = 0
        var clearCredentialsCallCount = 0

        override suspend fun getStoredRefreshToken(): RefreshToken? = refreshToken

        override suspend fun register(): AuthSession {
            registerCallCount++
            delay(registerDelayMillis)
            registerError?.let { throw it }
            refreshToken = registration.refreshToken
            return registration
        }

        override suspend fun refreshAccessToken(refreshToken: RefreshToken): AccessTokenInfo {
            refreshCallCount++
            refreshError?.let { throw it }
            return refreshResult
        }

        override suspend fun clearCredentials() {
            clearCredentialsCallCount++
        }
    }
}
