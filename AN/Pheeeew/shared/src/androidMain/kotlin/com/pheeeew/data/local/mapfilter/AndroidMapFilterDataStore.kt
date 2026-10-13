package com.pheeeew.data.local.mapfilter

import android.content.Context
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import okio.Path.Companion.toPath

object AndroidMapFilterSettings {
    @Volatile
    private var repository: MapFilterSettingsRepository? = null

    fun repository(context: Context): MapFilterSettingsRepository =
        repository ?: synchronized(this) {
            repository ?: PreferencesMapFilterSettingsRepository(
                createMapFilterDataStore {
                    context.applicationContext.filesDir.resolve(MAP_FILTER_DATASTORE_FILE).absolutePath.toPath()
                },
            ).also { repository = it }
        }
}
