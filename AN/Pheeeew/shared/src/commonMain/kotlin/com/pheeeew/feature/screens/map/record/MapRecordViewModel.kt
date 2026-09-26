package com.pheeeew.feature.screens.map.record

import androidx.lifecycle.ViewModel
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordRegistrationUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class MapRecordViewModel(
    private val isWithinRecordRadius: IsWithinEmotionRecordRadiusUseCase,
) : ViewModel() {
    private val _uiModel = MutableStateFlow(RecordBottomSheetUiModel())
    val uiModel: StateFlow<RecordBottomSheetUiModel> = _uiModel.asStateFlow()

    val groupOptions =
        listOf(
            GroupSelectorGroupUiModel(id = PERSONAL_GROUP_ID, name = "개인", stampLabel = "개인"),
            GroupSelectorGroupUiModel(id = NO_GROUP_ID, name = "그룹 없음", stampLabel = "없음"),
        )

    fun open(emotion: EmotionTypeUiModel) {
        _uiModel.value =
            RecordBottomSheetUiModel(
                step = RecordFlowStepUiModel.Input,
                selectedEmotion = emotion,
            )
    }

    fun dismiss() {
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
        if (_uiModel.value.step != RecordFlowStepUiModel.LocationSelection || origin == null ||
            _uiModel.value.confirmedRecord != null
        ) {
            return
        }
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return
        }
        val candidate = constrainToRecordRadius(origin, GeoCoordinate(latitude, longitude))
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
        if (state.step != RecordFlowStepUiModel.LocationSelection || !state.isSelectedCoordinateInRange ||
            state.confirmedRecord != null
        ) {
            return
        }
        val emotion = state.selectedEmotion ?: return
        val coordinate = state.selectedCoordinate ?: return
        val origin = state.origin ?: return
        if (!isWithinRecordRadius(origin, coordinate)) return
        _uiModel.value =
            state.copy(
                confirmedRecord =
                    RecordRegistrationUiModel(
                        emotion = emotion,
                        coordinate = coordinate,
                        groupId = state.selectedGroupId,
                        inputMode = state.inputMode,
                        memo = state.memo.takeIf { state.inputMode == RecordInputModeUiModel.Memo && it.isNotBlank() },
                        recordingFilePath =
                            state.recordingFilePath.takeIf {
                                state.inputMode ==
                                    RecordInputModeUiModel.Recording
                            },
                    ),
                submissionMessage = "선택한 위치로 등록 정보를 확정했어요",
            )
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
                confirmedRecord = null,
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
