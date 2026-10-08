package com.pheeeew.core.monitoring

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

enum class ActivityType(
    val wireValue: String,
) {
    EMOTION_RECORD("emotion_record"),
    PERSONAL_PRESS("personal_press"),
    GROUP_PRESS("group_press"),
}

internal const val DAY_MILLIS = 86_400_000L
internal const val ACTIVITY_MAX_AGE = 35 * DAY_MILLIS
internal const val ACTIVITY_KEY_RETENTION = 42 * DAY_MILLIS

fun activityDate(timestamp: Long): String =
    Instant.fromEpochMilliseconds(timestamp + 9 * 60 * 60 * 1000L).toString().substringBefore('T')

object MeaningfulActivity {
    const val NAME = "meaningful_activity_day"
    const val VERSION = "user_report_v1"
    val definition =
        EventDefinition(
            NAME,
            properties =
                mapOf(
                    "activity_date" to PropertyRule(ValueType.TEXT, required = true, maxLength = 10),
                    "activity_type" to
                        PropertyRule(
                            ValueType.TEXT,
                            required = true,
                            allowed = ActivityType.entries.map { it.wireValue }.toSet(),
                        ),
                    "audience" to
                        PropertyRule(
                            ValueType.TEXT,
                            required = true,
                            allowed = setOf("external", "internal", "test", "unknown"),
                        ),
                    "measurement_version" to PropertyRule(ValueType.TEXT, required = true, allowed = setOf(VERSION)),
                ),
            preserveOnOverflow = true,
        )
    private val common =
        setOf("anonymous_id", "event_id", "event_schema_version", "environment", "platform", "app_version")

    internal fun sanitize(fields: JsonObject): JsonObject? {
        for (key in listOf("environment", "platform", "app_version")) {
            val value = fields[key] as? JsonPrimitive ?: return null
            if (!value.isString || value.content.isBlank()) return null
        }
        if ((fields["environment"] as JsonPrimitive).content !in setOf("prod", "dev")) return null
        val date = (fields["activity_date"] as? JsonPrimitive)?.content ?: return null
        if (!date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) ||
            runCatching { Instant.parse("${date}T00:00:00Z") }.isFailure
        ) {
            return null
        }
        return JsonObject(fields.filterKeys { it in common || it in definition.properties })
    }
}
