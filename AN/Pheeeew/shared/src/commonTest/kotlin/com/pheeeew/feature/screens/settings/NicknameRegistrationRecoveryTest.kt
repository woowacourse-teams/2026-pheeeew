package com.pheeeew.feature.screens.settings

import com.pheeeew.core.network.ApiError
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.TransportFailureReason
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.data.local.device.CredentialRead
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.remote.api.DeviceSessionApi
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.remote.dto.DeviceChallengeDto
import com.pheeeew.data.remote.dto.DeviceRefreshRequestDto
import com.pheeeew.data.remote.dto.DeviceRefreshResponseDto
import com.pheeeew.data.remote.dto.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.dto.DeviceRegistrationResponseDto
import com.pheeeew.data.remote.dto.DeviceV3RegistrationRequestDto
import com.pheeeew.data.repository.DeviceSessionRepositoryImpl
import com.pheeeew.domain.model.device.DeviceCredentials
import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknameState
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.repository.DeviceNicknameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NicknameRegistrationRecoveryTest {
    @Test
    fun `실패 후 수정한 제출값을 재실행으로 복원하고 확인할 때만 이전 요청을 복구한다`() =
        audit {
            val fixture = Fixture()
            val first = fixture.start(this)
            first.onNicknameChanged("처음이름")
            first.submitNickname()
            runCurrent()
            assertEquals(1, fixture.api.names.size)
            assertFalse(first.uiState.value.isSubmitting)
            first.onNicknameChanged("바꾼이름")
            first.submitNickname()
            runCurrent()
            assertEquals("바꾼이름", fixture.storage.value?.nicknameDraft)
            assertEquals("바꾼이름", fixture.storage.value?.pendingNickname)
            val restarted = fixture.start(this)
            assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, restarted.uiState.value.phase)
            assertEquals("바꾼이름", restarted.uiState.value.nickname)
            assertEquals(2, fixture.api.names.size)
            fixture.api.fail = false
            restarted.submitNickname()
            runCurrent()
            assertEquals(listOf("처음이름", "바꾼이름", "바꾼이름"), fixture.api.names)
            assertEquals(
                1,
                fixture.api.ids
                    .distinct()
                    .size,
            )
            assertTrue(fixture.nicknames.updated.isEmpty())
            assertEquals(AppEntryPhase.READY, restarted.uiState.value.phase)
            assertEquals("바꾼이름", fixture.api.nickname)
            assertNull(fixture.storage.value?.nicknameDraft)
        }

    @Test
    fun `서버 등록 후 응답 유실은 자신의 닉네임 중복 검사 없이 같은 요청으로 복구한다`() =
        audit {
            val fixture = Fixture()
            fixture.api.applyBeforeFailure = true
            val entry = fixture.start(this)
            entry.onNicknameChanged("처음이름")
            entry.submitNickname()
            runCurrent()
            assertEquals("처음이름", fixture.api.nickname)
            fixture.api.fail = false
            entry.submitNickname()
            runCurrent()
            assertEquals(AppEntryPhase.READY, entry.uiState.value.phase)
            assertEquals(listOf("처음이름"), fixture.nicknames.checked)
            assertTrue(fixture.nicknames.updated.isEmpty())
            assertEquals(
                1,
                fixture.api.ids
                    .distinct()
                    .size,
            )
        }

    @Test
    fun `현재 입력이 거절되면 다시 전송하지 않고 안내한 뒤 새 요청으로 등록한다`() =
        audit {
            for ((status, code) in listOf(409 to "DEVICE-009", 400 to "DEVICE-008")) {
                val fixture = Fixture()
                val first = fixture.start(this)
                first.onNicknameChanged("처음이름")
                first.submitNickname()
                runCurrent()
                fixture.api.fail = false
                fixture.api.rejection = Triple("바꾼이름", status, code)
                val restarted = fixture.start(this)
                restarted.onNicknameChanged("바꾼이름")
                restarted.submitNickname()
                runCurrent()
                assertEquals(listOf("처음이름", "바꾼이름"), fixture.api.names)
                assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, restarted.uiState.value.phase)
                assertEquals(
                    if (code == "DEVICE-009") NicknameUiMessage.DUPLICATE else NicknameUiMessage.INVALID,
                    restarted.uiState.value.message,
                )
                assertNull(fixture.storage.value?.pendingRequestId)
                restarted.onNicknameChanged("세번째")
                restarted.submitNickname()
                runCurrent()
                assertEquals(listOf("처음이름", "바꾼이름", "세번째"), fixture.api.names)
                assertEquals(
                    2,
                    fixture.api.ids
                        .distinct()
                        .size,
                )
                assertEquals(AppEntryPhase.READY, restarted.uiState.value.phase)
                assertEquals("세번째", fixture.api.nickname)
            }
        }

    @Test
    fun `재시도 창 안의 응답 유실은 같은 요청으로 복구한 뒤 현재 입력으로 변경한다`() =
        audit {
            val fixture = Fixture()
            fixture.api.applyBeforeFailure = true
            val entry = fixture.start(this)
            entry.onNicknameChanged("처음이름")
            entry.submitNickname()
            runCurrent()
            fixture.api.fail = false
            entry.onNicknameChanged("바꾼이름")
            entry.submitNickname()
            runCurrent()
            assertEquals(listOf("처음이름", "바꾼이름"), fixture.api.names)
            assertEquals(
                1,
                fixture.api.ids
                    .distinct()
                    .size,
            )
            assertEquals(listOf("바꾼이름"), fixture.nicknames.updated)
            assertEquals(AppEntryPhase.READY, entry.uiState.value.phase)
            assertEquals("바꾼이름", fixture.api.nickname)
            assertNull(fixture.storage.value?.nicknameDraft)
        }

    @Test
    fun `재시도 창이 지나면 현재 입력으로 새 요청을 등록한다`() =
        audit {
            val fixture = Fixture()
            fixture.api.applyBeforeFailure = true
            val first = fixture.start(this)
            first.onNicknameChanged("처음이름")
            first.submitNickname()
            runCurrent()
            fixture.api.fail = false
            fixture.api.expired += fixture.api.ids.single()
            val restarted = fixture.start(this)
            restarted.onNicknameChanged("바꾼이름")
            restarted.submitNickname()
            runCurrent()
            assertEquals(listOf("처음이름", "바꾼이름", "바꾼이름"), fixture.api.names)
            assertEquals(
                2,
                fixture.api.ids
                    .distinct()
                    .size,
            )
            assertTrue(fixture.nicknames.updated.isEmpty())
            assertEquals(AppEntryPhase.READY, restarted.uiState.value.phase)
            assertEquals("바꾼이름", fixture.api.nickname)
        }

    @Test
    fun `재시도 창이 지난 뒤 이전 닉네임이 남은 기기에 묶여 있으면 중복으로 안내한다`() =
        audit {
            val fixture = Fixture()
            fixture.api.applyBeforeFailure = true
            val first = fixture.start(this)
            first.onNicknameChanged("처음이름")
            first.submitNickname()
            runCurrent()
            fixture.api.fail = false
            fixture.api.expired += fixture.api.ids.single()
            val restarted = fixture.start(this)
            assertEquals("처음이름", restarted.uiState.value.nickname)
            restarted.submitNickname()
            runCurrent()
            assertEquals(listOf("처음이름", "처음이름", "처음이름"), fixture.api.names)
            assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, restarted.uiState.value.phase)
            assertEquals(NicknameUiMessage.DUPLICATE, restarted.uiState.value.message)
            assertNull(fixture.storage.value?.pendingRequestId)
            restarted.onNicknameChanged("새이름")
            restarted.submitNickname()
            runCurrent()
            assertEquals(4, fixture.api.names.size)
            assertEquals(AppEntryPhase.READY, restarted.uiState.value.phase)
            assertEquals("새이름", fixture.api.nickname)
        }

    @Test
    fun `등록 후 조회 실패도 입력을 수정하고 재실행 뒤 현재 닉네임을 확정한다`() =
        audit {
            val fixture = Fixture()
            fixture.api.fail = false
            fixture.nicknames.lookupFails = true
            val first = fixture.start(this)
            first.onNicknameChanged("처음이름")
            first.submitNickname()
            runCurrent()
            assertEquals(NicknameUiMessage.RESULT_UNCONFIRMED, first.uiState.value.message)
            first.onNicknameChanged("바꾼이름")
            first.submitNickname()
            runCurrent()
            assertEquals("바꾼이름", fixture.storage.value?.nicknameDraft)
            val restarted = fixture.start(this)
            assertEquals(AppEntryPhase.NICKNAME_REQUIRED, restarted.uiState.value.phase)
            assertEquals("바꾼이름", restarted.uiState.value.nickname)
            assertTrue(fixture.nicknames.updated.isEmpty())
            fixture.nicknames.lookupFails = false
            restarted.submitNickname()
            runCurrent()
            assertEquals(AppEntryPhase.READY, restarted.uiState.value.phase)
            assertEquals(listOf("바꾼이름"), fixture.nicknames.updated)
            assertEquals(1, fixture.api.names.size)
        }

    @Test
    fun `이전 버전의 미완료 요청도 전송 없이 편집 가능한 입력값으로 복원한다`() =
        audit {
            val fixture = Fixture()
            fixture.storage.value =
                Json.decodeFromString<DeviceCredentials>(
                    """{"pendingRequestId":"legacy","pendingStartedAtMillis":1000,"pendingNickname":"이전이름"}""",
                )
            val entry = fixture.start(this)
            assertEquals("이전이름", entry.uiState.value.nickname)
            assertTrue(fixture.api.names.isEmpty())
            entry.onNicknameChanged("새이름")
            assertEquals("새이름", entry.uiState.value.nickname)
        }

    @Test
    fun `중복 확인과 저장 실패는 입력을 유지하고 재제출할 수 있다`() =
        audit {
            val fixture = Fixture()
            val entry = fixture.start(this)
            entry.onNicknameChanged("처음이름")
            fixture.storage.rejectWrite = true
            entry.submitNickname()
            entry.submitNickname()
            runCurrent()
            assertEquals(NicknameUiMessage.SAVE_FAILED, entry.uiState.value.message)
            assertFalse(entry.uiState.value.isSubmitting)
            assertTrue(fixture.api.names.isEmpty())
            fixture.storage.rejectWrite = false
            entry.onNicknameChanged("바꾼이름")
            entry.submitNickname()
            entry.submitNickname()
            runCurrent()
            assertEquals(1, fixture.api.names.size)
            assertEquals("바꾼이름", entry.uiState.value.nickname)
        }

    private fun audit(block: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class Fixture {
        val storage = Storage()
        val api = Api()
        val nicknames = Nicknames(api)
        private var nextId = 0

        fun start(scope: TestScope): AppEntryViewModel {
            val repository =
                DeviceSessionRepositoryImpl(
                    api,
                    storage,
                    DevicePlatform.ANDROID,
                    DeviceAttestationPolicy.PlatformOnly,
                    { 1000L },
                    { "request-${++nextId}" },
                )
            return AppEntryViewModel(DeviceSessionManager(repository, scope.backgroundScope) { 1000L }, nicknames)
                .also { scope.runCurrent() }
        }
    }

    private class Storage : DeviceCredentialStorage {
        var value: DeviceCredentials? = null
        var rejectWrite = false

        override suspend fun read() = value?.let(CredentialRead::Found) ?: CredentialRead.Missing

        override suspend fun write(credentials: DeviceCredentials): Boolean {
            if (rejectWrite) return false
            value = credentials
            return true
        }
    }

    private class Api : DeviceSessionApi {
        var fail = true
        var applyBeforeFailure = false
        var rejection: Triple<String, Int, String>? = null
        val expired = mutableSetOf<String>()
        private val devices = mutableMapOf<String, String>()
        private var current: String? = null
        var nickname: String?
            get() = current?.let(devices::get)
            set(value) {
                devices[checkNotNull(current)] = checkNotNull(value)
            }
        val names = mutableListOf<String>()
        val ids = mutableListOf<String>()

        fun isTaken(nickname: String) = devices.values.any { it.equals(nickname, ignoreCase = true) }

        override suspend fun register(request: DeviceRegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto> =
            error("Legacy registration must not be called")

        override suspend fun register(
            request: DeviceV3RegistrationRequestDto,
        ): ApiResult<DeviceRegistrationResponseDto> {
            names += request.nickname
            ids += request.requestId
            if (request.requestId in expired) return rejected(409, "DEVICE-002")
            if (request.requestId !in devices) {
                rejection?.takeIf { request.nickname == it.first }?.let { return rejected(it.second, it.third) }
                if (isTaken(request.nickname)) return rejected(409, "DEVICE-009")
            }
            if (!fail || applyBeforeFailure) {
                // A retried request keeps the nickname of the device it already created.
                devices.getOrPut(request.requestId) { request.nickname }
                current = request.requestId
            }
            return if (fail) {
                ApiResult.Failure(
                    NetworkFailure.Transport(TransportFailureReason.CONNECTION, MutationCertainty.UNKNOWN),
                )
            } else {
                ApiResult.Success(DeviceRegistrationResponseDto("access", "refresh", 1800))
            }
        }

        private fun rejected(
            status: Int,
            code: String,
        ) = ApiResult.Failure(
            NetworkFailure.HttpStatus(status, ApiError(code, null), mutationCertainty = MutationCertainty.NOT_APPLIED),
        )

        override suspend fun refresh(request: DeviceRefreshRequestDto) =
            ApiResult.Success(DeviceRefreshResponseDto("access", 1800))

        override suspend fun challenge(): ApiResult<DeviceChallengeDto> = error("No attestation required")
    }

    private class Nicknames(
        private val api: Api,
    ) : DeviceNicknameRepository {
        override val nicknameState = MutableStateFlow(DeviceNicknameState())
        var lookupFails = false
        val checked = mutableListOf<String>()
        val updated = mutableListOf<String>()

        override suspend fun findMyNickname(): DeviceNicknameLookup {
            if (lookupFails) return DeviceNicknameLookup.Failed()
            nicknameState.value = DeviceNicknameState(nickname = api.nickname, isConfirmed = true)
            return DeviceNicknameLookup.Found(api.nickname)
        }

        override suspend fun checkAvailability(nickname: String): DeviceNicknameAvailability {
            checked += nickname
            return DeviceNicknameAvailability.Checked(!api.isTaken(nickname))
        }

        override suspend fun updateNickname(nickname: String): DeviceNicknameUpdateResult {
            updated += nickname
            api.nickname = nickname
            return DeviceNicknameUpdateResult.UPDATED
        }
    }
}
