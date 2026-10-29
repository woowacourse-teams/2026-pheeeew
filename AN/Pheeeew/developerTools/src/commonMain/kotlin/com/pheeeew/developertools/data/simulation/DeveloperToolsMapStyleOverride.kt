package com.pheeeew.developertools.data.simulation

import com.pheeeew.feature.screens.map.renderer.MapStyleOverrideProvider

object DeveloperToolsMapStyleOverride : MapStyleOverrideProvider {
    override fun consumeForNextLoad(): String? =
        if (ErrorSimulation.shouldFail(ErrorSimulationScenario.MapRenderer)) {
            FAILED_MAP_STYLE_URL
        } else {
            null
        }
}

private const val FAILED_MAP_STYLE_URL = "https://127.0.0.1:1/developer-tools-map-style.json"
