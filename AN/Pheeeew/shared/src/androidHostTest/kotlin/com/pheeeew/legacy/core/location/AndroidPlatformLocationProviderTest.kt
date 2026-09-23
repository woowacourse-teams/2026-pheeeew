package com.pheeeew.legacy.core.location

import com.pheeeew.legacy.domain.model.location.CurrentLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidPlatformLocationProviderTest {
    @Test
    fun validNativeValuesBecomeCurrentLocation() {
        val result =
            androidPlatformLocationResult(
                latitude = 37.5505,
                longitude = 127.0373,
                accuracyMeters = 7.5f,
                capturedAtMillis = 1_000L,
            )

        val success = assertIs<PlatformLocationResult.Success>(result)
        assertEquals(37.5505, success.location.latitude)
        assertEquals(127.0373, success.location.longitude)
        assertEquals(7.5f, success.location.accuracyMeters)
        assertEquals(1_000L, success.location.capturedAtMillis)
    }

    @Test
    fun missingAccuracyIsRejected() {
        val result =
            androidPlatformLocationResult(
                latitude = 37.5505,
                longitude = 127.0373,
                accuracyMeters = null,
                capturedAtMillis = 1_000L,
            )

        assertSame(PlatformLocationResult.GpsUnavailable, result)
    }

    @Test
    fun invalidCoordinateOrTimestampIsRejected() {
        val invalidCoordinate =
            androidPlatformLocationResult(91.0, 127.0373, 7.5f, 1_000L)
        val invalidTimestamp =
            androidPlatformLocationResult(37.5505, 127.0373, 7.5f, 0L)

        assertSame(PlatformLocationResult.GpsUnavailable, invalidCoordinate)
        assertSame(PlatformLocationResult.GpsUnavailable, invalidTimestamp)
    }

    @Test
    fun recentLastKnownLocationIsAcceptedForSixtySeconds() {
        assertTrue(
            isRecentAndroidLocation(
                capturedAtMillis = 40_000L,
                nowMillis = 100_000L,
                maximumAgeMillis = 60_000L,
            ),
        )
    }

    @Test
    fun staleOrFutureLastKnownLocationIsRejected() {
        assertFalse(
            isRecentAndroidLocation(
                capturedAtMillis = 39_999L,
                nowMillis = 100_000L,
                maximumAgeMillis = 60_000L,
            ),
        )
        assertFalse(
            isRecentAndroidLocation(
                capturedAtMillis = 100_001L,
                nowMillis = 100_000L,
                maximumAgeMillis = 60_000L,
            ),
        )
    }

    @Test
    fun invalidRecentLocationDoesNotBlockValidFallback() {
        val fallbackLocation =
            com.pheeeew.legacy.domain.model.location.CurrentLocation(
                latitude = 37.5505,
                longitude = 127.0373,
                accuracyMeters = 12f,
                capturedAtMillis = 100_000L,
            )

        val result = firstValidCurrentLocation(listOf(null, fallbackLocation))

        assertSame(fallbackLocation, result)
    }
}
