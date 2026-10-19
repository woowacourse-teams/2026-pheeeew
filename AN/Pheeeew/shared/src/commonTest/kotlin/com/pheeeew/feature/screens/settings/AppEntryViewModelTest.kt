package com.pheeeew.feature.screens.settings

import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknameState
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.model.device.DeviceSessionFailure
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.DeviceNicknameRepository
import com.pheeeew.domain.repository.DeviceSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppEntryViewModelTest {
    @Test
    fun `필수 입력도 유효한 닉네임만 확인할 수 있다`() =
        runViewModelTest {
            val nicknameRepository = FakeDeviceNicknameRepository()
            val sessionRepository = FakeDeviceSessionRepository(DeviceSessionResult.NeedsRegistration())
            val viewModel = createViewModel(sessionRepository, nicknameRepository)
            assertFalse(viewModel.uiState.value.canConfirm)
            for (value in listOf("   ", "익명")) {
                viewModel.onNicknameChanged(value)
                viewModel.submitNickname()
                runCurrent()
                assertFalse(viewModel.uiState.value.canConfirm)
            }
            assertTrue(nicknameRepository.checkedNicknames.isEmpty())
            assertTrue(sessionRepository.registeredNicknames.isEmpty())
            viewModel.onNicknameChanged("새 이름")
            assertTrue(viewModel.uiState.value.canConfirm)
        }

    @Test
    fun `최초 설정도 초과 입력을 거절하고 문자 종류 제한 없이 등록한다`() =
        runViewModelTest {
            val nicknameRepository = FakeDeviceNicknameRepository()
            val sessionRepository =
                FakeDeviceSessionRepository(DeviceSessionResult.NeedsRegistration()) {
                    nicknameRepository.nickname = it
                }
            val viewModel = createViewModel(sessionRepository, nicknameRepository)
            val maximum = "😀".repeat(10)
            viewModel.onNicknameChanged(maximum)
            viewModel.onNicknameChanged(maximum + "!")
            assertEquals(maximum, viewModel.uiState.value.nickname)
            assertTrue(viewModel.uiState.value.canConfirm)

            val nickname = "새이름12!😀\nHi"
            viewModel.onNicknameChanged(nickname)
            viewModel.submitNickname()
            runCurrent()
            assertEquals(listOf(nickname), sessionRepository.registeredNicknames)
            assertEquals(nickname, viewModel.uiState.value.currentNickname)
        }

    @Test
    fun `등록 실패는 최소 로딩 시간이 지난 뒤 입력창 하단 오류로 표시한다`() =
        runViewModelTest {
            val sessionRepository =
                FakeDeviceSessionRepository(DeviceSessionResult.NeedsRegistration()).apply {
                    registrationDelayMillis = 200
                    registrationResult =
                        DeviceSessionResult.Failed(DeviceSessionFailure(DeviceSessionFailureKind.NETWORK))
                }
            val viewModel = createViewModel(sessionRepository, FakeDeviceNicknameRepository())
            viewModel.onNicknameChanged("새 이름")
            viewModel.submitNickname()
            viewModel.submitNickname()
            assertFalse(viewModel.uiState.value.canConfirm)
            runCurrent()
            advanceTimeBy(149)
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
            assertEquals(null, viewModel.uiState.value.message)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)
            advanceTimeBy(299)
            runCurrent()
            assertTrue(viewModel.uiState.value.isSubmitting)
            assertEquals(null, viewModel.uiState.value.message)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, viewModel.uiState.value.phase)
            assertEquals("새 이름", viewModel.uiState.value.nickname)
            assertEquals(NicknameUiMessage.REGISTRATION_FAILED, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
            assertEquals(1, sessionRepository.registeredNicknames.size)
        }

    @Test
    fun `등록 후 결과 조회 실패는 입력창을 유지하고 조회만 재시도한다`() =
        runViewModelTest {
            val nicknameRepository =
                FakeDeviceNicknameRepository().apply {
                    lookupResults += DeviceNicknameLookup.Failed
                }
            val sessionRepository =
                FakeDeviceSessionRepository(DeviceSessionResult.NeedsRegistration()) {
                    nicknameRepository.nickname = it
                }
            val viewModel = createViewModel(sessionRepository, nicknameRepository)
            viewModel.onNicknameChanged("새 이름")
            viewModel.submitNickname()
            runCurrent()
            assertTrue(viewModel.uiState.value.isVerificationPending)
            assertEquals(NicknameUiMessage.RESULT_UNCONFIRMED, viewModel.uiState.value.message)
            viewModel.submitNickname()
            runCurrent()
            assertEquals(AppEntryPhase.READY, viewModel.uiState.value.phase)
            assertEquals(1, sessionRepository.registeredNicknames.size)
            assertEquals(1, nicknameRepository.checkedNicknames.size)
        }

    @Test
    fun `신규 기기는 닉네임 제출 전 등록하지 않고 입력값으로 등록한 뒤 확인한다`() =
        runViewModelTest {
            val nicknameRepository = FakeDeviceNicknameRepository()
            val sessionRepository =
                FakeDeviceSessionRepository(DeviceSessionResult.NeedsRegistration()) { nickname ->
                    nicknameRepository.nickname = nickname
                }
            val viewModel = createViewModel(sessionRepository, nicknameRepository)

            assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, viewModel.uiState.value.phase)
            assertEquals(0, sessionRepository.registeredNicknames.size)

            viewModel.onNicknameChanged("  새 사용자  ")
            assertEquals(0, nicknameRepository.checkedNicknames.size)
            viewModel.submitNickname()
            runCurrent()

            assertEquals(listOf("새 사용자"), nicknameRepository.checkedNicknames)
            assertEquals(listOf("새 사용자"), sessionRepository.registeredNicknames)
            assertEquals(AppEntryPhase.READY, viewModel.uiState.value.phase)
            assertEquals("새 사용자", viewModel.uiState.value.currentNickname)
            assertEquals(1L, viewModel.uiState.value.sessionGeneration)
        }

    @Test
    fun `기존 기기의 null 닉네임은 재등록 없이 저장하고 서버 조회로 확인한다`() =
        runViewModelTest {
            val nicknameRepository = FakeDeviceNicknameRepository()
            val sessionRepository = FakeDeviceSessionRepository(ready())
            val viewModel = createViewModel(sessionRepository, nicknameRepository)

            assertEquals(AppEntryPhase.NICKNAME_REQUIRED, viewModel.uiState.value.phase)
            viewModel.onNicknameChanged("이전 사용자")
            assertEquals(0, nicknameRepository.checkedNicknames.size)
            viewModel.submitNickname()
            runCurrent()

            assertEquals(listOf("이전 사용자"), nicknameRepository.checkedNicknames)
            assertEquals(0, sessionRepository.registeredNicknames.size)
            assertEquals(listOf("이전 사용자"), nicknameRepository.updatedNicknames)
            assertEquals(AppEntryPhase.READY, viewModel.uiState.value.phase)
            assertEquals("이전 사용자", viewModel.uiState.value.currentNickname)
            assertEquals(1L, viewModel.uiState.value.sessionGeneration)
        }

    @Test
    fun `닉네임 조회 오류는 미설정 상태로 오인하지 않고 재시도 화면을 표시한다`() =
        runViewModelTest {
            val nicknameRepository =
                FakeDeviceNicknameRepository().apply {
                    lookupResults += DeviceNicknameLookup.Failed
                }
            val viewModel = createViewModel(FakeDeviceSessionRepository(ready()), nicknameRepository)

            assertEquals(AppEntryPhase.FAILED, viewModel.uiState.value.phase)
            assertIs<NicknameUiMessage>(viewModel.uiState.value.message)
            assertEquals(NicknameUiMessage.NICKNAME_LOOKUP_FAILED, viewModel.uiState.value.message)
        }

    @Test
    fun `사용 중인 닉네임이면 신규 기기 등록을 보내지 않는다`() =
        runViewModelTest {
            val nicknameRepository = FakeDeviceNicknameRepository().apply { available = false }
            val sessionRepository = FakeDeviceSessionRepository(DeviceSessionResult.NeedsRegistration())
            val viewModel = createViewModel(sessionRepository, nicknameRepository)
            viewModel.onNicknameChanged("중복 닉네임")

            viewModel.submitNickname()
            runCurrent()

            assertEquals(1, nicknameRepository.checkedNicknames.size)
            assertEquals(0, sessionRepository.registeredNicknames.size)
            assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, viewModel.uiState.value.phase)
            assertEquals(NicknameAvailabilityState.TAKEN, viewModel.uiState.value.availability)
        }

    @Test
    fun `설정에서 변경하고 서버 확인한 닉네임을 진입 상태와 다른 설정 화면도 공유한다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository().apply { nickname = "이전 이름" }
            val entry = createViewModel(FakeDeviceSessionRepository(ready()), repository)
            val editor = SettingsViewModel(repository)
            val observer = SettingsViewModel(repository)
            runCurrent()
            observer.openNicknameEditor()
            observer.onNicknameChanged("편집 중")
            editor.openNicknameEditor()
            editor.onNicknameChanged("새 이름")
            editor.saveNickname()
            runCurrent()

            assertEquals(AppEntryPhase.READY, entry.uiState.value.phase)
            assertEquals("새 이름", entry.uiState.value.currentNickname)
            assertEquals("새 이름", observer.uiState.value.nickname)
            assertEquals("편집 중", observer.uiState.value.draft)
            assertTrue(observer.uiState.value.isDialogVisible)
        }

    @Test
    fun `사용 중인 기기가 서버에서 사라지면 자동 등록 없이 닉네임 등록 화면으로 돌아간다`() =
        runViewModelTest {
            var result: DeviceSessionResult = ready()
            val repository =
                object : DeviceSessionRepository {
                    override suspend fun prepare() = result

                    override suspend fun restore() = result

                    override suspend fun register(nickname: String): DeviceSessionResult =
                        error("Must wait for user input")

                    override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult {
                        result = DeviceSessionResult.NeedsRegistration()
                        return result
                    }
                }
            val manager = DeviceSessionManager(repository, backgroundScope) { 1000L }
            val viewModel = AppEntryViewModel(manager, FakeDeviceNicknameRepository().apply { nickname = "existing" })
            runCurrent()
            assertEquals(AppEntryPhase.READY, viewModel.uiState.value.phase)
            manager.deviceUnavailable(manager.accessToken())
            runCurrent()
            assertEquals(AppEntryPhase.REGISTRATION_REQUIRED, viewModel.uiState.value.phase)
        }

    @Test
    fun `기기 무효화 저장 실패는 등록 화면이 아니라 재시도 가능한 실패로 표시한다`() =
        runViewModelTest {
            val repository =
                object : DeviceSessionRepository {
                    override suspend fun prepare(): DeviceSessionResult = ready()

                    override suspend fun restore(): DeviceSessionResult = ready()

                    override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult =
                        DeviceSessionResult.Failed(DeviceSessionFailure(DeviceSessionFailureKind.STORAGE))
                }
            val manager = DeviceSessionManager(repository, backgroundScope) { 1000L }
            val viewModel = AppEntryViewModel(manager, FakeDeviceNicknameRepository().apply { nickname = "existing" })
            runCurrent()
            assertEquals(AppEntryPhase.READY, viewModel.uiState.value.phase)
            manager.deviceUnavailable(manager.accessToken())
            runCurrent()
            assertEquals(AppEntryPhase.FAILED, viewModel.uiState.value.phase)
            assertEquals(NicknameUiMessage.DEVICE_PREPARATION_FAILED, viewModel.uiState.value.message)
        }

    private fun TestScope.createViewModel(
        sessionRepository: DeviceSessionRepository,
        nicknameRepository: DeviceNicknameRepository,
    ) = AppEntryViewModel(
        DeviceSessionManager(sessionRepository, backgroundScope) { 1_000L },
        nicknameRepository,
    ).also { runCurrent() }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class FakeDeviceSessionRepository(
        private val initialResult: DeviceSessionResult,
        private val onRegister: (String) -> Unit = {},
    ) : DeviceSessionRepository {
        val registeredNicknames = mutableListOf<String>()
        var registrationDelayMillis = 0L
        var registrationResult: DeviceSessionResult? = null

        override suspend fun prepare(): DeviceSessionResult = initialResult

        override suspend fun restore(): DeviceSessionResult = initialResult

        override suspend fun register(nickname: String): DeviceSessionResult {
            registeredNicknames += nickname
            delay(registrationDelayMillis)
            val result = registrationResult ?: ready()
            if (result is DeviceSessionResult.Ready) onRegister(nickname)
            return result
        }
    }

    private class FakeDeviceNicknameRepository : DeviceNicknameRepository {
        override val nicknameState = MutableStateFlow(DeviceNicknameState())
        var nickname: String? = null
        val updatedNicknames = mutableListOf<String>()
        val checkedNicknames = mutableListOf<String>()
        val lookupResults = mutableListOf<DeviceNicknameLookup>()
        var available = true

        override suspend fun findMyNickname(): DeviceNicknameLookup {
            val result =
                if (lookupResults.isNotEmpty()) {
                    lookupResults.removeAt(
                        0,
                    )
                } else {
                    DeviceNicknameLookup.Found(nickname)
                }
            nicknameState.value =
                when (result) {
                    is DeviceNicknameLookup.Found -> DeviceNicknameState(nickname = result.nickname, isConfirmed = true)
                    DeviceNicknameLookup.Failed -> nicknameState.value.copy(lookupFailed = true)
                }
            return result
        }

        override suspend fun checkAvailability(nickname: String): DeviceNicknameAvailability {
            checkedNicknames += nickname
            return DeviceNicknameAvailability.Checked(available)
        }

        override suspend fun updateNickname(nickname: String): DeviceNicknameUpdateResult {
            updatedNicknames += nickname
            this.nickname = nickname
            return DeviceNicknameUpdateResult.UPDATED
        }
    }

    private companion object {
        fun ready() = DeviceSessionResult.Ready(DeviceAccess("access", 0, 10_000_000, 1))
    }
}
