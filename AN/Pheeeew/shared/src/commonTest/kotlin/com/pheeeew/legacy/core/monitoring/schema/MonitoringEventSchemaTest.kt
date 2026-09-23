package com.pheeeew.legacy.core.monitoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitoringEventSchemaTest {
    @Test
    fun definitionsKeepWireNameAndRequiredFields() {
        assertEquals("sigh_started", MonitoringEventNames.SIGH_STARTED.value)
        assertTrue("entry_point" in MonitoringEventNames.SIGH_STARTED.requiredProperties)
        assertTrue("guide_mode" in MonitoringEventNames.SIGH_STARTED.requiredProperties)
        assertEquals(40, MonitoringEventNames.definitions.size)
        assertEquals(MonitoringEventNames.definitions.size, MonitoringEventNames.all.size)
    }

    @Test
    fun sanitizerRemovesControlCharactersAndLimitsStringSize() {
        val sanitized =
            MonitoringValueSanitizer.fields(
                mapOf("reason" to "ok\u0000${"x".repeat(200)}"),
            )

        val reason = sanitized["reason"] as String
        assertFalse(reason.any(Char::isISOControl))
        assertEquals(128, reason.length)
    }

    @Test
    fun sanitizerDropsNonFiniteNumbers() {
        val sanitized =
            MonitoringValueSanitizer.fields(
                mapOf(
                    "nan" to Float.NaN,
                    "infinity" to Double.POSITIVE_INFINITY,
                    "valid" to 1.5,
                ),
            )

        assertFalse("nan" in sanitized)
        assertFalse("infinity" in sanitized)
        assertEquals(1.5, sanitized["valid"])
    }
}
