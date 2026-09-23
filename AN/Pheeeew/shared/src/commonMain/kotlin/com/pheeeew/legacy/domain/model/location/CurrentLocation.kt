package com.pheeeew.legacy.domain.model.location

import com.pheeeew.legacy.domain.model.geo.Coordinate

/**
 * GPS로 획득한 사용자의 현재 위치입니다.
 *
 * 위치는 앱 내부에서 항상 위도, 경도 순서로 다룬니다. GeoJSON의
 * `[longitude, latitude]` 순서는 data 계층에서 변환해야 합니다.
 *
 * @property latitude 위도
 * @property longitude 경도
 * @property accuracyMeters GPS 위치의 예상 오차 범위(미터)
 * @property capturedAtMillis 위치를 획득한 시각(epoch milliseconds)
 */
data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMillis: Long,
) {
    /** 기존 [Coordinate] 기반 호출부와의 호환을 위한 보조 생성자입니다. */
    constructor(
        coordinate: Coordinate,
        accuracyMeters: Float,
        capturedAtMillis: Long,
    ) : this(
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        accuracyMeters = accuracyMeters,
        capturedAtMillis = capturedAtMillis,
    )

    /** 기존 코드가 좌표 객체를 필요로 할 때 사용하는 파생 값입니다. */
    val coordinate: Coordinate
        get() = Coordinate(latitude = latitude, longitude = longitude)

    /** 상태 객체가 실수로 기록되어도 정확한 좌표가 노출되지 않게 합니다. */
    override fun toString(): String = "CurrentLocation([redacted])"
}
