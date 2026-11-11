package com.pheeeew.data.config

import com.pheeeew.core.di.IosGraphs

/** Swift scene callbacks run on the main thread after FirebaseApp.configure(). */
object IosPressEventConfig {
    fun foreground(source: PressEventConfigSource) = IosGraphs.app.pressEventConfig.onForeground(source)
}
