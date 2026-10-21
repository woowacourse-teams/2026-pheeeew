package com.pheeeew

import android.content.Context
import com.pheeeew.core.di.AndroidAppConfig
import com.pheeeew.core.di.AndroidAppServiceFactory
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.di.createAndroidAppGraph
import com.pheeeew.developertools.simulation.DebugAppServiceFactory

internal fun createApplicationGraph(
    context: Context,
    config: AndroidAppConfig,
): AppGraph = createAndroidAppGraph(context, config, DebugAppServiceFactory(AndroidAppServiceFactory(context)))
