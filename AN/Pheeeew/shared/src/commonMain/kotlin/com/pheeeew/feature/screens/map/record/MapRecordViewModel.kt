package com.pheeeew.feature.screens.map.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

class MapRecordViewModel(
    private val isWithinRecordRadius: IsWithinEmotionRecordRadiusUseCase,
    private val registrationRepository: EmotionRegistrationRepository,
) : ViewModel() {
    private val _uiModel = MutableStateFlow(RecordBottomSheetUiModel())
    val uiModel: StateFlow<RecordBottomSheetUiModel> = _uiModel.asStateFlow()

    private var pendingRegistration: EmotionRegistration? = null
    private val _notice = MutableStateFlow<RecordNoticeUiModel?>(null)
    val notice = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    val groupOptions =
        listOf(
            GroupSelectorGroupUiModel(id = PERSONAL_GROUP_ID, name = "개인", stampLabel = "개인"),
            GroupSelectorGroupUiModel(id = NO_GROUP_ID, name = "그룹 없음", stampLabel = "없음"),
        )

    fun open(emotion: EmotionTypeUiModel) {
        pendingRegistration = null
        _uiModel.value =
            RecordBottomSheetUiModel(
                step = RecordFlowStepUiModel.Input,
                selectedEmotion = emotion,
            )
    }

    fun dismiss() {
        if (_uiModel.value.isSubmitting) return
        pendingRegistration = null
        _uiModel.value = RecordBottomSheetUiModel()
    }

    fun onInputModeChange(inputMode: RecordInputModeUiModel) {
        _uiModel.value = _uiModel.value.copy(inputMode = inputMode)
    }

    fun onMemoChange(memo: String) {
        _uiModel.value = _uiModel.value.copy(memo = memo.take(MAX_MEMO_LENGTH))
    }

    fun onNext(
        currentLocation: CurrentLocation?,
        recordingFilePath: String?,
    ) {
        _uiModel.value = _uiModel.value.copy(recordingFilePath = recordingFilePath)
        moveToLocationSelection(currentLocation)
    }

    fun onSkip(currentLocation: CurrentLocation?) {
        _uiModel.value = _uiModel.value.copy(memo = "", recordingFilePath = null)
        moveToLocationSelection(currentLocation)
    }

    fun onBackToInput() {
        if (_uiModel.value.isSubmitting) return
        pendingRegistration = null
        _uiModel.value =
            _uiModel.value.copy(
                step = RecordFlowStepUiModel.Input,
                locationMessage = null,
                submissionMessage = null,
            )
    }

    fun onLocationSelected(
        latitude: Double,
        longitude: Double,
    ) {
        val origin = _uiModel.value.origin
        if (_uiModel.value.step != RecordFlowStepUiModel.LocationSelection || origin == null) {
            return
        }
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return
        }
        if (_uiModel.value.isSubmitting) return
        val candidate = constrainToRecordRadius(origin, GeoCoordinate(latitude, longitude))
        if (candidate != _uiModel.value.selectedCoordinate) pendingRegistration = null
        val isInRange = isWithinRecordRadius(origin, candidate)
        _uiModel.value =
            _uiModel.value.copy(
                selectedCoordinate = candidate,
                isSelectedCoordinateInRange = isInRange,
                locationMessage =
                    when {
                        isInRange -> null
                        else -> "원 안으로 옮겨주세요"
                    },
                submissionMessage = null,
            )
    }

    fun onOriginLocationAvailable(currentLocation: CurrentLocation) {
        val state = _uiModel.value
        if (state.step != RecordFlowStepUiModel.LocationSelection || state.origin != null) return
        val origin = GeoCoordinate(currentLocation.latitude, currentLocation.longitude)
        _uiModel.value =
            state.copy(
                origin = origin,
                selectedCoordinate = origin,
                isSelectedCoordinateInRange = true,
                locationMessage = null,
            )
    }

    fun onConfirmLocation() {
        val state = _uiModel.value
        if (state.step != RecordFlowStepUiModel.LocationSelection || state.isSubmitting ||
            !state.isSelectedCoordinateInRange
        ) {
            return
        }
        val emotion = state.selectedEmotion ?: return
        val coordinate = state.selectedCoordinate ?: return
        val origin = state.origin ?: return
        if (!isWithinRecordRadius(origin, coordinate)) return
        val content =
            when (state.inputMode) {
                RecordInputModeUiModel.Memo -> {
                    state.memo
                        .takeIf { it.isNotBlank() }
                        ?.let { EmotionRegistrationContent.Memo(it) } ?: EmotionRegistrationContent.None
                }

                RecordInputModeUiModel.Recording -> {
                    state.recordingFilePath
                        ?.let { EmotionRegistrationContent.Audio(it) } ?: EmotionRegistrationContent.None
                }
            }
        val registration =
            pendingRegistration ?: EmotionRegistration(
                requestId = Uuid.random().toString(),
                state = EmotionState.valueOf(emotion.name),
                coordinate = coordinate,
                rotationDegrees = 0.0,
                groupId = state.selectedGroupId.takeUnless { it == PERSONAL_GROUP_ID || it == NO_GROUP_ID },
                content = content,
            ).also { pendingRegistration = it }
        _notice.value = null
        _uiModel.value = state.copy(isSubmitting = true, submissionMessage = "감정을 등록하고 있어요")
        viewModelScope.launch {
            val result =
                try {
                    registrationRepository.register(registration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    EmotionRegistrationResult.Unavailable
                }
            when (result) {
                is EmotionRegistrationResult.Success -> {
                    _uiModel.value = RecordBottomSheetUiModel()
                    pendingRegistration = null
                    _notice.value = RecordNoticeUiModel("선택한 위치에 감정을 남겼어요", false)
                }

                else -> {
                    _uiModel.value = _uiModel.value.copy(isSubmitting = false, submissionMessage = null)
                    _notice.value =
                        RecordNoticeUiModel(
                            if (result == EmotionRegistrationResult.AudioUnavailable) {
                                "녹음 등록은 아직 사용할 수 없어요. 잠시 후 다시 시도해 주세요"
                            } else {
                                "감정을 등록하지 못했어요. 다시 시도해 주세요"
                            },
                            true,
                        )
                }
            }
        }
    }

    fun onGroupSelectorOpen() {
        _uiModel.value =
            _uiModel.value.copy(
                isGroupSelectorVisible = true,
                pendingGroupId = _uiModel.value.selectedGroupId,
                groupDialProgress =
                    groupOptions
                        .indexOfFirst { it.id == _uiModel.value.selectedGroupId }
                        .coerceAtLeast(0)
                        .toFloat(),
            )
    }

    fun onGroupSelectorDismiss() {
        _uiModel.value =
            _uiModel.value.copy(
                isGroupSelectorVisible = false,
                pendingGroupId = _uiModel.value.selectedGroupId,
                groupDialProgress =
                    groupOptions
                        .indexOfFirst { it.id == _uiModel.value.selectedGroupId }
                        .coerceAtLeast(0)
                        .toFloat(),
            )
    }

    fun onGroupDialProgressChange(progress: Float) {
        _uiModel.value = _uiModel.value.copy(groupDialProgress = progress)
    }

    fun onGroupDialProgressSettle(progress: Float) {
        val index = progress.roundToInt().coerceIn(groupOptions.indices)
        val group = groupOptions[index]
        _uiModel.value =
            _uiModel.value.copy(
                groupDialProgress = index.toFloat(),
                pendingGroupId = group.id,
            )
    }

    fun onPendingGroupChange(group: GroupSelectorGroupUiModel) {
        _uiModel.value =
            _uiModel.value.copy(
                pendingGroupId = group.id,
                groupDialProgress = groupOptions.indexOfFirst { it.id == group.id }.coerceAtLeast(0).toFloat(),
            )
    }

    fun onGroupSelectionComplete(group: GroupSelectorGroupUiModel) {
        val label = if (group.id == NO_GROUP_ID) "없음" else group.name
        _uiModel.value =
            _uiModel.value.copy(
                isGroupSelectorVisible = false,
                selectedGroupId = group.id,
                pendingGroupId = group.id,
                groupLabel = label,
                groupDialProgress = groupOptions.indexOfFirst { it.id == group.id }.coerceAtLeast(0).toFloat(),
            )
    }

    private fun moveToLocationSelection(currentLocation: CurrentLocation?) {
        val origin =
            currentLocation?.let {
                GeoCoordinate(latitude = it.latitude, longitude = it.longitude)
            }
        _uiModel.value =
            _uiModel.value.copy(
                step = RecordFlowStepUiModel.LocationSelection,
                isGroupSelectorVisible = false,
                origin = origin,
                selectedCoordinate = origin,
                isSelectedCoordinateInRange = origin != null,
                locationMessage = if (origin == null) "현재 위치를 확인하고 있어요" else null,
                submissionMessage = null,
            )
    }

    private companion object {
        const val MAX_MEMO_LENGTH = 50
        const val PERSONAL_GROUP_ID = "personal"
        const val NO_GROUP_ID = "none"
    }
}

data class RecordNoticeUiModel(
    val message: String,
    val isError: Boolean,
)
