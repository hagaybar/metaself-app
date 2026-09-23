package com.metaself.app.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Everything the settings store holds, taken before a restore writes to it, so a restore that fails
 * can put it back.
 *
 * The settings cannot join the database's transaction, so this is how their half of a restore is
 * undone. [take] returns the put-back rather than a value to hand around, so nothing but this can
 * write it.
 */
fun interface SettingsSnapshot {
    suspend fun take(): suspend () -> Unit
}

/**
 * The snapshot, over the one preferences DataStore the profile, the AI settings and the reminder
 * share (`DataModule.provideProfileDataStore`) — so one read covers every key a restore can touch.
 *
 * Put back as the whole file rather than key by key: a key the restore added is removed, a flag it
 * set is unset — neither of which the per-key setters can do — and the put-back is one write, not
 * seven.
 */
class DataStoreSettingsSnapshot @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsSnapshot {

    override suspend fun take(): suspend () -> Unit {
        val before = store.data.first()
        return {
            store.edit { preferences ->
                preferences.clear()
                preferences += before
            }
        }
    }
}
