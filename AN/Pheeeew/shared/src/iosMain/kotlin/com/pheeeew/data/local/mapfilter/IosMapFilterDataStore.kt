@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pheeeew.data.local.mapfilter

import com.pheeeew.domain.repository.MapFilterSettingsRepository
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

object IosMapFilterSettings {
    val repository: MapFilterSettingsRepository by lazy {
        val supportDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val path = (requireNotNull(supportDirectory?.path) + "/$MAP_FILTER_DATASTORE_FILE").toPath()
        PreferencesMapFilterSettingsRepository(
            createMapFilterDataStore { path },
        )
    }
}
