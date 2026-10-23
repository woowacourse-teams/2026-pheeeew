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
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.map.EmotionPinUiModel
import com.pheeeew.feature.screens.map.monitoring.RecordFunnelMonitoring
import com.pheeeew.feature.screens.map.monitoring.RecordResultReceipt
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Inject
class MapRecordViewModel(
    private val isWithinRecordRadius: IsWithinEmotionRecordRadiusUseCase,
    private val registrationRepository: EmotionRegistrationRepository,
    monitoring: Monitoring = NoOpMonitoring,
) : ViewModel() {
    val funnel = RecordFunnelMonitoring(monitoring)

    private val _uiModel = MutableStateFlow(RecordBottomSheetUiModel())
    val uiModel: StateFlow<RecordBottomSheetUiModel> = _uiModel.asStateFlow()

    private val registrationEvents = Channel<EmotionPinUiModel>(Channel.BUFFERED)
    val registeredEmotions = registrationEvents.receiveAsFlow()

    private var pendingRegistration: EmotionRegistration? = null
    private var nextNoticeId = 0L
    private val _notice = MutableStateFlow<RecordNoticeUiModel?>(null)
    val notice = _notice.asStateFlow()
    private val _registeredEmotion = MutableStateFlow<RegisteredEmotionUiModel?>(null)
    val registeredEmotion = _registeredEmotion.asStateFlow()

    fun consumeRegisteredEmotion(): RegisteredEmotionUiModel? =
        _registeredEmotion.value.also { _registeredEmotion.value = null }

    fun dismissNotice(id: Long? = null) {
        if (id == null || _notice.value?.id == id) _notice.value = null
    }

    private fun newNotice(
        message: String,
        isError: Boolean,
        receipt: RecordResultReceipt? = null,
        suppressWhenOffline: Boolean = false,
    ) = RecordNoticeUiModel(
        message = message,
        isError = isError,
        receipt = receipt,
        suppressWhenOffline = suppressWhenOffline,
        id = ++nextNoticeId,
    )

    fun open(
        emotion: EmotionTypeUiModel,
        selectorId: String? = null,
    ) {
        funnel.start(selectorId)
        pendingRegistration = null
        _uiModel.value =
            RecordBottomSheetUiModel(
                step = RecordFlowStepUiModel.Input,
                selectedEmotion = emotion,
            )
    }

    fun dismiss() {
        if (_uiModel.value.isSubmitting) return
        funnel.recordEvent(
            "emotion_record_closed",
            mapOf(
                "step" to
                    if (_uiModel.value.step == RecordFlowStepUiModel.Input) {
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

    fun onAnonymousChange(isAnonymous: Boolean) {
        if (_uiModel.value.step != RecordFlowStepUiModel.Input || _uiModel.value.isSubmitting) return
        _uiModel.value = _uiModel.value.copy(isAnonymous = isAnonymous)
    }

    fun onMemoChange(memo: String) {
        if (memo.isNotBlank()) funnel.inputStarted("text")
        _uiModel.value = _uiModel.value.copy(memo = memo.take(MAX_MEMO_LENGTH))
    }

    fun onNext(
        currentLocation: CurrentLocation?,
        recordingFilePath: String?,
    ) {
        val state = _uiModel.value
        if (state.step != RecordFlowStepUiModel.Input) return
        val hasContent =
            when (state.inputMode) {
                RecordInputModeUiModel.Memo -> state.memo.isNotBlank()
                RecordInputModeUiModel.Recording -> !recordingFilePath.isNullOrBlank()
            }
        if (!hasContent) return
        funnel.inputFinished(if (state.inputMode == RecordInputModeUiModel.Recording) "voice" else "text")
        _uiModel.value = state.copy(recordingFilePath = recordingFilePath)
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
                hasMovedStamp = true,
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
        if (content == EmotionRegistrationContent.None) return
        val registration =
            pendingRegistration ?: EmotionRegistration(
                requestId = Uuid.random().toString(),
                state = EmotionState.valueOf(emotion.name),
                coordinate = coordinate,
                rotationDegrees = 0.0,
                content = content,
                anonymous = state.isAnonymous,
            ).also { pendingRegistration = it }
        val submission =
            funnel.submit(
                registration.requestId,
                when (registration.content) {
                    is EmotionRegistrationContent.Memo -> "text"
                    is EmotionRegistrationContent.Audio -> "voice"
                    EmotionRegistrationContent.None -> "none"
                },
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
                    registrationEvents.send(
                        EmotionPinUiModel(
                            id = result.id,
                            latitude = registration.coordinate.latitude,
                            longitude = registration.coordinate.longitude,
                            createdAt = Clock.System.now().toString(),
                            rotationDegrees = registration.rotationDegrees,
                            emotion = emotion,
                        ),
                    )
                    funnel.clearFlow()
                    _uiModel.value = RecordBottomSheetUiModel()
                    pendingRegistration = null
                    _registeredEmotion.value = RegisteredEmotionUiModel(result.id, registration.coordinate)
                    _notice.value = newNotice("선택한 위치에 감정을 남겼어요", false, receipt)
                }

                else -> {
                    _uiModel.value = _uiModel.value.copy(isSubmitting = false)

                    _notice.value =
                        newNotice(
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
                            isError = true,
                            receipt = receipt,
                        )
                }
            }
        }
    }

    private fun moveToLocationSelection(currentLocation: CurrentLocation?) {
        val origin =
            currentLocation?.let {
                GeoCoordinate(latitude = it.latitude, longitude = it.longitude)
            }
        _uiModel.value =
            _uiModel.value.copy(
                step = RecordFlowStepUiModel.LocationSelection,
                hasMovedStamp = false,
                origin = origin,
                selectedCoordinate = origin,
                isSelectedCoordinateInRange = origin != null,
            )
    }

    private companion object {
        const val MAX_MEMO_LENGTH = 50
    }
}

data class RecordNoticeUiModel(
    val message: String,
    val isError: Boolean,
    val receipt: RecordResultReceipt? = null,
    val suppressWhenOffline: Boolean = false,
    val id: Long = 0L,
)
