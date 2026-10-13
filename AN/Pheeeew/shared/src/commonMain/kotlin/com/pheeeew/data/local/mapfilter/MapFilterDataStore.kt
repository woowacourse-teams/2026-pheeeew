package com.pheeeew.data.local.mapfilter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterPeriod
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.model.emotion.SearchDate
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import okio.Path

internal const val MAP_FILTER_DATASTORE_FILE = "map_filter.preferences_pb"

fun createMapFilterDataStore(produceFile: () -> Path): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = produceFile)

class PreferencesMapFilterSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : MapFilterSettingsRepository {
    override suspend fun read(): MapFilterSettings =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.map(::toSettings)
            .first()

    override suspend fun save(settings: MapFilterSettings) {
        dataStore.edit { preferences ->
            preferences[VERSION] = CURRENT_VERSION
            preferences[PERIOD] = settings.period.name
            settings.customStartDate?.let { preferences[START_DATE] = it.toIsoString() }
                ?: preferences.remove(START_DATE)
            settings.customEndDate?.let { preferences[END_DATE] = it.toIsoString() } ?: preferences.remove(END_DATE)
            preferences[CONTENT] = settings.content.name
            preferences[SCOPE] = settings.scope.name
            settings.groupId?.let { preferences[GROUP_ID] = it } ?: preferences.remove(GROUP_ID)
        }
    }

    private fun toSettings(preferences: Preferences): MapFilterSettings {
        if (preferences[VERSION] != CURRENT_VERSION) return MapFilterSettings.DEFAULT
        val period =
            preferences[PERIOD]?.let { value -> MapFilterPeriod.entries.firstOrNull { it.name == value } }
                ?: return MapFilterSettings.DEFAULT
        val content =
            preferences[CONTENT]?.let { value -> MapFilterContent.entries.firstOrNull { it.name == value } }
                ?: return MapFilterSettings.DEFAULT
        val scope =
            preferences[SCOPE]?.let { value -> MapFilterScope.entries.firstOrNull { it.name == value } }
                ?: return MapFilterSettings.DEFAULT
        val start = preferences[START_DATE]?.let { SearchDate.parse(it) }
        val end = preferences[END_DATE]?.let { SearchDate.parse(it) }
        val groupId = preferences[GROUP_ID]
        return runCatching {
            MapFilterSettings(period, start, end, content, scope, groupId)
        }.getOrDefault(MapFilterSettings.DEFAULT)
    }

    private companion object {
        const val CURRENT_VERSION = 1
        val VERSION = intPreferencesKey("version")
        val PERIOD = stringPreferencesKey("period")
        val START_DATE = stringPreferencesKey("custom_start_date")
        val END_DATE = stringPreferencesKey("custom_end_date")
        val CONTENT = stringPreferencesKey("content")
        val SCOPE = stringPreferencesKey("scope")
        val GROUP_ID = stringPreferencesKey("group_id")
    }
}
