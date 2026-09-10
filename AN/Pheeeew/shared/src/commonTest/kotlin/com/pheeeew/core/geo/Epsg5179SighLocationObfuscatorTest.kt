@file:Suppress("NonAsciiCharacters")

package com.pheeeew.core.geo

import com.pheeeew.domain.model.geo.Coordinate
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Epsg5179SighLocationObfuscatorTest {
    @Test
    fun `반지름 난수의 제곱근을 사용해 원 내부에서 면적 균등 추첨한다`() {
        val original = Coordinate(latitude = 37.5665, longitude = 126.9780)
        val obfuscator = Epsg5179SighLocationObfuscator(samples(0.25, 0.0))

        val result = obfuscator.obfuscate(original)

        val originalProjected = Epsg5179Projection.forward(original)
        val resultProjected = Epsg5179Projection.forward(result)
        val distance =
            hypot(
                resultProjected.easting - originalProjected.easting,
                resultProjected.northing - originalProjected.northing,
            )
        assertEquals(150.0, distance, 0.01)
        assertTrue(resultProjected.easting > originalProjected.easting)
        assertEquals(originalProjected.northing, resultProjected.northing, 0.01)
    }

    @Test
    fun `생성 좌표는 원본에서 300m 미만에 있다`() {
        val original = Coordinate(latitude = 37.5665, longitude = 126.9780)
        val obfuscator = Epsg5179SighLocationObfuscator(samples(0.999999, 0.75))

        val result = obfuscator.obfuscate(original, radiusMeters = 300.0)

        val originalProjected = Epsg5179Projection.forward(original)
        val resultProjected = Epsg5179Projection.forward(result)
        val distance =
            hypot(
                resultProjected.easting - originalProjected.easting,
                resultProjected.northing - originalProjected.northing,
            )
        assertTrue(distance < 300.0)
        assertTrue(distance > 299.0)
    }

    @Test
    fun `유효하지 않은 좌표와 난수는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            Epsg5179SighLocationObfuscator(samples(0.0, 0.0)).obfuscate(
                Coordinate(latitude = 91.0, longitude = 127.0),
                radiusMeters = 300.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Epsg5179SighLocationObfuscator(samples(1.0)).obfuscate(
                Coordinate(latitude = 37.5665, longitude = 126.9780),
                radiusMeters = 300.0,
            )
        }
    }

    @Test
    fun `반경은 호출할 때 지정하며 0이면 원본 좌표를 반환한다`() {
        val original = Coordinate(latitude = 37.5665, longitude = 126.9780)
        val obfuscator = Epsg5179SighLocationObfuscator { error("난수를 사용하면 안 됩니다.") }

        assertEquals(original, obfuscator.obfuscate(original, radiusMeters = 0.0))
        assertFailsWith<IllegalArgumentException> {
            obfuscator.obfuscate(original, radiusMeters = -1.0)
        }
    }

    private fun samples(vararg values: Double): () -> Double {
        val iterator = values.iterator()
        return { iterator.next() }
    }
}
