@file:Suppress("NonAsciiCharacters")

package com.pheeeew.core.geo

import com.pheeeew.domain.model.geo.Coordinate
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeodesicSighLocationObfuscatorTest {
    @Test
    fun `구면 면적에 균등한 거리와 방위각을 사용한다`() {
        val original = Coordinate(latitude = 37.5665, longitude = 126.9780)
        val obfuscator = GeodesicSighLocationObfuscator(samples(0.25, 0.0))

        val result = obfuscator.obfuscate(original)

        assertEquals(150.0, distanceMeters(original, result), 0.01)
        assertTrue(result.latitude > original.latitude)
        assertEquals(original.longitude, result.longitude, 0.000_001)
    }

    @Test
    fun `생성 좌표는 전 세계 어느 위치에서도 지정 반경 안에 있다`() {
        val coordinates =
            listOf(
                Coordinate(latitude = 37.5665, longitude = 126.9780),
                Coordinate(latitude = 37.4220, longitude = -122.0840),
                Coordinate(latitude = 0.0, longitude = 179.9990),
                Coordinate(latitude = 89.9990, longitude = 45.0),
                Coordinate(latitude = -89.9990, longitude = -45.0),
            )

        coordinates.forEach { original ->
            val result =
                GeodesicSighLocationObfuscator(samples(0.999999, 0.75))
                    .obfuscate(original, radiusMeters = 300.0)

            assertTrue(result.latitude in -90.0..90.0)
            assertTrue(result.longitude in -180.0..180.0)
            assertTrue(distanceMeters(original, result) < 300.0)
            assertTrue(distanceMeters(original, result) > 299.0)
        }
    }

    @Test
    fun `날짜변경선을 넘으면 경도를 정규화한다`() {
        val original = Coordinate(latitude = 0.0, longitude = 179.9990)
        val result =
            GeodesicSighLocationObfuscator(samples(0.999999, 0.25))
                .obfuscate(original, radiusMeters = 300.0)

        assertTrue(result.longitude < -179.0)
        assertEquals(300.0, distanceMeters(original, result), 0.01)
    }

    @Test
    fun `유효하지 않은 좌표와 난수는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            GeodesicSighLocationObfuscator(samples(0.0, 0.0)).obfuscate(
                Coordinate(latitude = 91.0, longitude = 127.0),
                radiusMeters = 300.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GeodesicSighLocationObfuscator(samples(1.0)).obfuscate(
                Coordinate(latitude = 37.5665, longitude = 126.9780),
                radiusMeters = 300.0,
            )
        }
    }

    @Test
    fun `반경이 0이면 원본 좌표를 반환한다`() {
        val original = Coordinate(latitude = 37.5665, longitude = 126.9780)
        val obfuscator = GeodesicSighLocationObfuscator { error("난수를 사용하면 안 됩니다.") }

        assertEquals(original, obfuscator.obfuscate(original, radiusMeters = 0.0))
        assertFailsWith<IllegalArgumentException> {
            obfuscator.obfuscate(original, radiusMeters = -1.0)
        }
    }

    private fun distanceMeters(
        start: Coordinate,
        end: Coordinate,
    ): Double {
        val startLatitude = start.latitude.toRadians()
        val endLatitude = end.latitude.toRadians()
        val latitudeDelta = (end.latitude - start.latitude).toRadians()
        val longitudeDelta = (end.longitude - start.longitude).toRadians()
        val haversine =
            sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
                cos(startLatitude) * cos(endLatitude) *
                sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private fun samples(vararg values: Double): () -> Double {
        val iterator = values.iterator()
        return { iterator.next() }
    }

    private companion object {
        const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8
    }
}
