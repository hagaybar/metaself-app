package com.metaself.app.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metaself.app.data.time.Today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The model, the ceiling and today's count, beside the profile in the same DataStore.
 *
 * The count carries the day it belongs to. Reading it on a later day reports zero without writing
 * anything, so a reset needs no scheduled work and cannot be missed by an app that was closed at
 * midnight.
 */
class DataStoreAiSettingsStore(
    private val store: DataStore<Preferences>,
    private val today: Today,
) : AiSettingsStore {

    override val settings: Flow<AiSettings> = store.data.map { preferences ->
        val countedOn = preferences[key(KEY_COUNT_DAY)]?.toLongOrNull()
        val used = preferences[key(KEY_COUNT)]?.toIntOrNull() ?: 0
        AiSettings(
            model = preferences[key(KEY_MODEL)]?.takeIf { it.isNotBlank() }
                ?: EstimatePrompt.DEFAULT_MODEL,
            dailyCeiling = preferences[key(KEY_CEILING)]?.toIntOrNull()
                ?: AiSettings.DEFAULT_CEILING,
            usedToday = if (countedOn == today().toEpochDay()) used else 0,
        )
    }

    override suspend fun setModel(model: String) {
        store.edit { it[key(KEY_MODEL)] = model.trim() }
    }

    override suspend fun setDailyCeiling(ceiling: Int) {
        store.edit { it[key(KEY_CEILING)] = ceiling.coerceAtLeast(0).toString() }
    }

    override suspend fun recordCall() {
        val day = today().toEpochDay()
        store.edit { preferences ->
            val countedOn = preferences[key(KEY_COUNT_DAY)]?.toLongOrNull()
            val used = if (countedOn == day) preferences[key(KEY_COUNT)]?.toIntOrNull() ?: 0 else 0
            preferences[key(KEY_COUNT_DAY)] = day.toString()
            preferences[key(KEY_COUNT)] = (used + 1).toString()
        }
    }

    private fun key(name: String) = stringPreferencesKey(name)

    private companion object {
        const val KEY_MODEL = "ai_model"
        const val KEY_CEILING = "ai_daily_ceiling"
        const val KEY_COUNT = "ai_calls_today"
        const val KEY_COUNT_DAY = "ai_calls_day"
    }
}
