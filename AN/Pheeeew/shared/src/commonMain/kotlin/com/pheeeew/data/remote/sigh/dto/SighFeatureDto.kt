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

fun SighFeatureDto<SighV1PropertiesDto>.toSighPin(): SighPin {
    validateGeoJson()

    return SighPin(
        id = id,
        coordinate = geometry.toCoordinate(),
        createdAt = properties.createdAt.toInstantOrNull(),
    )
}

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

private fun SighFeatureDto<*>.validateGeoJson() {
    require(type == "Feature") { "GeoJSON Feature 타입이 올바르지 않습니다." }
    require(geometry.type == "Point") { "GeoJSON Geometry 타입이 올바르지 않습니다." }
    require(geometry.coordinates.size >= 2) { "GeoJSON Point 좌표가 올바르지 않습니다." }
}

private fun PointGeometryDto.toCoordinate(): Coordinate =
    Coordinate(
        latitude = coordinates[1],
        longitude = coordinates[0],
    )

private fun String?.toInstantOrNull(): Instant? =
    this?.let { value ->
        runCatching { Instant.parse(value) }.getOrNull()
    }
