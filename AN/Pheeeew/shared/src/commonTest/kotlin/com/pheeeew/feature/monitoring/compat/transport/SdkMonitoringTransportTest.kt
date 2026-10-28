package com.pheeeew.feature.monitoring.compat

import com.pheeeew.core.di.appMonitoringRegistry
import com.pheeeew.core.monitoring.MeaningfulActivity
import com.pheeeew.core.monitoring.sanitizePostHogEvent
import com.pheeeew.feature.monitoring.product.ProductEvents
import com.posthog.kmp.PostHogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SdkMonitoringTransportTest {
    private val input =
        PostHogEvent(
            MeaningfulActivity.NAME,
            "sdk-id",
            mapOf(
                "event_id" to "event",
                "anonymous_id" to "install",
                "event_schema_version" to 1,
                "activity_date" to "2026-10-08",
                "activity_type" to "personal_press",
                "audience" to "unknown",
                "measurement_version" to "user_report_v1",
                "environment" to "prod",
                "platform" to "android",
                "app_version" to "2.2",
            ),
        )

    @Test
    fun `report event uses installation identity and removes SDK and content properties`() {
        val extra =
            mapOf(
                "memo" to "private",
                "latitude" to 37.0,
                "session_id" to "session",
                "\$device_id" to "device",
                "press_count" to 1000,
            )
        val clean =
            assertNotNull(
                sanitizePostHogEvent(input.copy(properties = input.properties + extra), appMonitoringRegistry()),
            )
        assertEquals("install", clean.distinctId)
        assertEquals(input.properties.keys + "\$geoip_disable", clean.properties.keys)
        assertEquals(true, clean.properties["\$geoip_disable"])
    }

    @Test
    fun `collection claim click is registered and keeps its collection screen`() {
        val event =
            input.copy(
                event = ProductEvents.COLLECTION_CLAIM_CLICKED,
                properties = input.properties + ("screen" to "collection"),
            )

        val clean = assertNotNull(sanitizePostHogEvent(event, appMonitoringRegistry()))

        assertEquals("collection", clean.properties["screen"])
        assertEquals("install", clean.distinctId)
    }

    @Test
    fun `automatic legacy request and funnel events are all rejected`() {
        for (name in listOf(
            "sigh_started",
            "memo_completed",
            "star_detail_shown",
            "http_attempt_finished",
            "app_active_day",
            "settings_action_selected",
            "\$screen",
            "\$snapshot",
        )) {
            assertNull(sanitizePostHogEvent(input.copy(event = name), appMonitoringRegistry()), name)
        }
    }

    @Test
    fun `missing type invalid dates and unknown measurement versions are rejected`() {
        assertNull(
            sanitizePostHogEvent(input.copy(properties = input.properties - "activity_type"), appMonitoringRegistry()),
        )
        for ((key, value) in listOf(
            "activity_type" to "opened",
            "activity_date" to "2026-02-31",
            "measurement_version" to "other",
            "audience" to "everyone",
        )) {
            assertNull(
                sanitizePostHogEvent(
                    input.copy(properties = input.properties + (key to value)),
                    appMonitoringRegistry(),
                ),
            )
        }
    }
}
