package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.feature.screens.map.record.location.RECORD_RADIUS_METERS
import com.pheeeew.feature.screens.map.record.location.distance
import com.pheeeew.feature.screens.map.record.location.recordRangeGeoJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordRangeGeometryTest {
    @Test
    fun rangeStays500MetersFromOriginAndDimHasMatchingHole() {
        for (origin in listOf(GeoCoordinate(37.5665, 126.9780), GeoCoordinate(-33.86, 151.21))) {
            val features = features(origin)
            val circle =
                features[1]
                    .jsonObject
                    .getValue(
                        "geometry",
                    ).jsonObject
                    .getValue("coordinates")
                    .jsonArray[0]
                    .jsonArray
            val mask =
                features[0]
                    .jsonObject
                    .getValue("geometry")
                    .jsonObject
                    .getValue("coordinates")
                    .jsonArray
            assertEquals(circle.first(), circle.last())
            assertEquals(circle.toList().reversed(), mask[1].jsonArray.toList())
            circle.forEach { point ->
                val coordinate = point.jsonArray
                val location = GeoCoordinate(coordinate[1].jsonPrimitive.double, coordinate[0].jsonPrimitive.double)
                assertTrue(abs(distance(origin, location) - RECORD_RADIUS_METERS) < 0.001)
            }
        }
    }

    @Test
    fun dateLineCrossingDoesNotStretchCircleAcrossTheWorld() {
        for (longitude in listOf(179.9999, -179.9999)) {
            val origin = GeoCoordinate(0.0, longitude)
            val circle =
                features(
                    origin,
                )[1].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray[0].jsonArray
            circle.zipWithNext().forEach { (first, second) ->
                assertTrue(
                    abs(first.jsonArray[0].jsonPrimitive.double - second.jsonArray[0].jsonPrimitive.double) < 0.01,
                )
            }
        }
    }

    @Test
    fun leavingLocationSelectionClearsAllRangeFeatures() {
        assertTrue(features(null).isEmpty())
    }

    private fun features(origin: GeoCoordinate?): JsonArray =
        Json
            .parseToJsonElement(recordRangeGeoJson(origin))
            .jsonObject
            .getValue("features")
            .jsonArray
}
