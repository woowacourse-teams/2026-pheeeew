@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map.sighlist

import com.pheeeew.data.local.device.DeviceIdStorage
import com.pheeeew.data.remote.block.api.DeviceBlockApi
import com.pheeeew.data.remote.block.dto.DeviceBlockCreateRequestDto
import com.pheeeew.data.remote.block.dto.DeviceBlockResponseDto
import com.pheeeew.data.remote.report.api.SighReportApi
import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.data.remote.report.dto.SighReportResponseDto
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.ReportSighUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SighModerationViewModelTest {
    @Test
    fun `액션 메뉴에서 차단 확인 상태로 전환한다`() {
        val viewModel = createViewModel()

        viewModel.openActions(sighId = 42L, nickname = "테스터")
        viewModel.requestBlock()

        assertNull(viewModel.uiState.value.actionTarget)
        assertEquals(
            SighModerationTarget(sighId = 42L, nickname = "테스터"),
            viewModel.uiState.value.blockTarget,
        )

        viewModel.confirmBlock()

        assertNull(viewModel.uiState.value.blockTarget)
    }

    @Test
    fun `액션 메뉴에서 신고 화면으로 전환하고 입력 상태를 관리한다`() {
        val viewModel = createViewModel()

        viewModel.openActions(sighId = 42L, nickname = "테스터")
        viewModel.requestReport()

        assertEquals(DEFAULT_SIGH_REPORT_REASON, viewModel.uiState.value.selectedReason)

        viewModel.selectReason("사이버 괴롭힘")
        viewModel.updateDescription("상세 설명")

        val state = viewModel.uiState.value
        assertNull(state.actionTarget)
        assertEquals(42L, state.reportTarget?.sighId)
        assertEquals("사이버 괴롭힘", state.selectedReason)
        assertEquals("상세 설명", state.description)
        assertTrue(state.canSubmitReport)
    }

    @Test
    fun `상세 설명은 최대 이백 자까지 보관한다`() {
        val viewModel = createViewModel()
        viewModel.openActions(sighId = 42L, nickname = "테스터")
        viewModel.requestReport()

        viewModel.updateDescription("가".repeat(MAX_REPORT_REASON_LENGTH + 1))

        assertEquals(MAX_REPORT_REASON_LENGTH, viewModel.uiState.value.description.length)
    }

    @Test
    fun `신고 화면을 닫으면 입력 상태를 초기화한다`() {
        val viewModel = createViewModel()
        viewModel.openActions(sighId = 42L, nickname = "테스터")
        viewModel.requestReport()
        viewModel.selectReason("기타")

        viewModel.dismissReport()

        assertFalse(viewModel.uiState.value.isReportVisible)
        assertNull(viewModel.uiState.value.selectedReason)
        assertEquals("", viewModel.uiState.value.description)
    }

    @Test
    fun `신고 제출 중에는 중복 요청을 보내지 않고 성공 상태로 전환한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val api = RecordingSighReportApi()
                val viewModel = createViewModel(api)
                viewModel.openActions(sighId = 42L, nickname = "테스터")
                viewModel.requestReport()

                viewModel.submitReport()
                viewModel.submitReport()
                assertTrue(viewModel.uiState.value.isSubmitting)

                advanceUntilIdle()

                assertEquals(1, api.callCount)
                assertFalse(viewModel.uiState.value.isReportVisible)
                assertEquals("신고가 접수되었습니다.", viewModel.uiState.value.successMessage)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(api: SighReportApi = FakeSighReportApi): SighModerationViewModel =
        SighModerationViewModel(
            blockUser = BlockUserUseCase(FakeDeviceBlockApi),
            reportSigh =
                ReportSighUseCase(
                    api = api,
                    deviceIdStorage = FakeDeviceIdStorage,
                ),
        )

    private object FakeDeviceBlockApi : DeviceBlockApi {
        override suspend fun create(request: DeviceBlockCreateRequestDto): DeviceBlockResponseDto =
            DeviceBlockResponseDto(
                blockId = 1L,
                sighId = request.sighId,
                nickname = "테스터",
                createdAt = "2026-09-15T00:00:00Z",
            )
    }

    private object FakeDeviceIdStorage : DeviceIdStorage {
        override fun getOrCreate(): String = "device-id"
    }

    private object FakeSighReportApi : SighReportApi {
        override suspend fun create(request: SighReportCreateRequestDto): SighReportResponseDto =
            SighReportResponseDto(
                id = 1L,
                sighId = request.sighId,
                reason = request.reason,
                createdAt = "2026-09-15T00:00:00Z",
            )
    }

    private class RecordingSighReportApi : SighReportApi {
        var callCount = 0

        override suspend fun create(request: SighReportCreateRequestDto): SighReportResponseDto {
            callCount += 1
            return SighReportResponseDto(
                id = 1L,
                sighId = request.sighId,
                reason = request.reason,
                createdAt = "2026-09-15T00:00:00Z",
            )
        }
    }
}
