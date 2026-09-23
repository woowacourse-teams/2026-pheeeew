package com.pheeeew.legacy.core.location

import com.pheeeew.legacy.domain.model.location.CurrentLocation

/** OS 위치 API에서 위치를 가져오는 플랫폼 계약입니다. */
interface PlatformLocationProvider {
    suspend fun getCurrentLocation(): PlatformLocationResult
}

/** 플랫폼 위치 API가 공통 계층에 전달하는 결과입니다. */
sealed interface PlatformLocationResult {
    data class Success(
        val location: CurrentLocation,
    ) : PlatformLocationResult

    data object GpsUnavailable : PlatformLocationResult
}
