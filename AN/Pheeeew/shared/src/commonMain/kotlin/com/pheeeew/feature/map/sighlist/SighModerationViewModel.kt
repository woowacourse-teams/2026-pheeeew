package com.pheeeew.feature.map.sighlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.ReportSighUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SighModerationViewModel(
    private val blockUser: BlockUserUseCase,
    private val reportSigh: ReportSighUseCase,
    private val onBlockSucceeded: (Long) -> Unit = {},
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
        if (_uiState.value.isBlocking) return
        val target = _uiState.value.actionTarget ?: return
        _uiState.update { it.copy(actionTarget = null, blockTarget = target) }
    }

    fun dismissBlock() {
        _uiState.update { it.copy(blockTarget = null, blockErrorMessage = null) }
    }

    fun confirmBlock() {
        if (_uiState.value.isBlocking) return
        val target = _uiState.value.blockTarget ?: return
        _uiState.update {
            it.copy(
                blockTarget = null,
                blockErrorMessage = null,
                isBlocking = true,
            )
        }
        viewModelScope.launch {
            runCatching { blockUser(target.sighId) }
                .onSuccess {
                    onBlockSucceeded(target.sighId)
                    _uiState.update {
                        it.copy(
                            isBlocking = false,
                            successMessage = "차단되었습니다.",
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBlocking = false,
                            blockErrorMessage = error.toBlockErrorMessage(),
                        )
                    }
                }
        }
    }

    fun clearBlockError() {
        _uiState.update { it.copy(blockErrorMessage = null) }
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
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            reportTarget = null,
                            isSubmitting = false,
                            successMessage =
                                if (result.isNew) {
                                    "신고가 접수되었습니다."
                                } else {
                                    "이미 신고한 한숨입니다."
                                },
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

    private fun Throwable.toBlockErrorMessage(): String =
        when (this) {
            is ApiException.Conflict -> {
                when (code) {
                    "BLOCK-002" -> "내가 작성한 한숨은 차단할 수 없어요."
                    "BLOCK-003" -> "작성자 정보를 알 수 없는 한숨은 사용자를 차단할 수 없어요."
                    else -> "이 사용자는 차단할 수 없어요."
                }
            }

            is ApiException.Network -> {
                "인터넷 연결 상태를 확인해주세요."
            }

            is ApiException.NotFound -> {
                "차단할 한숨을 찾을 수 없습니다."
            }

            else -> {
                "차단에 실패했습니다. 잠시 후 다시 시도해주세요."
            }
        }
}
