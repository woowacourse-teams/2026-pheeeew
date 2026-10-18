package com.pheeeew.feature.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.core.utils.withDelayedLoadingIndicator
import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknamePolicy
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.model.device.DeviceNicknameValidation
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.device.DeviceNicknameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppEntryPhase { LOADING, REGISTRATION_REQUIRED, NICKNAME_REQUIRED, READY, FAILED }

data class AppEntryUiState(
    val phase: AppEntryPhase = AppEntryPhase.LOADING,
    val sessionGeneration: Long? = null,
    val nickname: String = "",
    val currentNickname: String? = null,
    val validation: DeviceNicknameValidation = DeviceNicknameValidation.EMPTY,
    val availability: NicknameAvailabilityState = NicknameAvailabilityState.IDLE,
    val isSubmitting: Boolean = false,
    val isLoadingIndicatorVisible: Boolean = false,
    val nicknameLocked: Boolean = false,
    val isVerificationPending: Boolean = false,
    val message: NicknameUiMessage? = null,
) {
    val canConfirm: Boolean
        get() =
            (phase == AppEntryPhase.REGISTRATION_REQUIRED || phase == AppEntryPhase.NICKNAME_REQUIRED) &&
                validation == DeviceNicknameValidation.VALID && !isSubmitting
}

class AppEntryViewModel(
    private val session: DeviceSessionManager,
    private val nicknameRepository: DeviceNicknameRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppEntryUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            nicknameRepository.nicknameState.collect { state ->
                if (mutableUiState.value.phase == AppEntryPhase.READY) {
                    if (!state.isConfirmed) {
                        retry()
                    } else {
                        mutableUiState.update {
                            it.copy(
                                currentNickname = state.nickname,
                                phase =
                                    if (state.nickname.isNullOrBlank()) {
                                        AppEntryPhase.NICKNAME_REQUIRED
                                    } else {
                                        AppEntryPhase.READY
                                    },
                            )
                        }
                    }
                }
            }
        }
        retry()
    }

    fun retry() {
        if (mutableUiState.value.isSubmitting) return
        val previousNickname = mutableUiState.value.nickname
        viewModelScope.launch {
            mutableUiState.value =
                AppEntryUiState(
                    nickname = previousNickname,
                    validation = DeviceNicknamePolicy.validate(previousNickname),
                )
            when (val result = session.restore()) {
                is DeviceSessionResult.Ready -> {
                    mutableUiState.value = resolveNickname(keepDraftWhenUnset = previousNickname.isNotEmpty())
                }

                is DeviceSessionResult.NeedsRegistration -> {
                    mutableUiState.update {
                        it.copy(
                            phase = AppEntryPhase.REGISTRATION_REQUIRED,
                            nickname = result.pendingNickname.orEmpty(),
                            nicknameLocked = result.pendingNickname != null,
                            validation = DeviceNicknamePolicy.validate(result.pendingNickname.orEmpty()),
                        )
                    }
                }

                is DeviceSessionResult.Failed -> {
                    mutableUiState.update {
                        it.copy(phase = AppEntryPhase.FAILED, message = NicknameUiMessage.DEVICE_PREPARATION_FAILED)
                    }
                }
            }
        }
    }

    fun onNicknameChanged(value: String) {
        if (!DeviceNicknamePolicy.canAcceptInput(value)) return
        val state = mutableUiState.value
        if (state.isSubmitting || state.phase == AppEntryPhase.READY || state.nicknameLocked ||
            state.isVerificationPending
        ) {
            return
        }
        mutableUiState.update {
            it.copy(
                nickname = value,
                validation = DeviceNicknamePolicy.validate(value),
                message = null,
                availability = NicknameAvailabilityState.IDLE,
            )
        }
    }

    fun submitNickname() {
        val state = mutableUiState.value
        if (!state.canConfirm) return
        val nickname = DeviceNicknamePolicy.normalize(state.nickname)
        mutableUiState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            val result =
                withDelayedLoadingIndicator(
                    onVisibleChanged = { visible ->
                        mutableUiState.update { it.copy(isLoadingIndicatorVisible = visible) }
                    },
                ) {
                    if (state.isVerificationPending) {
                        resolveNickname(keepDraftWhenUnset = true, inlineFailure = true)
                    } else {
                        submitNickname(state, nickname)
                    }
                }
            mutableUiState.value =
                result.copy(
                    currentNickname = nicknameRepository.nicknameState.value.nickname,
                    isSubmitting = false,
                    isLoadingIndicatorVisible = false,
                )
        }
    }

    private suspend fun submitNickname(
        state: AppEntryUiState,
        nickname: String,
    ): AppEntryUiState {
        val reusePendingRegistration = state.phase == AppEntryPhase.REGISTRATION_REQUIRED && state.nicknameLocked
        if (!reusePendingRegistration) {
            when (val result = nicknameRepository.checkAvailability(nickname)) {
                is DeviceNicknameAvailability.Checked -> {
                    if (!result.available) {
                        return mutableUiState.value.copy(
                            availability = NicknameAvailabilityState.TAKEN,
                            message = NicknameUiMessage.DUPLICATE,
                        )
                    }
                }

                DeviceNicknameAvailability.Invalid -> {
                    return mutableUiState.value.copy(
                        availability = NicknameAvailabilityState.FAILED,
                        message = NicknameUiMessage.INVALID,
                    )
                }

                DeviceNicknameAvailability.Failed -> {
                    return mutableUiState.value.copy(
                        availability = NicknameAvailabilityState.FAILED,
                        message = NicknameUiMessage.AVAILABILITY_FAILED,
                    )
                }
            }
        }
        return when (state.phase) {
            AppEntryPhase.REGISTRATION_REQUIRED -> {
                when (val result = session.register(nickname)) {
                    is DeviceSessionResult.Ready -> {
                        resolveNickname(keepDraftWhenUnset = true, inlineFailure = true)
                    }

                    is DeviceSessionResult.NeedsRegistration -> {
                        mutableUiState.value.copy(message = NicknameUiMessage.REGISTRATION_FAILED)
                    }

                    is DeviceSessionResult.Failed -> {
                        val duplicate = result.reason.statusCode == 409 && result.reason.code == "DEVICE-009"
                        val invalid = result.reason.code == "DEVICE-008"
                        mutableUiState.value.copy(
                            availability =
                                if (duplicate) {
                                    NicknameAvailabilityState.TAKEN
                                } else {
                                    mutableUiState.value.availability
                                },
                            nicknameLocked = !duplicate && !invalid,
                            message =
                                when {
                                    duplicate -> NicknameUiMessage.DUPLICATE
                                    invalid -> NicknameUiMessage.INVALID
                                    else -> NicknameUiMessage.REGISTRATION_FAILED
                                },
                        )
                    }
                }
            }

            AppEntryPhase.NICKNAME_REQUIRED -> {
                when (nicknameRepository.updateNickname(nickname)) {
                    DeviceNicknameUpdateResult.UPDATED, DeviceNicknameUpdateResult.OUTCOME_UNKNOWN -> {
                        resolveNickname(keepDraftWhenUnset = true, inlineFailure = true)
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

            else -> {
                mutableUiState.value
            }
        }
    }

    private suspend fun resolveNickname(
        keepDraftWhenUnset: Boolean = false,
        inlineFailure: Boolean = false,
    ): AppEntryUiState {
        val generation = (session.state.value as? DeviceSessionResult.Ready)?.access?.generation
        return when (val result = nicknameRepository.findMyNickname()) {
            is DeviceNicknameLookup.Found -> {
                if (!result.nickname.isNullOrBlank()) {
                    mutableUiState.value.copy(
                        phase = AppEntryPhase.READY,
                        sessionGeneration = generation,
                        currentNickname = result.nickname,
                        nickname = result.nickname,
                        nicknameLocked = false,
                        isVerificationPending = false,
                        message = null,
                    )
                } else {
                    val draft = if (keepDraftWhenUnset) mutableUiState.value.nickname else ""
                    mutableUiState.value.copy(
                        phase = AppEntryPhase.NICKNAME_REQUIRED,
                        nickname = draft,
                        currentNickname = null,
                        nicknameLocked = false,
                        isVerificationPending = false,
                        validation = DeviceNicknamePolicy.validate(draft),
                        message = if (keepDraftWhenUnset) NicknameUiMessage.RESULT_UNSET else null,
                    )
                }
            }

            DeviceNicknameLookup.Failed -> {
                mutableUiState.value.copy(
                    phase = if (inlineFailure) mutableUiState.value.phase else AppEntryPhase.FAILED,
                    isVerificationPending = inlineFailure,
                    message =
                        if (inlineFailure) {
                            NicknameUiMessage.RESULT_UNCONFIRMED
                        } else {
                            NicknameUiMessage.NICKNAME_LOOKUP_FAILED
                        },
                )
            }
        }
    }
}
