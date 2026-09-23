package com.pheeeew.legacy.domain.repository

import com.pheeeew.legacy.domain.model.location.LocationState
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    val locationState: StateFlow<LocationState>

    suspend fun refreshCurrentLocation()
}
