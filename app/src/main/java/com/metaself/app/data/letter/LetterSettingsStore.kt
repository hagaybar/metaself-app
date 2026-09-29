package com.metaself.app.data.letter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** D99: whether the weekly letter is written, and at which Sunday hour. */
data class LetterSettings(val on: Boolean = true, val hour: Int = DEFAULT_HOUR) {
    companion object {
        const val DEFAULT_HOUR = 20
        val HOURS = 18..23
    }
}

interface LetterSettingsStore {
    val settings: Flow<LetterSettings>
    suspend fun setOn(on: Boolean)

    /** Clamped to [LetterSettings.HOURS]. */
    suspend fun setHour(hour: Int)
}

/** Over the preferences DataStore the AI settings use, so a restore's settings snapshot covers it. */
class DataStoreLetterSettingsStore(private val store: DataStore<Preferences>) : LetterSettingsStore {
    override val settings: Flow<LetterSettings> = store.data.map {
        LetterSettings(on = it[ON] ?: true, hour = (it[HOUR] ?: LetterSettings.DEFAULT_HOUR).coerceIn(LetterSettings.HOURS))
    }

    override suspend fun setOn(on: Boolean) {
        store.edit { it[ON] = on }
    }

    override suspend fun setHour(hour: Int) {
        store.edit { it[HOUR] = hour.coerceIn(LetterSettings.HOURS) }
    }

    private companion object {
        val ON = booleanPreferencesKey("weekly_letter_on")
        val HOUR = intPreferencesKey("weekly_letter_hour")
    }
}
