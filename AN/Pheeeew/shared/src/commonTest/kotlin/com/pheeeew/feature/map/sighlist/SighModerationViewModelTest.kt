@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map.sighlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SighModerationViewModelTest {
    @Test
    fun `액션 메뉴에서 차단 확인 상태로 전환한다`() {
        val viewModel = SighModerationViewModel()

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
        val viewModel = SighModerationViewModel()

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
        val viewModel = SighModerationViewModel()
        viewModel.openActions(sighId = 42L, nickname = "테스터")
        viewModel.requestReport()

        viewModel.updateDescription("가".repeat(MAX_REPORT_REASON_LENGTH + 1))

        assertEquals(MAX_REPORT_REASON_LENGTH, viewModel.uiState.value.description.length)
    }

    @Test
    fun `신고 화면을 닫으면 입력 상태를 초기화한다`() {
        val viewModel = SighModerationViewModel()
        viewModel.openActions(sighId = 42L, nickname = "테스터")
        viewModel.requestReport()
        viewModel.selectReason("기타")

        viewModel.dismissReport()

        assertFalse(viewModel.uiState.value.isReportVisible)
        assertNull(viewModel.uiState.value.selectedReason)
        assertEquals("", viewModel.uiState.value.description)
    }
}
