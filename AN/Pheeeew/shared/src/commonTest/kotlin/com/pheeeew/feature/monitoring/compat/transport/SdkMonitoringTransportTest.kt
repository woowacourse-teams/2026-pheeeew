package com.pheeeew.feature.monitoring.compat

import com.pheeeew.core.di.appMonitoringRegistry
import com.pheeeew.core.monitoring.sanitizePostHogEvent
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
                    "entry_point" to "map",
                    "guide_mode" to false,
                    "memo" to "private",
                    "latitude" to 37.0,
                    "Authorization" to "private",
                    "\$device_id" to "sdk-device",
                    "environment" to "dev",
                ),
            )
        val clean = sanitizePostHogEvent(input, appMonitoringRegistry())!!
        assertEquals("install", clean.distinctId)
        assertEquals(
            setOf(
                "event_id",
                "anonymous_id",
                "session_id",
                "environment",
                "event_schema_version",
                "entry_point",
                "guide_mode",
                "\$geoip_disable",
            ),
            clean.properties.keys,
        )
        assertNull(sanitizePostHogEvent(input.copy(event = "\$screen"), appMonitoringRegistry()))
    }

    @Test
    fun posthogFilterAllowsMemoMetricsButDropsMemoContent() {
        val input =
            PostHogEvent(
                "memo_completed",
                "sdk-id",
                mapOf(
                    "event_id" to "event",
                    "anonymous_id" to "install",
                    "session_id" to "visit",
                    "sigh_attempt_id" to "attempt",
                    "memo_present" to true,
                    "memo_elapsed_ms" to 1500L,
                    "memo" to "private",
                ),
            )

        val clean = sanitizePostHogEvent(input, appMonitoringRegistry())!!

        assertEquals(true, clean.properties["memo_present"])
        assertEquals(1500L, clean.properties["memo_elapsed_ms"])
        assertNull(clean.properties["memo"])
    }

    @Test
    fun posthogFilterAllowsMapCorrelationWithoutSighIdentifiers() {
        val input =
            PostHogEvent(
                "star_detail_shown",
                "sdk-id",
                mapOf(
                    "event_id" to "event",
                    "anonymous_id" to "install",
                    "session_id" to "visit",
                    "map_visit_id" to "map-visit",
                    "selection_id" to "selection",
                    "entry_source" to "map",
                    "sigh_id" to 123L,
                    "latitude" to 37.0,
                ),
            )

        val clean = sanitizePostHogEvent(input, appMonitoringRegistry())!!

        assertEquals("map-visit", clean.properties["map_visit_id"])
        assertEquals("selection", clean.properties["selection_id"])
        assertEquals("map", clean.properties["entry_source"])
        assertNull(clean.properties["sigh_id"])
        assertNull(clean.properties["latitude"])
    }
}
