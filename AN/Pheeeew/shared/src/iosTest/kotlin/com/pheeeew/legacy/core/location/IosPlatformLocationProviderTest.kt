package com.pheeeew.legacy.core.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class IosPlatformLocationProviderTest {
    @Test
    fun validCoreLocationValuesBecomeCurrentLocation() {
        val result =
            iosPlatformLocationResult(
                latitude = 37.5505,
                longitude = 127.0373,
                accuracyMeters = 6.25,
                capturedAtMillis = 1_000L,
            )

        val success = assertIs<PlatformLocationResult.Success>(result)
        assertEquals(37.5505, success.location.latitude)
        assertEquals(127.0373, success.location.longitude)
        assertEquals(6.25f, success.location.accuracyMeters)
        assertEquals(1_000L, success.location.capturedAtMillis)
    }

    @Test
    fun nonFiniteAccuracyIsRejected() {
        val result =
            iosPlatformLocationResult(
                latitude = 37.5505,
                longitude = 127.0373,
                accuracyMeters = Double.NaN,
                capturedAtMillis = 1_000L,
            )

        assertSame(PlatformLocationResult.GpsUnavailable, result)
    }

    @Test
    fun invalidCoordinateOrTimestampIsRejected() {
        val invalidCoordinate =
            iosPlatformLocationResult(37.5505, 181.0, 6.25, 1_000L)
        val invalidTimestamp =
            iosPlatformLocationResult(37.5505, 127.0373, 6.25, 0L)

        assertSame(PlatformLocationResult.GpsUnavailable, invalidCoordinate)
        assertSame(PlatformLocationResult.GpsUnavailable, invalidTimestamp)
    }
}
