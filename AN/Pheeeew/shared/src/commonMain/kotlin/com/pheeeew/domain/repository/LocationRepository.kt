package com.pheeeew.domain.repository

import com.pheeeew.domain.model.LocationState
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    val state: StateFlow<LocationState>

    suspend fun refresh()
}
