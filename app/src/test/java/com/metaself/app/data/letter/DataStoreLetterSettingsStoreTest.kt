package com.metaself.app.data.letter

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

/** D99: whether the weekly letter is written, and at which Sunday hour, beside the AI settings. */
class DataStoreLetterSettingsStoreTest {

    @Test
    fun `an untouched store is on, at eight in the evening`(@TempDir dir: File) = runTest {
        assertThat(DataStoreLetterSettingsStore(preferencesIn(dir)).settings.first()).isEqualTo(LetterSettings(on = true, hour = 20))
    }

    @Test
    fun `a chosen hour and the switch are stored`(@TempDir dir: File) = runTest {
        val store = DataStoreLetterSettingsStore(preferencesIn(dir))

        store.setHour(23)
        store.setOn(false)

        assertThat(store.settings.first()).isEqualTo(LetterSettings(on = false, hour = 23))
    }

    @Test
    fun `an hour outside six to eleven in the evening is brought inside`(@TempDir dir: File) = runTest {
        val store = DataStoreLetterSettingsStore(preferencesIn(dir))

        store.setHour(17)
        assertThat(store.settings.first().hour).isEqualTo(18)
        store.setHour(24)
        assertThat(store.settings.first().hour).isEqualTo(23)
    }

    /**
     * Another store over the same file reads what the first wrote. One DataStore, two views of it:
     * DataStore refuses two instances over the same file in one process.
     */
    @Test
    fun `the setting is read back by a new store over the same file`(@TempDir dir: File) = runTest {
        val shared = preferencesIn(dir)
        DataStoreLetterSettingsStore(shared).apply {
            setOn(false)
            setHour(19)
        }

        assertThat(DataStoreLetterSettingsStore(shared).settings.first()).isEqualTo(LetterSettings(on = false, hour = 19))
    }

    /** It shares the AI settings' file; a write to either leaves the other as it was. */
    @Test
    fun `it sits beside the AI settings without disturbing them`(@TempDir dir: File) = runTest {
        val shared = preferencesIn(dir)
        val letter = DataStoreLetterSettingsStore(shared)
        val ai = DataStoreAiSettingsStore(shared, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) })

        ai.setModel("some-model")
        letter.setHour(21)

        assertThat(letter.settings.first().hour).isEqualTo(21)
        assertThat(ai.settings.first().model).isEqualTo("some-model")
    }

    private fun preferencesIn(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "settings.preferences_pb") }
}
