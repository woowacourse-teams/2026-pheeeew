package com.pheeeew.feature.screens.settings

import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknameState
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.repository.device.DeviceNicknameRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun `로컬 검증을 통과한 입력만 확인할 수 있다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("기존 이름")
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            for (value in listOf("", "   ", "익명")) {
                viewModel.onNicknameChanged(value)
                assertFalse(viewModel.uiState.value.canConfirm)
                viewModel.saveNickname()
                runCurrent()
                assertEquals(null, viewModel.uiState.value.message)
            }
            assertTrue(repository.checkedNicknames.isEmpty())
            assertTrue(repository.updatedNicknames.isEmpty())
            for (value in listOf("가", "가".repeat(10), " New Name ", "닉네임1!😀", "새\n이름")) {
                viewModel.onNicknameChanged(value)
                assertTrue(viewModel.uiState.value.canConfirm)
            }
        }

    @Test
    fun `10자 이후 입력과 긴 붙여넣기를 거절하고 마지막 한글 조합은 유지한다`() =
        runViewModelTest {
            val viewModel = createViewModel(FakeDeviceNicknameRepository("기존 이름"))
            viewModel.openNicknameEditor()
            val prefix = "가".repeat(9)
            viewModel.onNicknameChanged(prefix + "ㄱ")
            viewModel.onNicknameChanged(prefix + "가")
            assertEquals(prefix + "가", viewModel.uiState.value.draft)
            viewModel.onNicknameChanged(prefix + "가나")
            assertEquals(prefix + "가", viewModel.uiState.value.draft)
            viewModel.onNicknameChanged("긴 붙여넣기".repeat(10))
            assertEquals(prefix + "가", viewModel.uiState.value.draft)
            viewModel.onNicknameChanged("😀".repeat(10))
            viewModel.onNicknameChanged("😀".repeat(11))
            assertEquals("😀".repeat(10), viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.canConfirm)
        }

    @Test
    fun `숫자 특수문자 이모지와 줄바꿈을 치환하지 않고 저장한다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("기존 이름")
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            val nickname = "N1!😀\nName"
            viewModel.onNicknameChanged(nickname)
            viewModel.saveNickname()
            runCurrent()
            assertEquals(listOf(nickname), repository.updatedNicknames)
            assertEquals(nickname, viewModel.uiState.value.nickname)
        }

    @Test
    fun `로딩은 지연 후 표시하고 최소시간 동안 중복 제출과 닫기를 막는다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("기존 이름").apply { updateDelayMillis = 200 }
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("새 이름")
            viewModel.saveNickname()
            viewModel.saveNickname()
            assertFalse(viewModel.uiState.value.canConfirm)
            assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
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
            viewModel.closeNicknameEditor()
            assertTrue(viewModel.uiState.value.isDialogVisible)
            assertTrue(viewModel.uiState.value.isSaving)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
            assertFalse(viewModel.uiState.value.isDialogVisible)
            assertEquals(listOf("새 이름"), repository.updatedNicknames)
        }

    @Test
    fun `빠른 저장 실패는 로딩 없이 입력과 하단 오류를 유지한다`() =
        runViewModelTest {
            val repository =
                FakeDeviceNicknameRepository(
                    "기존 이름",
                ).apply { updateResult = DeviceNicknameUpdateResult.FAILED }
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("새 이름")
            viewModel.saveNickname()
            runCurrent()
            assertEquals("새 이름", viewModel.uiState.value.draft)
            assertEquals(NicknameUiMessage.SAVE_FAILED, viewModel.uiState.value.message)
            assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)
            assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
            assertTrue(viewModel.uiState.value.canConfirm)
            assertEquals(0, testScheduler.currentTime)
        }

    @Test
    fun `서버 닉네임을 표시하고 편집 취소 후 다시 열면 현재 값으로 초기화한다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("민지")
            val viewModel = createViewModel(repository)
            assertEquals("민지", viewModel.uiState.value.nickname)

            viewModel.openNicknameEditor()
            assertEquals("민지", viewModel.uiState.value.draft)
            viewModel.onNicknameChanged("임시 이름")
            viewModel.closeNicknameEditor()
            viewModel.openNicknameEditor()

            assertEquals("민지", viewModel.uiState.value.draft)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `대소문자 변경은 중복 조회 없이 저장하고 서버 값 확인 후 닫는다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("alice")
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("Alice")
            viewModel.saveNickname()
            runCurrent()

            assertEquals(listOf("Alice"), repository.updatedNicknames)
            assertTrue(repository.checkedNicknames.isEmpty())
            assertEquals("Alice", viewModel.uiState.value.nickname)
            assertFalse(viewModel.uiState.value.isDialogVisible)
            assertNotNull(viewModel.uiState.value.nicknameChangedNoticeId)
        }

    @Test
    fun `저장 후 조회 실패 시 재저장하지 않고 닉네임 조회만 다시 시도한다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("이전 이름")
            repository.lookupResults += DeviceNicknameLookup.Found("이전 이름")
            repository.lookupResults += DeviceNicknameLookup.Failed
            repository.lookupResults += DeviceNicknameLookup.Found("새 이름")
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("새 이름")
            viewModel.saveNickname()
            runCurrent()

            assertTrue(viewModel.uiState.value.savedButUnverified)
            assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)
            assertEquals("새 이름", viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.isDialogVisible)

            viewModel.retryConfirmSavedNickname()
            runCurrent()

            assertEquals(listOf("새 이름"), repository.updatedNicknames)
            assertEquals("새 이름", viewModel.uiState.value.nickname)
            assertFalse(viewModel.uiState.value.isDialogVisible)
            assertNotNull(viewModel.uiState.value.nicknameChangedNoticeId)
        }

    @Test
    fun `입력 중에는 중복 확인하지 않고 확인 버튼을 누르면 현재 값만 확인한다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("기존 이름")
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("새 이름")
            assertTrue(repository.checkedNicknames.isEmpty())

            viewModel.saveNickname()
            runCurrent()

            assertEquals(listOf("새 이름"), repository.checkedNicknames)
            assertEquals(listOf("새 이름"), repository.updatedNicknames)
            assertEquals("새 이름", viewModel.uiState.value.nickname)
        }

    @Test
    fun `변경 없는 확인은 완료 안내를 만들지 않고 이전 안내 확인은 새 안내를 지우지 않는다`() =
        runViewModelTest {
            val repository = FakeDeviceNicknameRepository("기존 이름")
            val viewModel = createViewModel(repository)
            viewModel.openNicknameEditor()
            viewModel.saveNickname()
            runCurrent()
            assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)

            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("첫 이름")
            viewModel.saveNickname()
            runCurrent()
            val firstNotice = assertNotNull(viewModel.uiState.value.nicknameChangedNoticeId)
            viewModel.acknowledgeNicknameChanged(firstNotice)
            assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)

            viewModel.openNicknameEditor()
            viewModel.onNicknameChanged("다음 이름")
            viewModel.saveNickname()
            runCurrent()
            val secondNotice = assertNotNull(viewModel.uiState.value.nicknameChangedNoticeId)
            viewModel.acknowledgeNicknameChanged(firstNotice)
            assertEquals(secondNotice, viewModel.uiState.value.nicknameChangedNoticeId)
            viewModel.acknowledgeNicknameChanged(secondNotice)
            assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)
        }

    @Test
    fun `저장 결과 확인 대기 중에도 닫고 서버 값으로 다시 동기화한다`() =
        runViewModelTest {
            for (updateResult in listOf(
                DeviceNicknameUpdateResult.UPDATED,
                DeviceNicknameUpdateResult.OUTCOME_UNKNOWN,
            )) {
                val repository =
                    FakeDeviceNicknameRepository("이전 이름").apply {
                        this.updateResult = updateResult
                        lookupResults += DeviceNicknameLookup.Found("이전 이름")
                        lookupResults += DeviceNicknameLookup.Failed
                        lookupResults += DeviceNicknameLookup.Found("서버 이름")
                    }
                val viewModel = createViewModel(repository)
                viewModel.openNicknameEditor()
                viewModel.onNicknameChanged("새 이름")
                viewModel.saveNickname()
                runCurrent()
                assertTrue(viewModel.uiState.value.savedButUnverified)
                assertEquals(
                    updateResult == DeviceNicknameUpdateResult.OUTCOME_UNKNOWN,
                    viewModel.uiState.value.updateOutcomeUnknown,
                )

                viewModel.closeNicknameEditor()
                assertFalse(viewModel.uiState.value.isDialogVisible)
                assertFalse(viewModel.uiState.value.savedButUnverified)
                assertFalse(viewModel.uiState.value.updateOutcomeUnknown)
                assertEquals(null, viewModel.uiState.value.message)
                runCurrent()

                assertEquals(3, repository.lookupCalls)
                assertEquals(listOf("새 이름"), repository.updatedNicknames)
                assertEquals("서버 이름", viewModel.uiState.value.nickname)
                assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)
                viewModel.openNicknameEditor()
                assertEquals("서버 이름", viewModel.uiState.value.draft)
            }
        }

    @Test
    fun `재조회가 계속 실패하거나 저장 값과 달라도 모달을 닫을 수 있다`() =
        runViewModelTest {
            for (lookup in listOf(DeviceNicknameLookup.Failed, DeviceNicknameLookup.Found("다른 이름"))) {
                val repository =
                    FakeDeviceNicknameRepository("이전 이름").apply {
                        lookupResults += DeviceNicknameLookup.Found("이전 이름")
                        lookupResults += lookup
                        lookupResults += lookup
                    }
                val viewModel = createViewModel(repository)
                viewModel.openNicknameEditor()
                viewModel.onNicknameChanged("새 이름")
                viewModel.saveNickname()
                runCurrent()
                assertTrue(viewModel.uiState.value.savedButUnverified)

                viewModel.closeNicknameEditor()
                runCurrent()
                assertFalse(viewModel.uiState.value.isDialogVisible)
                assertFalse(viewModel.uiState.value.savedButUnverified)
                assertEquals(3, repository.lookupCalls)
                assertEquals(listOf("새 이름"), repository.updatedNicknames)
                assertEquals(null, viewModel.uiState.value.nicknameChangedNoticeId)
            }
        }

    private fun TestScope.createViewModel(repository: DeviceNicknameRepository): SettingsViewModel =
        SettingsViewModel(repository).also { runCurrent() }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class FakeDeviceNicknameRepository(
        initialNickname: String?,
    ) : DeviceNicknameRepository {
        override val nicknameState = MutableStateFlow(DeviceNicknameState())
        var nickname: String? = initialNickname
        val updatedNicknames = mutableListOf<String>()
        val checkedNicknames = mutableListOf<String>()
        val lookupResults = mutableListOf<DeviceNicknameLookup>()
        var lookupCalls = 0
        var updateDelayMillis = 0L
        var updateResult = DeviceNicknameUpdateResult.UPDATED

        override suspend fun findMyNickname(): DeviceNicknameLookup {
            lookupCalls++
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
            return DeviceNicknameAvailability.Checked(true)
        }

        override suspend fun updateNickname(nickname: String): DeviceNicknameUpdateResult {
            updatedNicknames += nickname
            delay(updateDelayMillis)
            if (updateResult == DeviceNicknameUpdateResult.UPDATED) this.nickname = nickname
            return updateResult
        }
    }
}
