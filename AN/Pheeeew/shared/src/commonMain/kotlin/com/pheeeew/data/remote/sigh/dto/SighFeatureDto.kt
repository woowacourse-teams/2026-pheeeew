package com.pheeeew.data.remote.sigh.dto

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighPin
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class SighFeatureDto<Properties>(
    val type: String,
    val id: Long,
    val geometry: PointGeometryDto,
    val properties: Properties,
)

/** V1 지도 Feature를 하위 호환 가능한 지도 핀 projection으로 변환합니다. */
fun SighFeatureDto<SighV1PropertiesDto>.toSighPin(): SighPin {
    validateGeoJson()

    return SighPin(
        id = id,
        coordinate = geometry.toCoordinate(),
        createdAt = properties.createdAt.toInstantOrNull(),
    )
}

/** V2 Feature를 메모와 생성 시각을 포함한 한숨 모델로 변환합니다. */
fun SighFeatureDto<SighV2PropertiesDto>.toSigh(): Sigh {
    validateGeoJson()

    return Sigh(
        id = id,
        coordinate = geometry.toCoordinate(),
        memo = properties.memo,
        nickname = properties.nickname,
        createdAt = Instant.parse(properties.createdAt),
    )
}

/** 지도 projection에 필요한 GeoJSON Feature 계약을 검증합니다. */
private fun SighFeatureDto<*>.validateGeoJson() {
    require(type == "Feature") { "GeoJSON Feature 타입이 올바르지 않습니다." }
    require(geometry.type == "Point") { "GeoJSON Geometry 타입이 올바르지 않습니다." }
    require(geometry.coordinates.size >= 2) { "GeoJSON Point 좌표가 올바르지 않습니다." }
}

/** GeoJSON의 경도·위도 순서 좌표를 앱의 위도·경도 모델로 변환합니다. */
private fun PointGeometryDto.toCoordinate(): Coordinate =
    Coordinate(
        latitude = coordinates[1],
        longitude = coordinates[0],
    )

/** 잘못된 서버 시각이 전체 지도 응답을 무효화하지 않도록 안전하게 파싱합니다. */
private fun String?.toInstantOrNull(): Instant? =
    this?.let { value ->
        runCatching { Instant.parse(value) }.getOrNull()
    }
