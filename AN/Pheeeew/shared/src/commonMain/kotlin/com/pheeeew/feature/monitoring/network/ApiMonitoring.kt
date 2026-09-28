package com.pheeeew.feature.monitoring.network

import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.ValueType
import com.pheeeew.core.network.ApiAttempt
import com.pheeeew.core.network.ApiAttemptObserver
import com.pheeeew.core.network.HttpAttemptOutcome

object ApiMonitoringEvents {
    val endpoints =
        setOf(
            "emotion_map",
            "emotion_list",
            "emotion_detail",
            "emotion_reaction",
            "emotion_delete",
            "emotion_block",
            "user_block",
            "group_stamps",
            "group_create",
            "group_list",
            "group_lookup",
            "group_join",
            "group_detail",
            "group_leave",
            "group_press",
            "group_ranking",
            "emotion_report",
            "emotion_register",
            "emotion_audio_upload_url",
            "emotion_audio_upload",
            "device_register",
            "device_refresh",
            "device_challenge",
        )
    val finished =
        EventDefinition(
            "http_attempt_finished",
            properties =
                mapOf(
                    "endpoint" to PropertyRule(ValueType.TEXT, required = true, allowed = endpoints),
                    "method" to
                        PropertyRule(
                            ValueType.TEXT,
                            required = true,
                            allowed = setOf("GET", "POST", "PUT", "PATCH", "DELETE"),
                        ),
                    "outcome" to
                        PropertyRule(
                            ValueType.TEXT,
                            required = true,
                            allowed = HttpAttemptOutcome.entries.map { it.name.lowercase() }.toSet(),
                        ),
                    "status_code" to PropertyRule(ValueType.INTEGER, minimum = 100.0),
                    "duration_ms" to PropertyRule(ValueType.INTEGER, required = true, minimum = 0.0),
                ),
        )
    val definitions = listOf(finished)
}

class MonitoringApiObserver(
    private val monitoring: Monitoring,
) : ApiAttemptObserver {
    override fun started(
        endpoint: String,
        method: String,
    ): ApiAttempt {
        val context = monitoring.context("network")
        return ApiAttempt { outcome, status, elapsed ->
            monitoring.track(
                DefinedEvent(
                    ApiMonitoringEvents.finished,
                    buildMap {
                        put("endpoint", EventValue.Text(endpoint))
                        put("method", EventValue.Text(method))
                        put("outcome", EventValue.Text(outcome.name.lowercase()))
                        put("duration_ms", EventValue.Integer(elapsed.coerceAtLeast(0)))
                        status?.let { put("status_code", EventValue.Integer(it.toLong())) }
                    },
                ),
                context,
            )
        }
    }
}
