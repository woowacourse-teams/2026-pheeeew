package com.pheeeew.legacy.feature.map

import com.pheeeew.legacy.domain.model.location.LocationState
import com.pheeeew.legacy.domain.model.sigh.CreateSighCommand
import com.pheeeew.legacy.domain.model.sigh.Sigh
import com.pheeeew.legacy.domain.model.sigh.SighPin
import com.pheeeew.legacy.feature.map.map.MapCameraCommand

data class MapUiState(
    val sighs: List<SighPin> = emptyList(),
    val location: MapLocationUiState = MapLocationUiState(),
    val viewport: MapViewportUiState = MapViewportUiState(),
    val sighRelease: SighReleaseState = SighReleaseState.Idle,
    val sighBrowser: SighBrowserUiState = SighBrowserUiState(),
    val errors: MapErrorUiState = MapErrorUiState(),
)

data class SighBrowserUiState(
    val isVisible: Boolean = false,
    val isListVisible: Boolean = false,
    val items: List<Sigh> = emptyList(),
    val selectedSigh: Sigh? = null,
    val pendingSighPin: SighPin? = null,
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadMoreError: Boolean = false,
    val refreshRevision: Long = 0L,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
)

data class MapLocationUiState(
    val state: LocationState = LocationState.Loading,
    val isRequesting: Boolean = false,
)

data class MapViewportUiState(
    val cameraCommand: MapCameraCommand? = null,
    val focusRequest: MapFocusRequest? = null,
)

data class MapErrorUiState(
    val refreshMessage: String? = null,
    val renderMessage: String? = null,
)

sealed interface SighReleaseState {
    data object Idle : SighReleaseState

    data class EditingMemo(
        val draft: PendingSighDraft,
    ) : SighReleaseState

    data class AwaitingBreath(
        val command: CreateSighCommand,
    ) : SighReleaseState

    data class Submitting(
        val command: CreateSighCommand,
    ) : SighReleaseState

    data class Error(
        val message: String,
        val canRetry: Boolean,
        val command: CreateSighCommand? = null,
    ) : SighReleaseState
}
