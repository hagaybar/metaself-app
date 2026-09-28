package com.metaself.app.data.trainer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The owner's "About me" note for the trainer (D90): one free text, written once and edited any time,
 * sent unchanged with every trainer request. Its own interface rather than a member of the AI settings,
 * so nothing that only needs the model's settings has to know about it.
 */
interface AboutMeStore {
    /** The note; "" when there is none. */
    val note: Flow<String>

    /** Stored trimmed, and at most [MAX] characters; a blank note clears it. */
    suspend fun save(note: String)

    companion object {
        const val MAX = 1_000

        /**
         * [text] cut to at most [MAX] chars without splitting a character outside the basic plane (an
         * emoji is two chars): one whose second half would fall past the limit is left out whole.
         */
        fun cut(text: String): String {
            if (text.length <= MAX) return text
            val end = if (Character.isHighSurrogate(text[MAX - 1])) MAX - 1 else MAX
            return text.substring(0, end)
        }
    }
}

/**
 * Over the one preferences DataStore the profile and the AI settings share
 * (`DataModule.provideProfileDataStore`), so the settings snapshot a restore takes covers it too.
 */
class DataStoreAboutMeStore(private val store: DataStore<Preferences>) : AboutMeStore {

    override val note: Flow<String> = store.data.map { it[KEY].orEmpty() }

    override suspend fun save(note: String) {
        val kept = AboutMeStore.cut(note.trim())
        store.edit { preferences ->
            if (kept.isEmpty()) preferences.remove(KEY) else preferences[KEY] = kept
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("trainer_about_me")
    }
}
