package com.pheeeew

import com.pheeeew.core.di.IosAppServiceFactory
import com.pheeeew.core.di.initializeIosAppGraph
import com.pheeeew.developertools.data.simulation.DebugAppServiceFactory

fun initializeDeveloperTools() {
    initializeIosAppGraph(DebugAppServiceFactory(IosAppServiceFactory()))
}
