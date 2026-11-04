package com.pheeeew

import android.content.Context
import com.pheeeew.core.di.AndroidAppConfig
import com.pheeeew.core.di.AndroidAppServiceFactory
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.di.createAndroidAppGraph
import com.pheeeew.developertools.data.simulation.DebugAppServiceFactory

internal fun createApplicationGraph(
    context: Context,
    config: AndroidAppConfig,
): AppGraph =
    createAndroidAppGraph(
        context,
        if (config.applicationId.endsWith(".updatetest")) {
            config.copy(
                versionRepositoryDecorator = { remote -> UpdateTestRepository(context, remote) },
            )
        } else {
            config
        },
        DebugAppServiceFactory(AndroidAppServiceFactory(context)),
    )
