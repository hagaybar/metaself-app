package com.metaself.app.data.trainer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** D116: the newest week_note, in the preferences store beside About me. Every note and moment is invented. */
class DataStoreWeekNoteStoreTest {

    private val monday = TEST_EPOCH_DAY - 3

    @Test
    fun `an untouched store has no note`(@TempDir dir: File) = runTest {
        assertThat(DataStoreWeekNoteStore(preferencesIn(dir)).note.first()).isNull()
    }

    @Test
    fun `a kept note reads back whole`(@TempDir dir: File) = runTest {
        val store = DataStoreWeekNoteStore(preferencesIn(dir))
        val note = WeekNote("Invented week note.", 2_000, monday, WeekNoteSource.LETTER)

        store.keep(note)

        assertThat(store.note.first()).isEqualTo(note)
    }

    @Test
    fun `an older note does not replace a newer one, and a newer one does`(@TempDir dir: File) = runTest {
        val store = DataStoreWeekNoteStore(preferencesIn(dir))
        val kept = WeekNote("Invented newer note.", 2_000, monday, WeekNoteSource.TRAINER)
        store.keep(kept)

        store.keep(WeekNote("Invented older note.", 1_000, monday, WeekNoteSource.TRAINER))
        assertThat(store.note.first()).isEqualTo(kept)

        val later = WeekNote("Invented later note.", 3_000, monday, WeekNoteSource.TRAINER)
        store.keep(later)
        assertThat(store.note.first()).isEqualTo(later)
    }

    private fun preferencesIn(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "settings.preferences_pb") }
}
