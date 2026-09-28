package com.pheeeew.feature.screens.map.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.domain.repository.group.LastRecordedGroupRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.EmotionPinUiModel
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.map.monitoring.RecordFunnelMonitoring
import com.pheeeew.feature.screens.map.monitoring.RecordResultReceipt
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.group.toSelectorUiModel
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MapRecordViewModel(
    private val isWithinRecordRadius: IsWithinEmotionRecordRadiusUseCase,
    private val registrationRepository: EmotionRegistrationRepository,
    private val groupStampListRepository: GroupStampListRepository,
    private val lastRecordedGroupRepository: LastRecordedGroupRepository,
    monitoring: Monitoring = NoOpMonitoring,
) : ViewModel() {
    val funnel = RecordFunnelMonitoring(monitoring)

    private val _uiModel = MutableStateFlow(RecordBottomSheetUiModel())
    val uiModel: StateFlow<RecordBottomSheetUiModel> = _uiModel.asStateFlow()

    private val registrationEvents = Channel<EmotionPinUiModel>(Channel.BUFFERED)
    val registeredEmotions = registrationEvents.receiveAsFlow()

    private var pendingRegistration: EmotionRegistration? = null
    private val _notice = MutableStateFlow<RecordNoticeUiModel?>(null)
    val notice = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    private val _groupOptions = MutableStateFlow(listOf(GroupSelectorGroupUiModel(NO_GROUP_ID, "없음", null)))
    val groupOptions: StateFlow<List<GroupSelectorGroupUiModel>> = _groupOptions.asStateFlow()
    private var groupLoadJob: Job? = null

    fun open(
        emotion: EmotionTypeUiModel,
        selectorId: String? = null,
    ) {
        funnel.start(selectorId)
        groupLoadJob?.cancel()
        pendingRegistration = null
        _uiModel.value =
            RecordBottomSheetUiModel(
                step = RecordFlowStepUiModel.Input,
                selectedEmotion = emotion,
                isGroupSelectionLoading = true,
            )
        loadMyGroups(restoreLastGroup = true)
    }

    fun dismiss() {
        if (_uiModel.value.isSubmitting) return
        groupLoadJob?.cancel()
        funnel.recordEvent(
            "emotion_record_closed",
            mapOf(
                "step" to
                    if (_uiModel.value.isGroupSelectorVisible) {
                        "group"
                    } else if (_uiModel.value.step ==
                        RecordFlowStepUiModel.Input
                    ) {
                        "input"
                    } else {
                        "location"
                    },
            ),
        )
        funnel.clearFlow()
        pendingRegistration = null
        _uiModel.value = RecordBottomSheetUiModel()
    }

    fun onInputModeChange(inputMode: RecordInputModeUiModel) {
        _uiModel.value = _uiModel.value.copy(inputMode = inputMode)
    }

    fun onMemoChange(memo: String) {
        if (memo.isNotBlank()) funnel.inputStarted("text")
        _uiModel.value = _uiModel.value.copy(memo = memo.take(MAX_MEMO_LENGTH))
    }

    fun onNext(
        currentLocation: CurrentLocation?,
        recordingFilePath: String?,
    ) {
        if (_uiModel.value.isGroupSelectionLoading) return
        if (_uiModel.value.step != RecordFlowStepUiModel.Input) return
        funnel.inputFinished(
            if (_uiModel.value.inputMode == RecordInputModeUiModel.Recording) {
                if (recordingFilePath != null) "voice" else "none"
            } else if (_uiModel.value.memo.isNotBlank()) {
                "text"
            } else {
                "none"
            },
            skipped = false,
        )
        _uiModel.value = _uiModel.value.copy(recordingFilePath = recordingFilePath)
        moveToLocationSelection(currentLocation)
    }

    fun onSkip(currentLocation: CurrentLocation?) {
        if (_uiModel.value.isGroupSelectionLoading) return
        if (_uiModel.value.step != RecordFlowStepUiModel.Input) return
        funnel.inputFinished("none", skipped = true)
        _uiModel.value = _uiModel.value.copy(memo = "", recordingFilePath = null)
        moveToLocationSelection(currentLocation)
    }

    fun onBackToInput() {
        if (_uiModel.value.isSubmitting) return
        pendingRegistration = null
        _uiModel.value =
            _uiModel.value.copy(
                step = RecordFlowStepUiModel.Input,
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
        if (candidate != _uiModel.value.selectedCoordinate) {
            pendingRegistration = null
            funnel.recordEvent("emotion_record_location_changed")
        }
        val isInRange = isWithinRecordRadius(origin, candidate)
        _uiModel.value =
            _uiModel.value.copy(
                selectedCoordinate = candidate,
                isSelectedCoordinateInRange = isInRange,
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
        funnel.recordEvent("emotion_record_location_confirmed")
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
                groupId = state.selectedGroupId.takeUnless { it == NO_GROUP_ID },
                content = content,
            ).also { pendingRegistration = it }
        val submission =
            funnel.submit(
                registration.requestId,
                when (registration.content) {
                    is EmotionRegistrationContent.Memo -> "text"
                    is EmotionRegistrationContent.Audio -> "voice"
                    EmotionRegistrationContent.None -> "none"
                },
                registration.groupId != null,
            )
        _notice.value = null
        _uiModel.value = state.copy(isSubmitting = true)
        viewModelScope.launch {
            val result =
                try {
                    registrationRepository.register(registration) { observation ->
                        submission?.audioFinished(observation)
                    }
                } catch (cancelled: CancellationException) {
                    submission?.cancelled()
                    throw cancelled
                } catch (_: Exception) {
                    EmotionRegistrationResult.Unavailable
                }
            // Record the API outcome before optional local persistence or UI work.
            val receipt = submission?.finish(result)
            when (result) {
                is EmotionRegistrationResult.Success -> {
                    try {
                        lastRecordedGroupRepository.writeGroupId(registration.groupId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Registration succeeded even if remembering its group failed.
                    }
                    registrationEvents.send(
                        EmotionPinUiModel(
                            id = result.id,
                            latitude = registration.coordinate.latitude,
                            longitude = registration.coordinate.longitude,
                            createdAt = Clock.System.now().toString(),
                            rotationDegrees = registration.rotationDegrees,
                            emotion = emotion,
                            stamp = _groupOptions.value.firstOrNull { it.id == state.selectedGroupId }?.stamp,
                        ),
                    )
                    funnel.clearFlow()
                    registrationEvents.send(
                        EmotionPinUiModel(
                            id = result.id,
                            latitude = registration.coordinate.latitude,
                            longitude = registration.coordinate.longitude,
                            createdAt = Clock.System.now().toString(),
                            rotationDegrees = registration.rotationDegrees,
                            emotion = emotion,
                            stamp = _groupOptions.value.firstOrNull { it.id == state.selectedGroupId }?.stamp,
                        ),
                    )
                    funnel.clearFlow()
                    _uiModel.value = RecordBottomSheetUiModel()
                    pendingRegistration = null
                    _notice.value = RecordNoticeUiModel("선택한 위치에 감정을 남겼어요", false, receipt)
                }

                else -> {
                    _uiModel.value = _uiModel.value.copy(isSubmitting = false)
                    _notice.value =
                        RecordNoticeUiModel(
                            when (result) {
                                EmotionRegistrationResult.AudioUnavailable -> {
                                    "녹음 파일을 확인할 수 없어요. 다시 녹음해 주세요"
                                }

                                EmotionRegistrationResult.AudioUploadFailed -> {
                                    "녹음을 업로드하지 못했어요. 다시 시도해 주세요"
                                }

                                else -> {
                                    "감정을 등록하지 못했어요. 다시 시도해 주세요"
                                }
                            },
                            true,
                            receipt,
                        )
                }
            }
        }
    }

    fun onGroupSelectorOpen() {
        if (_uiModel.value.isGroupSelectionLoading) return
        _uiModel.value =
            _uiModel.value.copy(
                isGroupSelectionLoading = true,
                isGroupSelectorVisible = false,
                pendingGroupId = _uiModel.value.selectedGroupId,
                groupDialProgress =
                    _groupOptions.value
                        .indexOfFirst { it.id == _uiModel.value.selectedGroupId }
                        .coerceAtLeast(0)
                        .toFloat(),
            )
        loadMyGroups(restoreLastGroup = false)
    }

    fun onGroupSelectorDismiss() {
        _uiModel.value =
            _uiModel.value.copy(
                isGroupSelectorVisible = false,
                pendingGroupId = _uiModel.value.selectedGroupId,
                groupDialProgress =
                    _groupOptions.value
                        .indexOfFirst { it.id == _uiModel.value.selectedGroupId }
                        .coerceAtLeast(0)
                        .toFloat(),
            )
    }

    fun onGroupDialProgressChange(progress: Float) {
        _uiModel.value = _uiModel.value.copy(groupDialProgress = progress)
    }

    fun onGroupDialProgressSettle(progress: Float) {
        val index = progress.roundToInt().coerceIn(_groupOptions.value.indices)
        val group = _groupOptions.value[index]
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
                groupDialProgress =
                    _groupOptions.value
                        .indexOfFirst { it.id == group.id }
                        .coerceAtLeast(0)
                        .toFloat(),
            )
    }

    fun onGroupSelectionComplete(group: GroupSelectorGroupUiModel) {
        if (_uiModel.value.selectedGroupId != group.id) {
            funnel.recordEvent(
                "emotion_record_group_changed",
                mapOf("group_selection" to if (group.id == NO_GROUP_ID) "none" else "group"),
            )
        }
        _uiModel.value =
            _uiModel.value.copy(
                isGroupSelectorVisible = false,
                selectedGroupId = group.id,
                pendingGroupId = group.id,
                groupDialProgress =
                    _groupOptions.value
                        .indexOfFirst { it.id == group.id }
                        .coerceAtLeast(0)
                        .toFloat(),
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
            )
    }

    private fun loadMyGroups(restoreLastGroup: Boolean) {
        groupLoadJob?.cancel()
        val observation = funnel.observe("emotion_record_group_options_finished")
        groupLoadJob =
            viewModelScope.launch {
                val storedId =
                    if (restoreLastGroup) {
                        try {
                            lastRecordedGroupRepository.readGroupId()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                val result =
                    try {
                        observation.observe(::resultLabel) { groupStampListRepository.findMyStamps() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        GroupStampListLoadResult.Unavailable
                    }
                when (result) {
                    is GroupStampListLoadResult.Loaded -> {
                        val options =
                            listOf(GroupSelectorGroupUiModel(NO_GROUP_ID, "없음", null)) +
                                result.groups.map { it.toSelectorUiModel() }
                        _groupOptions.value = options
                        val current = _uiModel.value
                        val selectedId =
                            (if (restoreLastGroup) storedId else current.selectedGroupId)
                                ?.takeIf { id -> options.any { it.id == id } } ?: NO_GROUP_ID
                        if (restoreLastGroup && storedId != null && selectedId == NO_GROUP_ID) {
                            clearStoredGroup()
                        } else if (!restoreLastGroup && current.selectedGroupId != NO_GROUP_ID &&
                            selectedId == NO_GROUP_ID
                        ) {
                            clearStoredGroup()
                        }
                        val pendingId =
                            current.pendingGroupId.takeIf { id -> options.any { it.id == id } } ?: selectedId
                        val dialId = if (restoreLastGroup) selectedId else pendingId
                        _uiModel.value =
                            current.copy(
                                selectedGroupId = selectedId,
                                pendingGroupId = dialId,
                                isGroupSelectionLoading = false,
                                isGroupSelectorVisible =
                                    !restoreLastGroup && current.step == RecordFlowStepUiModel.Input,
                                groupDialProgress = options.indexOfFirst { it.id == dialId }.toFloat(),
                            )
                    }

                    GroupStampListLoadResult.Unavailable -> {
                        _uiModel.value =
                            _uiModel.value.copy(isGroupSelectorVisible = false, isGroupSelectionLoading = false)
                        _notice.value = RecordNoticeUiModel("그룹 목록을 불러오지 못했어요. 다시 시도해 주세요", true)
                    }
                }
            }
    }

    private suspend fun clearStoredGroup() {
        try {
            lastRecordedGroupRepository.writeGroupId(null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the safe no-group selection even if local cleanup fails.
        }
    }

    private companion object {
        const val MAX_MEMO_LENGTH = 50
        const val NO_GROUP_ID = "none"
    }
}

data class RecordNoticeUiModel(
    val message: String,
    val isError: Boolean,
    val receipt: RecordResultReceipt? = null,
)
