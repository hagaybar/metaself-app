package com.metaself.app.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metaself.app.domain.reminder.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Where the one reminder is kept: beside the profile, because it is one record with no queries. */
interface ReminderStore {

    val reminder: Flow<Reminder>

    suspend fun save(reminder: Reminder)

    /** For the alarm and the boot receiver, which have no scope to collect a flow in. */
    suspend fun current(): Reminder
}

class DataStoreReminderStore @Inject constructor(
    private val store: DataStore<Preferences>,
) : ReminderStore {

    override val reminder: Flow<Reminder> = store.data.map { preferences ->
        ReminderCodec.decode(
            preferences.asMap()
                .mapNotNull { (key, value) -> (value as? String)?.let { key.name to it } }
                .toMap(),
        )
    }

    override suspend fun save(reminder: Reminder) {
        val encoded = ReminderCodec.encode(reminder)
        store.edit { preferences ->
            encoded.forEach { (key, value) ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    override suspend fun current(): Reminder = reminder.first()
}
