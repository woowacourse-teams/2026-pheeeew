package com.pheeeew.feature.map.sighlist

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SighModerationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SighModerationUiState())
    val uiState: StateFlow<SighModerationUiState> = _uiState.asStateFlow()

    fun openActions(
        sighId: Long,
        nickname: String,
    ) {
        _uiState.update {
            it.copy(actionTarget = SighModerationTarget(sighId, nickname))
        }
    }

    fun dismissActions() {
        _uiState.update { it.copy(actionTarget = null) }
    }

    fun requestBlock() {
        val target = _uiState.value.actionTarget ?: return
        _uiState.update { it.copy(actionTarget = null, blockTarget = target) }
    }

    fun dismissBlock() {
        _uiState.update { it.copy(blockTarget = null) }
    }

    fun confirmBlock() {
        val target = _uiState.value.blockTarget ?: return
        _uiState.update { it.copy(blockTarget = null) }

        // TODO: 차단 API가 확정되면 target.sighId와 target.nickname으로 작성자 차단을 요청한다.
    }

    fun requestReport() {
        val target = _uiState.value.actionTarget ?: return
        _uiState.value =
            SighModerationUiState(
                reportTarget = target,
                selectedReason = DEFAULT_SIGH_REPORT_REASON,
            )
    }

    fun selectReason(reason: String) {
        if (reason !in sighReportReasons || _uiState.value.isSubmitting) return
        _uiState.update { it.copy(selectedReason = reason, errorMessage = null) }
    }

    fun updateDescription(description: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                description = description.take(MAX_REPORT_REASON_LENGTH),
                errorMessage = null,
            )
        }
    }

    fun dismissReport() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { SighModerationUiState(successMessage = it.successMessage) }
    }

    fun submitReport() {
        val current = _uiState.value
        val target = current.reportTarget ?: return
        if (!current.canSubmitReport) return

        // TODO: 신고 API가 확정되면 target.sighId와 current.reportRequestReason()으로 신고를 제출한다.
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
