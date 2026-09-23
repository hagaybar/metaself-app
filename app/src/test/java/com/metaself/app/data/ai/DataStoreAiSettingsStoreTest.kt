package com.metaself.app.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

class DataStoreAiSettingsStoreTest {

    @Test
    fun `an untouched store has the defaults`(@TempDir dir: File) = runTest {
        val settings = storeIn(dir).settings.first()

        assertThat(settings.model).isEqualTo(EstimatePrompt.DEFAULT_MODEL)
        assertThat(settings.dailyCeiling).isEqualTo(AiSettings.DEFAULT_CEILING)
        assertThat(settings.usedToday).isEqualTo(0)
    }

    @Test
    fun `the model can be changed without waiting for a release`(@TempDir dir: File) = runTest {
        val store = storeIn(dir)

        store.setModel("some-newer-model")

        assertThat(store.settings.first().model).isEqualTo("some-newer-model")
    }

    @Test
    fun `calls are counted`(@TempDir dir: File) = runTest {
        val store = storeIn(dir)

        store.recordCall()
        store.recordCall()

        assertThat(store.settings.first().usedToday).isEqualTo(2)
        assertThat(store.settings.first().remainingToday).isEqualTo(28)
    }

    @Test
    fun `the count resets when the day turns over, with nothing scheduled`(
        @TempDir dir: File,
    ) = runTest {
        // The count carries its day, so an app that was closed at midnight cannot miss a reset.
        //
        // One DataStore, two views of it: DataStore refuses two instances over the same file, and
        // what is being tested is the clock changing, not the file being reopened.
        val shared = dataStore(dir)
        val yesterday = DataStoreAiSettingsStore(shared, Today { day(TEST_EPOCH_DAY - 1) })
        val today = DataStoreAiSettingsStore(shared, Today { day(TEST_EPOCH_DAY) })

        yesterday.recordCall()
        yesterday.recordCall()
        assertThat(yesterday.settings.first().usedToday).isEqualTo(2)

        assertThat(today.settings.first().usedToday).isEqualTo(0)
    }

    private fun day(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @Test
    fun `a lowered ceiling takes effect at once`(@TempDir dir: File) = runTest {
        val store = storeIn(dir)
        store.recordCall()

        store.setDailyCeiling(1)

        assertThat(store.settings.first().remainingToday).isEqualTo(0)
    }

    private fun storeIn(dir: File, day: Long = TEST_EPOCH_DAY): DataStoreAiSettingsStore =
        DataStoreAiSettingsStore(
            store = dataStore(dir),
            today = Today { LocalDate.ofEpochDay(day) },
        )

    private fun dataStore(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "ai.preferences_pb") }
}
