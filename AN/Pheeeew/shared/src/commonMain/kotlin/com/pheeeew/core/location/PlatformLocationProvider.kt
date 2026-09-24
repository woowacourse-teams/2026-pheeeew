package com.pheeeew.core.location

import com.pheeeew.domain.model.CurrentLocation

interface PlatformLocationProvider {
    suspend fun getCurrentLocation(): CurrentLocation?
}
