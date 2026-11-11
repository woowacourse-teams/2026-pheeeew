package com.pheeeew.data.repository

import com.pheeeew.data.config.PressEventConfigSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-scoped; foreground calls and SDK completions are serialized on the main thread. */
class PressEventConfigRepository(
    environment: String,
    isDebug: Boolean,
) {
    val parameterKey: String =
        when (environment) {
            "dev" -> "press_event_enabled_dev"
            "prod" -> "press_event_enabled_prod"
            else -> error("Unsupported event config environment")
        }
    val minimumFetchIntervalSeconds: Long = if (isDebug) 0L else 3_600L
    private val mutableEnabled = MutableStateFlow(false)
    val eventButtonEnabled = mutableEnabled.asStateFlow()
    private var initialized = false
    private var refreshing = false

    /** The platform owner supplies the same SDK adapter for the lifetime of this repository. */
    fun onForeground(source: PressEventConfigSource) {
        if (refreshing) return
        refreshing = true
        if (initialized) {
            fetch(source)
        } else {
            source.initialize(parameterKey, minimumFetchIntervalSeconds) { succeeded ->
                if (succeeded) {
                    initialized = true
                    // Restore the last activated SDK cache before waiting for any network request.
                    mutableEnabled.value = source.activatedBoolean(parameterKey)
                    fetch(source)
                } else {
                    refreshing = false
                }
            }
        }
    }

    private fun fetch(source: PressEventConfigSource) {
        source.fetchAndActivate { succeeded ->
            if (succeeded) mutableEnabled.value = source.activatedBoolean(parameterKey)
            refreshing = false
        }
    }
}
