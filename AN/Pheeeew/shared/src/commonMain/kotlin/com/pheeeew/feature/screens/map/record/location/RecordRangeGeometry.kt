package com.pheeeew.feature.screens.map.record.location

import com.pheeeew.domain.model.GeoCoordinate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.PI

/** Geographic geometry shared by both native renderers; camera movement never changes it. */
fun recordRangeGeoJson(origin: GeoCoordinate?): String {
    val features =
        if (origin == null) {
            emptyList()
        } else {
            val ring =
                List(128) { index ->
                    val point = destination(origin, RECORD_RADIUS_METERS, -2 * PI * index / 128)
                    // Keep the ring continuous when the range crosses the date line.
                    val delta = (point.longitude - origin.longitude + 540.0) % 360.0 - 180.0
                    listOf(origin.longitude + delta, point.latitude)
                }.let { it + listOf(it.first()) }
            val west = origin.longitude - 180.0
            val east = origin.longitude + 180.0
            val world =
                listOf(
                    listOf(west, -85.051129),
                    listOf(east, -85.051129),
                    listOf(east, 85.051129),
                    listOf(west, 85.051129),
                    listOf(west, -85.051129),
                )
            listOf(
                rangePolygonFeature("dim", listOf(world, ring.reversed())),
                rangePolygonFeature("range", listOf(ring)),
            )
        }
    return JsonObject(
        mapOf("type" to JsonPrimitive("FeatureCollection"), "features" to JsonArray(features)),
    ).toString()
}

private fun rangePolygonFeature(
    kind: String,
    rings: List<List<List<Double>>>,
): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("Feature"),
            "properties" to JsonObject(mapOf("kind" to JsonPrimitive(kind))),
            "geometry" to
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("Polygon"),
                        "coordinates" to
                            JsonArray(
                                rings.map { ring ->
                                    JsonArray(ring.map { point -> JsonArray(point.map(::JsonPrimitive)) })
                                },
                            ),
                    ),
                ),
        ),
    )
