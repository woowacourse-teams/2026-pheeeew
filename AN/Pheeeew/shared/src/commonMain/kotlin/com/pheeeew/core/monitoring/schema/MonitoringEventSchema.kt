package com.pheeeew.core.monitoring

/** Event metadata kept separate from the string value sent to analytics providers. */
data class MonitoringEventDefinition(
    val value: String,
    val requiredProperties: Set<String> = emptySet(),
)

/**
 * Sanitizes values at the monitoring boundary. Callers can still use domain strings internally,
 * but control characters and unexpectedly large values cannot reach an SDK.
 */
internal object MonitoringValueSanitizer {
    private const val MAX_STRING_LENGTH = 128

    fun fields(fields: Map<String, Any>): Map<String, Any> =
        fields
            .mapNotNull { (key, value) ->
                sanitize(value)?.let { key to it }
            }.toMap()

    private fun sanitize(value: Any): Any? =
        when (value) {
            is String -> {
                value
                    .filterNot(Char::isISOControl)
                    .take(MAX_STRING_LENGTH)
                    .takeIf(String::isNotEmpty)
            }

            is Boolean, is Number -> {
                value
            }

            else -> {
                null
            }
        }
}
