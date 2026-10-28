package com.pheeeew.developertools.simulation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull

enum class ErrorSimulationScenario(
    val title: String,
) {
    MapRenderer("지도 스타일 로드 실패"),
    MapPins("감정 핀 조회 실패"),
    MapPinsNextPage("감정 핀 추가 페이지 실패"),
    RegionClusters("지역 감정 조회 실패"),
    EmotionDetailUnavailable("감정 상세 조회 실패"),
    EmotionDetailNotFound("감정 상세 접근 불가"),
    EmotionRegistrationUnavailable("감정 등록 실패"),
    AudioUnavailable("녹음 파일 확인 실패"),
    AudioUploadFailed("녹음 업로드 실패"),
    EmotionDeleteUnavailable("감정 삭제 실패"),
    EmotionBlockUnavailable("감정 차단 실패"),
    EmotionReportUnavailable("감정 신고 실패"),
}

enum class ErrorSimulationMode {
    NEXT,
    ALWAYS,
}

data class ErrorSimulationState(
    val offline: Boolean = false,
    val networkDelayMillis: Long = 0L,
    val networkNeverResponds: Boolean = false,
    val networkRevision: Long = 0L,
    val location: LocationSimulationSettings = LocationSimulationSettings(),
    val developerToolsEnabled: Boolean = false,
    val modes: Map<ErrorSimulationScenario, ErrorSimulationMode> = emptyMap(),
)

/** Shared by the Debug-only developer tools and existing feature failure paths. */
object ErrorSimulation : LocationSimulationSettingsProvider {
    private val mutableState = MutableStateFlow(ErrorSimulationState())
    val state: StateFlow<ErrorSimulationState> = mutableState.asStateFlow()

    fun enableDeveloperTools() {
        mutableState.update { it.copy(developerToolsEnabled = true) }
    }

    fun replaceSettings(settings: ErrorSimulationState) {
        mutableState.update { current ->
            val networkChanged =
                current.offline != settings.offline ||
                    current.networkDelayMillis != settings.networkDelayMillis ||
                    current.networkNeverResponds != settings.networkNeverResponds
            current.copy(
                offline = settings.offline,
                networkDelayMillis = settings.networkDelayMillis,
                networkNeverResponds = settings.networkNeverResponds,
                networkRevision = current.networkRevision + if (networkChanged) 1 else 0,
                location = settings.location,
                developerToolsEnabled = true,
                modes = settings.modes,
            )
        }
    }

    fun defaultState(): ErrorSimulationState = ErrorSimulationState(developerToolsEnabled = true)

    fun setOffline(offline: Boolean) {
        mutableState.update { current ->
            if (current.offline == offline) {
                current
            } else {
                current.copy(offline = offline, networkRevision = current.networkRevision + 1)
            }
        }
    }

    internal fun setRecoveryNetwork(offline: Boolean): Long =
        mutableState
            .updateAndGet { current ->
                current.copy(
                    developerToolsEnabled = true,
                    offline = offline,
                    networkDelayMillis = 0,
                    networkNeverResponds = false,
                    networkRevision = current.networkRevision + 1,
                )
            }.networkRevision

    fun setNetworkProfile(
        delayMillis: Long,
        neverResponds: Boolean,
    ) {
        require(delayMillis in 0L..MAX_SIMULATION_DELAY_MILLIS)
        mutableState.update { current ->
            if (current.networkDelayMillis == delayMillis && current.networkNeverResponds == neverResponds) {
                current
            } else {
                current.copy(
                    networkDelayMillis = delayMillis,
                    networkNeverResponds = neverResponds,
                    networkRevision = current.networkRevision + 1,
                )
            }
        }
    }

    fun setLocationMode(mode: LocationSimulationMode) {
        mutableState.update { current -> current.copy(location = current.location.copy(mode = mode)) }
    }

    fun setLocationProfile(
        delayMillis: Long,
        neverResponds: Boolean,
    ) {
        require(delayMillis in 0L..MAX_SIMULATION_DELAY_MILLIS)
        mutableState.update { current ->
            current.copy(location = current.location.copy(delayMillis = delayMillis, neverResponds = neverResponds))
        }
    }

    fun setFixedCoordinates(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return false
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return false
        mutableState.update { current ->
            current.copy(
                location =
                    current.location.copy(
                        mode = LocationSimulationMode.FIXED,
                        latitude = latitude,
                        longitude = longitude,
                    ),
            )
        }
        return true
    }

    override fun currentSettings(): LocationSimulationSettings {
        val current = state.value
        return current.location.takeIf { current.developerToolsEnabled } ?: LocationSimulationSettings()
    }

    suspend fun awaitAvailability(): NetworkRequestAvailability {
        while (true) {
            val current = state.value
            if (!current.developerToolsEnabled) return NetworkRequestAvailability.AVAILABLE
            if (current.offline) return NetworkRequestAvailability.OFFLINE

            if (current.networkNeverResponds) {
                state.first { it.networkRevision != current.networkRevision || !it.developerToolsEnabled }
                continue
            }

            if (current.networkDelayMillis > 0L) {
                val conditionsChanged =
                    withTimeoutOrNull(current.networkDelayMillis) {
                        state.first { it.networkRevision != current.networkRevision || !it.developerToolsEnabled }
                    } != null
                if (conditionsChanged) continue
            }

            val latest = state.value
            if (!latest.developerToolsEnabled) return NetworkRequestAvailability.AVAILABLE
            if (latest.networkRevision != current.networkRevision) continue
            if (latest.offline) return NetworkRequestAvailability.OFFLINE
            return NetworkRequestAvailability.AVAILABLE
        }
    }

    fun setMode(
        scenario: ErrorSimulationScenario,
        mode: ErrorSimulationMode?,
    ) {
        mutableState.update { current ->
            val modes = current.modes.toMutableMap()
            if (mode == null) {
                modes.remove(scenario)
            } else {
                modes[scenario] = mode
            }
            current.copy(modes = modes)
        }
    }

    fun mode(scenario: ErrorSimulationScenario): ErrorSimulationMode? = state.value.modes[scenario]

    fun shouldFail(scenario: ErrorSimulationScenario): Boolean {
        while (true) {
            val current = mutableState.value
            when (current.modes[scenario]) {
                null -> {
                    return false
                }

                ErrorSimulationMode.ALWAYS -> {
                    return true
                }

                ErrorSimulationMode.NEXT -> {
                    val modes = current.modes.toMutableMap()
                    modes.remove(scenario)
                    if (mutableState.compareAndSet(current, current.copy(modes = modes))) return true
                }
            }
        }
    }

    fun clear() {
        mutableState.update { current ->
            ErrorSimulationState(
                developerToolsEnabled = current.developerToolsEnabled,
                networkRevision = current.networkRevision + 1,
            )
        }
    }

    private const val MAX_SIMULATION_DELAY_MILLIS = 300_000L
}
