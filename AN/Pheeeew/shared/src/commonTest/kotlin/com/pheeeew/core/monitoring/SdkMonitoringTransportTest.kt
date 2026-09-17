package com.pheeeew.core.monitoring

import com.posthog.kmp.PostHogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdkMonitoringTransportTest {
    @Test
    fun posthogFilterDropsUnapprovedFieldsAndAutomaticEvents() {
        val input =
            PostHogEvent(
                "sigh_started",
                "sdk-id",
                mapOf(
                    "event_id" to "event",
                    "anonymous_id" to "install",
                    "session_id" to "visit",
                    "memo" to "private",
                    "latitude" to 37.0,
                    "Authorization" to "private",
                    "\$device_id" to "sdk-device",
                    "environment" to "dev",
                ),
            )
        val clean = sanitizePostHogEvent(input)!!
        assertEquals("install", clean.distinctId)
        assertEquals(
            setOf("event_id", "anonymous_id", "session_id", "environment", "\$geoip_disable"),
            clean.properties.keys,
        )
        assertNull(sanitizePostHogEvent(input.copy(event = "\$screen")))
    }
}
