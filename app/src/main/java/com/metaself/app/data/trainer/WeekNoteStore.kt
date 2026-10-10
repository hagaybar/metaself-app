package com.metaself.app.data.trainer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.domain.trainer.WeekNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * D116: the newest week_note, which the Trainer page shows as the coach's note. One note, replaced only by
 * [WeekNotes.replaces]'s rule. Not in the backup: it is about one week, and the next answer writes another.
 */
interface WeekNoteStore {
    /** The kept note; null when none was ever kept. */
    val note: Flow<WeekNote?>

    /** Keeps [note] when [WeekNotes.replaces] the kept one says so; otherwise nothing changes. */
    suspend fun keep(note: WeekNote)

    companion object {
        /** Keeps nothing: for the callers' tests that do not look at the note. */
        val NONE: WeekNoteStore = object : WeekNoteStore {
            override val note: Flow<WeekNote?> = flowOf(null)
            override suspend fun keep(note: WeekNote) = Unit
        }
    }
}

/**
 * Over the one preferences DataStore the profile, the AI settings and About me share
 * (`DataModule.provideProfileDataStore`). Four keys; a row missing one, or naming an unknown source, reads as
 * no note.
 */
class DataStoreWeekNoteStore(private val store: DataStore<Preferences>) : WeekNoteStore {

    override val note: Flow<WeekNote?> = store.data.map(::read)

    override suspend fun keep(note: WeekNote) {
        store.edit { preferences ->
            if (WeekNotes.replaces(read(preferences), note)) {
                preferences[TEXT] = note.text
                preferences[AT] = note.writtenAtMillis
                preferences[WEEK] = note.weekMonday
                preferences[FROM] = note.source.name
            }
        }
    }

    private fun read(preferences: Preferences): WeekNote? {
        val text = preferences[TEXT] ?: return null
        val at = preferences[AT] ?: return null
        val week = preferences[WEEK] ?: return null
        val source = WeekNoteSource.entries.firstOrNull { it.name == preferences[FROM] } ?: return null
        return WeekNote(text, at, week, source)
    }

    private companion object {
        val TEXT = stringPreferencesKey("trainer_week_note")
        val AT = longPreferencesKey("trainer_week_note_at")
        val WEEK = longPreferencesKey("trainer_week_note_week")
        val FROM = stringPreferencesKey("trainer_week_note_from")
    }
}
