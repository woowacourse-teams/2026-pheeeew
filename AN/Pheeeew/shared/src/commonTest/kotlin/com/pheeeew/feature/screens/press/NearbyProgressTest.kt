package com.pheeeew.feature.screens.press

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NearbyProgressTest {
    @Test
    fun invalidValuesRemainUnknownInsteadOfCreatingInvalidLayoutSizes() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertNull(normalizedNearbyProgress(it))
        }
    }

    @Test
    fun finiteProgressIsClampedWithoutLosingKnownZero() {
        assertEquals(0f, normalizedNearbyProgress(-1f))
        assertEquals(0f, normalizedNearbyProgress(0f))
        assertEquals(0.25f, normalizedNearbyProgress(0.25f))
        assertEquals(1f, normalizedNearbyProgress(2f))
    }
}
