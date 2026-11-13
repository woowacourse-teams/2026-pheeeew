package com.pheeeew

import android.content.Context
import com.pheeeew.core.di.AndroidAppConfig
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.di.createAndroidAppGraph

internal fun createApplicationGraph(
    context: Context,
    config: AndroidAppConfig,
): AppGraph = createAndroidAppGraph(context, config)
