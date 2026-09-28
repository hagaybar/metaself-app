package com.metaself.app.data.trainer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.DataStoreAiSettingsStore
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

/** D90: the owner's note, in the preferences store beside the AI settings. Every word is invented. */
class DataStoreAboutMeStoreTest {

    @Test
    fun `an untouched store has no note`(@TempDir dir: File) = runTest {
        assertThat(DataStoreAboutMeStore(preferencesIn(dir)).note.first()).isEmpty()
    }

    @Test
    fun `a saved note reads back, trimmed`(@TempDir dir: File) = runTest {
        val store = DataStoreAboutMeStore(preferencesIn(dir))

        store.save("  Invented note.\nSecond line.  ")

        assertThat(store.note.first()).isEqualTo("Invented note.\nSecond line.")
    }

    @Test
    fun `a note is kept to a thousand characters`(@TempDir dir: File) = runTest {
        val store = DataStoreAboutMeStore(preferencesIn(dir))

        store.save("a".repeat(AboutMeStore.MAX + 50))

        assertThat(store.note.first()).hasLength(1_000)
    }

    /** A character outside the basic plane is two chars: cut at the limit, it is left out whole, never halved. */
    @Test
    fun `the limit never splits a character in two`(@TempDir dir: File) = runTest {
        val store = DataStoreAboutMeStore(preferencesIn(dir))

        store.save("a".repeat(999) + "\uD83D\uDE00")

        assertThat(store.note.first()).isEqualTo("a".repeat(999))
    }

    @Test
    fun `saving an empty note clears it`(@TempDir dir: File) = runTest {
        val store = DataStoreAboutMeStore(preferencesIn(dir))
        store.save("Invented note.")

        store.save("   ")

        assertThat(store.note.first()).isEmpty()
    }

    /** It shares the AI settings' file; a write to either leaves the other as it was. */
    @Test
    fun `it sits beside the AI settings without disturbing them`(@TempDir dir: File) = runTest {
        val shared = preferencesIn(dir)
        val note = DataStoreAboutMeStore(shared)
        val ai = DataStoreAiSettingsStore(shared, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) })

        ai.setModel("some-model")
        note.save("Invented note.")
        ai.setDailyCeiling(12)

        assertThat(note.note.first()).isEqualTo("Invented note.")
        assertThat(ai.settings.first().model).isEqualTo("some-model")
        assertThat(ai.settings.first().dailyCeiling).isEqualTo(12)
    }

    private fun preferencesIn(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "settings.preferences_pb") }
}
