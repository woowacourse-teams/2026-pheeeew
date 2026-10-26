package com.pheeeew.feature.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.utils.withDelayedLoadingIndicator
import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknamePolicy
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.model.device.DeviceNicknameValidation
import com.pheeeew.domain.repository.DeviceNicknameRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NicknameAvailabilityState { IDLE, CHECKING, AVAILABLE, TAKEN, FAILED }

data class SettingsUiState(
    val nickname: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isDialogVisible: Boolean = false,
    val draft: String = "",
    val validation: DeviceNicknameValidation = DeviceNicknameValidation.EMPTY,
    val availability: NicknameAvailabilityState = NicknameAvailabilityState.IDLE,
    val isSaving: Boolean = false,
    val isLoadingIndicatorVisible: Boolean = false,
    val message: NicknameUiMessage? = null,
    val savedButUnverified: Boolean = false,
    val updateOutcomeUnknown: Boolean = false,
    val nicknameChangedNoticeId: Long? = null,
) {
    val canConfirm: Boolean
        get() = isDialogVisible && validation == DeviceNicknameValidation.VALID && !isSaving
}

@Inject
class SettingsViewModel(
    private val repository: DeviceNicknameRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState = mutableUiState.asStateFlow()
    private var nextNicknameChangedNoticeId = 0L

    init {
        viewModelScope.launch {
            repository.nicknameState.collect { state ->
                mutableUiState.update {
                    it.copy(
                        nickname = state.nickname,
                        isLoading = state.isRefreshing || (!state.isConfirmed && !state.lookupFailed),
                        loadFailed = state.lookupFailed,
                    )
                }
            }
        }
        refreshNickname()
    }

    fun refreshNickname() {
        viewModelScope.launch { repository.findMyNickname() }
    }

    fun openNicknameEditor() {
        val state = mutableUiState.value
        if (state.isLoading || state.loadFailed || state.isSaving) return
        val draft = state.nickname.orEmpty()
        mutableUiState.update {
            it.copy(
                isDialogVisible = true,
                draft = draft,
                validation = DeviceNicknamePolicy.validate(draft),
                message = null,
                savedButUnverified = false,
                updateOutcomeUnknown = false,
                availability = NicknameAvailabilityState.IDLE,
            )
        }
    }

    fun closeNicknameEditor() {
        val state = mutableUiState.value
        if (state.isSaving) return
        mutableUiState.update {
            it.copy(
                isDialogVisible = false,
                message = null,
                savedButUnverified = false,
                updateOutcomeUnknown = false,
            )
        }
        if (state.savedButUnverified) refreshNickname()
    }

    fun onNicknameChanged(value: String) {
        if (!DeviceNicknamePolicy.canAcceptInput(value)) return
        mutableUiState.update {
            if (!it.isDialogVisible || it.isSaving || it.savedButUnverified) {
                it
            } else {
                it.copy(
                    draft = value,
                    validation = DeviceNicknamePolicy.validate(value),
                    message = null,
                    availability = NicknameAvailabilityState.IDLE,
                )
            }
        }
    }

    fun saveNickname() {
        val state = mutableUiState.value
        if (!state.canConfirm || state.savedButUnverified) return
        val candidate = DeviceNicknamePolicy.normalize(state.draft)
        if (candidate == state.nickname?.let(DeviceNicknamePolicy::normalize)) {
            closeNicknameEditor()
            return
        }
        val needsAvailabilityCheck = !candidate.equals(state.nickname?.trim(), ignoreCase = true)
        runNicknameOperation {
            if (needsAvailabilityCheck) {
                when (val result = repository.checkAvailability(candidate)) {
                    is DeviceNicknameAvailability.Checked -> {
                        if (!result.available) {
                            return@runNicknameOperation mutableUiState.value.copy(
                                availability = NicknameAvailabilityState.TAKEN,
                                message = NicknameUiMessage.DUPLICATE,
                            )
                        }
                    }

                    DeviceNicknameAvailability.Invalid -> {
                        return@runNicknameOperation mutableUiState.value.copy(
                            availability = NicknameAvailabilityState.FAILED,
                            message = NicknameUiMessage.INVALID,
                        )
                    }

                    DeviceNicknameAvailability.Failed -> {
                        return@runNicknameOperation mutableUiState.value.copy(
                            availability = NicknameAvailabilityState.FAILED,
                            message = NicknameUiMessage.AVAILABILITY_FAILED,
                        )
                    }
                }
            }
            when (repository.updateNickname(candidate)) {
                DeviceNicknameUpdateResult.UPDATED -> {
                    confirmSavedNickname(candidate, putSucceeded = true)
                }

                DeviceNicknameUpdateResult.OUTCOME_UNKNOWN -> {
                    confirmSavedNickname(candidate, putSucceeded = false)
                }

                DeviceNicknameUpdateResult.DUPLICATE -> {
                    mutableUiState.value.copy(
                        availability = NicknameAvailabilityState.TAKEN,
                        message = NicknameUiMessage.DUPLICATE,
                    )
                }

                DeviceNicknameUpdateResult.INVALID -> {
                    mutableUiState.value.copy(message = NicknameUiMessage.INVALID)
                }

                DeviceNicknameUpdateResult.FAILED -> {
                    mutableUiState.value.copy(message = NicknameUiMessage.SAVE_FAILED)
                }
            }
        }
    }

    fun retryConfirmSavedNickname() {
        val state = mutableUiState.value
        if (!state.savedButUnverified || !state.canConfirm) return
        runNicknameOperation {
            confirmSavedNickname(
                DeviceNicknamePolicy.normalize(state.draft),
                putSucceeded = !state.updateOutcomeUnknown,
            )
        }
    }

    fun acknowledgeNicknameChanged(noticeId: Long) {
        mutableUiState.update {
            if (it.nicknameChangedNoticeId == noticeId) it.copy(nicknameChangedNoticeId = null) else it
        }
    }

    private fun runNicknameOperation(block: suspend () -> SettingsUiState) {
        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            val result =
                withDelayedLoadingIndicator(
                    onVisibleChanged = { visible ->
                        mutableUiState.update { it.copy(isLoadingIndicatorVisible = visible) }
                    },
                    block = block,
                )
            val current = repository.nicknameState.value
            mutableUiState.value =
                result.copy(
                    nickname = current.nickname,
                    isLoading = current.isRefreshing || (!current.isConfirmed && !current.lookupFailed),
                    loadFailed = current.lookupFailed,
                    isSaving = false,
                    isLoadingIndicatorVisible = false,
                )
        }
    }

    private suspend fun confirmSavedNickname(
        candidate: String,
        putSucceeded: Boolean,
    ): SettingsUiState =
        when (val result = repository.findMyNickname()) {
            is DeviceNicknameLookup.Found -> {
                if (result.nickname?.let(DeviceNicknamePolicy::normalize) == candidate) {
                    mutableUiState.value.copy(
                        nickname = result.nickname,
                        isDialogVisible = false,
                        message = null,
                        savedButUnverified = false,
                        updateOutcomeUnknown = false,
                        nicknameChangedNoticeId = ++nextNicknameChangedNoticeId,
                    )
                } else {
                    mutableUiState.value.copy(
                        message =
                            if (putSucceeded) {
                                NicknameUiMessage.SAVED_UNCONFIRMED
                            } else {
                                NicknameUiMessage.CHANGE_UNCONFIRMED
                            },
                        savedButUnverified = putSucceeded,
                        updateOutcomeUnknown = false,
                    )
                }
            }

            is DeviceNicknameLookup.Failed -> {
                mutableUiState.value.copy(
                    message = NicknameUiMessage.RESULT_UNCONFIRMED,
                    savedButUnverified = true,
                    updateOutcomeUnknown = !putSucceeded,
                )
            }
        }
}
