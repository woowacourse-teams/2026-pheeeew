package com.pheeeew.developertools.feature

import com.pheeeew.developertools.data.simulation.ErrorSimulationScenario
import com.pheeeew.developertools.data.simulation.ErrorSimulationState

/** Untouched fields follow live conditions, including consumed NEXT modes and network recovery. */
internal data class SimulationDraft(
    val baseline: ErrorSimulationState,
    val edited: ErrorSimulationState = baseline,
) {
    fun resolve(applied: ErrorSimulationState): ErrorSimulationState =
        applied.copy(
            offline = if (edited.offline == baseline.offline) applied.offline else edited.offline,
            networkDelayMillis =
                if (edited.networkDelayMillis ==
                    baseline.networkDelayMillis
                ) {
                    applied.networkDelayMillis
                } else {
                    edited.networkDelayMillis
                },
            networkNeverResponds =
                if (edited.networkNeverResponds ==
                    baseline.networkNeverResponds
                ) {
                    applied.networkNeverResponds
                } else {
                    edited.networkNeverResponds
                },
            location = if (edited.location == baseline.location) applied.location else edited.location,
            modes =
                ErrorSimulationScenario.entries
                    .mapNotNull { scenario ->
                        val mode =
                            if (edited.modes[scenario] ==
                                baseline.modes[scenario]
                            ) {
                                applied.modes[scenario]
                            } else {
                                edited.modes[scenario]
                            }
                        mode?.let { scenario to it }
                    }.toMap(),
        )
}
