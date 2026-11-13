package com.pheeeew.domain.model

sealed interface LocationState {
    data object Loading : LocationState

    data class Available(
        val location: CurrentLocation,
    ) : LocationState

    data class Unavailable(
        val reason: LocationError,
    ) : LocationState
}
