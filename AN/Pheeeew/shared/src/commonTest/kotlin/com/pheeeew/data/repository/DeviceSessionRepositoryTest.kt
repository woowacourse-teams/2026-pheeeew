package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiError
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.TransportFailureReason
import com.pheeeew.data.local.device.CredentialRead
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.remote.device.DeviceAttestationDto
import com.pheeeew.data.remote.device.DeviceAttestationPolicy
import com.pheeeew.data.remote.device.DeviceChallengeDto
import com.pheeeew.data.remote.device.DeviceProofException
import com.pheeeew.data.remote.device.DeviceRefreshRequestDto
import com.pheeeew.data.remote.device.DeviceRefreshResponseDto
import com.pheeeew.data.remote.device.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.DeviceRegistrationResponseDto
import com.pheeeew.data.remote.device.DeviceSessionApi
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceCredentials
import com.pheeeew.domain.model.device.DeviceDiagnosticOutcome
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.DeviceSessionDiagnostic
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.model.device.DeviceSessionStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceSessionRepositoryTest {
    @Test
    fun `required proof is bound to a fresh challenge on retry with the same registration id`() =
        runTest {
            val api = Api()
            val challenges = mutableListOf<String>()
            api.registerBlock = { request ->
                assertEquals("proof-${api.challenges}", request.attestation.token)
                assertEquals("challenge-${api.challenges}", request.attestation.challenge)
                if (api.ids.size == 1) http(400, "DEVICE-005") else success()
            }
            val result =
                repository(
                    api,
                    Storage(),
                    DeviceAttestationPolicy.Required { platform, challenge ->
                        challenges += challenge
                        DeviceAttestationDto(platform.name, "proof-${api.challenges}", challenge)
                    },
                ).prepare()
            assertIs<DeviceSessionResult.Ready>(result)
            assertEquals(listOf("challenge-1", "challenge-2"), challenges)
            assertEquals(listOf("new", "new"), api.ids)
        }

    @Test
    fun `rejected proof does not register again or downgrade`() =
        runTest {
            val api = Api().apply { registerBlock = { http(403, "DEVICE-006") } }
            val result =
                repository(
                    api,
                    Storage(),
                    DeviceAttestationPolicy.Required { platform, challenge ->
                        DeviceAttestationDto(platform.name, "proof", challenge)
                    },
                ).prepare()
            assertEquals(DeviceSessionFailureKind.ATTESTATION, assertIs<DeviceSessionResult.Failed>(result).reason.kind)
            assertEquals(1, api.ids.size)
            assertEquals(1, api.challenges)
        }

    @Test
    fun `refresh does not invoke proof provider`() =
        runTest {
            val api = Api()
            val result =
                repository(
                    api,
                    Storage(DeviceCredentials(refreshToken = "existing", generation = 1)),
                    DeviceAttestationPolicy.Required { _, _ -> error("Must not generate a new key for refresh") },
                ).prepare()
            assertIs<DeviceSessionResult.Ready>(result)
            assertEquals(0, api.challenges)
            assertTrue(api.ids.isEmpty())
        }

    @Test
    fun `registration persists refresh before exposing access and removes pending`() =
        runTest {
            val storage = Storage()
            val api =
                Api().apply {
                    registerBlock = {
                        assertEquals(it.requestId, storage.value?.pendingRequestId)
                        assertEquals("ANDROID", it.attestation.platform)
                        assertNull(it.attestation.token)
                        success()
                    }
                }
            val result = assertIs<DeviceSessionResult.Ready>(repository(api, storage).prepare())
            assertEquals("refresh", storage.value?.refreshToken)
            assertNull(storage.value?.pendingRequestId)
            assertEquals(1, result.access.generation)
            assertEquals(0, api.challenges)
        }

    @Test
    fun `uncertain registration reuses persisted id across attempts and process restart`() =
        runTest {
            val storage = Storage()
            val api = Api().apply { registerBlock = { networkFailure() } }
            assertIs<DeviceSessionResult.Failed>(repository(api, storage).prepare())
            val id = storage.value?.pendingRequestId
            api.registerBlock = { success() }
            repository(api, storage).prepare()
            assertEquals(listOf(id, id, id), api.ids)
        }

    @Test
    fun `expired window allows one new registration id`() =
        runTest {
            val storage = Storage(DeviceCredentials(pendingRequestId = "old", pendingStartedAtMillis = 0))
            val api =
                Api().apply {
                    registerBlock =
                        { if (it.requestId == "old") http(409, "DEVICE-002") else success() }
                }
            assertIs<DeviceSessionResult.Ready>(repository(api, storage).prepare())
            assertEquals(listOf("old", "new"), api.ids)
        }

    @Test
    fun `repeated expired windows terminate after two transmissions`() =
        runTest {
            val api = Api().apply { registerBlock = { http(409, "DEVICE-002") } }
            assertIs<DeviceSessionResult.Failed>(repository(api, Storage()).prepare())
            assertEquals(2, api.ids.size)
        }

    @Test
    fun `refresh preserves credential and generation`() =
        runTest {
            val storage = Storage(DeviceCredentials(refreshToken = "old-refresh", generation = 7))
            val api = Api()
            val result = assertIs<DeviceSessionResult.Ready>(repository(api, storage).prepare())
            assertEquals("old-refresh", api.refreshUsed)
            assertEquals("old-refresh", storage.value?.refreshToken)
            assertEquals(7, result.access.generation)
            assertTrue(api.ids.isEmpty())
        }

    @Test
    fun `invalid refresh replaces identity while server failure retains it`() =
        runTest {
            val storage = Storage(DeviceCredentials(refreshToken = "old", generation = 7))
            val api = Api().apply { refreshResult = http(500, "SERVER-001") }
            val repository = repository(api, storage)
            assertIs<DeviceSessionResult.Failed>(repository.prepare())
            assertEquals("old", storage.value?.refreshToken)
            assertTrue(api.ids.isEmpty())
            api.refreshResult = http(401, "DEVICE-003")
            val result = assertIs<DeviceSessionResult.Ready>(repository.prepare())
            assertEquals(8, result.access.generation)
        }

    @Test
    fun `storage read error does not register`() =
        runTest {
            val storage = Storage().apply { readFailure = true }
            val api = Api()
            val result = assertIs<DeviceSessionResult.Failed>(repository(api, storage).prepare())
            assertEquals(DeviceSessionFailureKind.STORAGE, result.reason.kind)
            assertTrue(api.ids.isEmpty())
        }

    @Test
    fun `pending write failure prevents transmission`() =
        runTest {
            val storage = Storage().apply { rejectWrite = true }
            val api = Api()
            assertIs<DeviceSessionResult.Failed>(repository(api, storage).prepare())
            assertTrue(api.ids.isEmpty())
        }

    @Test
    fun `refresh persistence failure never publishes access`() =
        runTest {
            val storage = Storage()
            val api =
                Api().apply {
                    registerBlock = {
                        storage.rejectWrite = true
                        success()
                    }
                }
            val result = assertIs<DeviceSessionResult.Failed>(repository(api, storage).prepare())
            assertEquals(DeviceSessionFailureKind.STORAGE, result.reason.kind)
            assertNotNull(storage.value?.pendingRequestId)
            assertNull(storage.value?.refreshToken)
        }

    @Test
    fun `invalid tokens ttl and overflow fail without saving success`() =
        runTest {
            for (dto in listOf(
                DeviceRegistrationResponseDto("", "r", 10),
                DeviceRegistrationResponseDto("a", "", 10),
                DeviceRegistrationResponseDto("a", "r", 0),
                DeviceRegistrationResponseDto("a", "r", Long.MAX_VALUE),
            )) {
                val storage = Storage()
                val api = Api().apply { registerBlock = { ApiResult.Success(dto) } }
                assertIs<DeviceSessionResult.Failed>(repository(api, storage).prepare())
                assertNull(storage.value?.refreshToken)
            }
        }

    @Test
    fun `retry after suppresses further registration calls`() =
        runTest {
            var now = 1000L
            val api = Api().apply { registerBlock = { http(503, "DEVICE-007", "30") } }
            val repository = repository(api, Storage(), now = { now })
            repeat(3) { assertIs<DeviceSessionResult.Failed>(repository.prepare()) }
            assertEquals(1, api.ids.size)
            now += 30_000
            repository.prepare()
            assertEquals(2, api.ids.size)
        }

    @Test
    fun `proof failure never downgrades to platform only`() =
        runTest {
            val api = Api()
            val repository =
                repository(
                    api,
                    Storage(),
                    DeviceAttestationPolicy.Required {
                        _,
                        _,
                        ->
                        error("private proof data")
                    },
                )
            assertEquals(
                DeviceSessionFailureKind.ATTESTATION,
                assertIs<DeviceSessionResult.Failed>(repository.prepare()).reason.kind,
            )
            assertEquals(1, api.challenges)
            assertTrue(api.ids.isEmpty())
        }

    @Test
    fun `cancellation preserves pending id and propagates`() =
        runTest {
            val storage = Storage()
            val api = Api().apply { registerBlock = { throw CancellationException() } }
            assertFailsWith<CancellationException> { repository(api, storage).prepare() }
            assertNotNull(storage.value?.pendingRequestId)
        }

    @Test
    fun `credential and wire types redact secrets`() {
        val secret = "never-log-this"
        listOf(
            DeviceCredentials(refreshToken = secret),
            DeviceAttestationDto("IOS", secret, secret, secret),
            DeviceRegistrationRequestDto(secret, DeviceAttestationDto("IOS")),
            DeviceRegistrationResponseDto(secret, secret, 10),
            DeviceRefreshRequestDto(secret),
            DeviceRefreshResponseDto(secret, 10),
            DeviceChallengeDto(secret, 10),
            DeviceAccess(secret, 0, 100, 1),
        ).forEach { assertFalse(it.toString().contains(secret)) }
    }

    @Test
    fun `proof SDK failure preserves safe code and differs from registration rejection`() =
        runTest {
            val events = mutableListOf<DeviceSessionDiagnostic>()
            val api = Api()
            val proofFailure =
                DeviceSessionRepositoryImpl(
                    api,
                    Storage(),
                    DevicePlatform.ANDROID,
                    DeviceAttestationPolicy.Required { _, _ -> throw DeviceProofException(-7) },
                    { 1000 },
                    DeviceSessionDiagnostics { events += it },
                )
            val failure = assertIs<DeviceSessionResult.Failed>(proofFailure.prepare()).reason
            assertEquals(DeviceSessionStage.PROOF, failure.stage)
            assertEquals(-7, failure.sdkCode)
            assertTrue(api.ids.isEmpty())
            assertEquals(DeviceDiagnosticOutcome.FAILED, events.last().outcome)

            api.registerBlock = { http(403, "DEVICE-006") }
            val serverFailure =
                DeviceSessionRepositoryImpl(
                    api,
                    Storage(),
                    DevicePlatform.ANDROID,
                    DeviceAttestationPolicy.Required {
                        platform,
                        challenge,
                        ->
                        DeviceAttestationDto(platform.name, "proof", challenge)
                    },
                    { 1000 },
                )
            val rejected = assertIs<DeviceSessionResult.Failed>(serverFailure.prepare()).reason
            assertEquals(DeviceSessionStage.REGISTER, rejected.stage)
            assertEquals("DEVICE-006", rejected.code)
            assertNull(rejected.sdkCode)
            assertEquals(1, api.ids.size)
        }

    @Test
    fun `diagnostic logger failure does not change successful registration`() =
        runTest {
            val repository =
                DeviceSessionRepositoryImpl(
                    Api(),
                    Storage(),
                    DevicePlatform.ANDROID,
                    DeviceAttestationPolicy.Required {
                        platform,
                        challenge,
                        ->
                        DeviceAttestationDto(platform.name, "proof", challenge)
                    },
                    { 1000 },
                    DeviceSessionDiagnostics { error("logger failed") },
                )
            assertIs<DeviceSessionResult.Ready>(repository.prepare())
            val event =
                DeviceSessionDiagnostic(
                    DeviceSessionStage.REGISTER,
                    DeviceDiagnosticOutcome.FAILED,
                    serverCode = "secret-token-value",
                )
            assertFalse(event.toString().contains("secret-token-value"))
        }

    private fun repository(
        api: Api,
        storage: Storage,
        policy: DeviceAttestationPolicy = DeviceAttestationPolicy.PlatformOnly,
        now: () -> Long = { 1000 },
    ) = DeviceSessionRepositoryImpl(api, storage, DevicePlatform.ANDROID, policy, now) { "new" }

    private class Storage(
        var value: DeviceCredentials? = null,
    ) : DeviceCredentialStorage {
        var readFailure = false
        var rejectWrite = false

        override suspend fun read(): CredentialRead =
            if (readFailure) {
                CredentialRead.Failure()
            } else {
                value?.let(CredentialRead::Found)
                    ?: CredentialRead.Missing
            }

        override suspend fun write(credentials: DeviceCredentials): Boolean {
            if (rejectWrite) return false
            value = credentials
            return true
        }
    }

    private class Api : DeviceSessionApi {
        var registerBlock: suspend (
            DeviceRegistrationRequestDto,
        ) -> ApiResult<DeviceRegistrationResponseDto> = { success() }
        var refreshResult: ApiResult<DeviceRefreshResponseDto> =
            ApiResult.Success(
                DeviceRefreshResponseDto("access", 1800),
            )
        var refreshUsed: String? = null
        var challenges = 0
        val ids = mutableListOf<String>()

        override suspend fun register(request: DeviceRegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto> {
            ids += request.requestId
            return registerBlock(request)
        }

        override suspend fun refresh(request: DeviceRefreshRequestDto): ApiResult<DeviceRefreshResponseDto> {
            refreshUsed = request.refreshToken
            return refreshResult
        }

        override suspend fun challenge(): ApiResult<DeviceChallengeDto> {
            challenges++
            return ApiResult.Success(DeviceChallengeDto("challenge-$challenges", 300))
        }
    }

    private companion object {
        fun success() = ApiResult.Success(DeviceRegistrationResponseDto("access", "refresh", 1800))

        fun networkFailure() =
            ApiResult.Failure(NetworkFailure.Transport(TransportFailureReason.CONNECTION, MutationCertainty.UNKNOWN))

        fun http(
            status: Int,
            code: String,
            retry: String? = null,
        ) = ApiResult.Failure(NetworkFailure.HttpStatus(status, ApiError(code, null), retry, MutationCertainty.UNKNOWN))
    }
}
