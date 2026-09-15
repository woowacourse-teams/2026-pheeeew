package com.pheeeew.feature.map.sighlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.usecase.ReportSighUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SighModerationViewModel(
    private val reportSigh: ReportSighUseCase,
) : ViewModel() {
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

        val reason = current.reportRequestReason()
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { reportSigh(target.sighId, reason) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            reportTarget = null,
                            isSubmitting = false,
                            successMessage = "신고가 접수되었습니다.",
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.toReportErrorMessage(),
                        )
                    }
                }
        }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun Throwable.toReportErrorMessage(): String =
        when (this) {
            is ApiException.Network -> "인터넷 연결 상태를 확인해주세요."
            is ApiException.InvalidRequest -> "신고 내용을 확인해주세요."
            is ApiException.NotFound -> "신고할 한숨을 찾을 수 없습니다."
            else -> "신고에 실패했습니다. 잠시 후 다시 시도해주세요."
        }
}
