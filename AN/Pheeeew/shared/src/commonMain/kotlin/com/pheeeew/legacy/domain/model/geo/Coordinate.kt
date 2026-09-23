package com.pheeeew.legacy.domain.model.geo

// 앱 내부 좌표는 위도(latitude), 경도(longitude) 순서로 관리한다.
// 외부 API의 [longitude, latitude] 형식은 data 계층에서 변환한다.
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)
