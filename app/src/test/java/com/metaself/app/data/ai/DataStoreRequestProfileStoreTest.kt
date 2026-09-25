package com.metaself.app.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

/** Remembered profiles, per model name, in the settings store (D57 §5). */
class DataStoreRequestProfileStoreTest {

    private val learned = RequestProfile(temperature = false, reasoningEffort = "medium", strictFormat = false)

    @Test
    fun `a name with nothing remembered has no profile`(@TempDir dir: File) = runTest {
        assertThat(DataStoreRequestProfileStore(dataStore(dir)).profileFor("gpt-6-luna").first()).isNull()
    }

    @Test
    fun `a remembered profile reads back whole, for exactly its name`(@TempDir dir: File) = runTest {
        val store = DataStoreRequestProfileStore(dataStore(dir))

        store.remember("gpt-6-luna", learned)

        assertThat(store.profileFor("gpt-6-luna").first()).isEqualTo(learned)
        assertThat(store.profileFor("gpt-6-luna-mini").first()).isNull()
        assertThat(store.profileFor("GPT-6-LUNA").first()).isNull()
    }

    @Test
    fun `remembering one name leaves the others, and a second profile replaces the first`(
        @TempDir dir: File,
    ) = runTest {
        val store = DataStoreRequestProfileStore(dataStore(dir))

        store.remember("gpt-6-luna", learned)
        store.remember("gpt-4o", RequestProfile.DETERMINISTIC)
        store.remember("gpt-6-luna", RequestProfile.REASONING)

        assertThat(store.profileFor("gpt-6-luna").first()).isEqualTo(RequestProfile.REASONING)
        assertThat(store.profileFor("gpt-4o").first()).isEqualTo(RequestProfile.DETERMINISTIC)
    }

    /** Two calls learning at once: each write is one edit, so neither loses the other's name. */
    @Test
    fun `profiles remembered at once are all kept`(@TempDir dir: File) = runTest {
        val store = DataStoreRequestProfileStore(dataStore(dir))
        val names = (1..20).map { "model-$it" }

        names.map { name -> async { store.remember(name, learned) } }.awaitAll()

        names.forEach { assertThat(store.profileFor(it).first()).isEqualTo(learned) }
    }

    @Test
    fun `an entry that cannot be read is nothing remembered, and is rewritten`(@TempDir dir: File) = runTest {
        val preferences = dataStore(dir)
        preferences.edit { it[stringPreferencesKey("ai_request_profiles")] = "not json" }
        val store = DataStoreRequestProfileStore(preferences)

        assertThat(store.profileFor("gpt-6-luna").first()).isNull()
        store.remember("gpt-6-luna", learned)
        assertThat(store.profileFor("gpt-6-luna").first()).isEqualTo(learned)
    }

    /** Saving a model, or going back to the default, touches no remembered profile. */
    @Test
    fun `the model's own settings and the profiles leave each other alone`(@TempDir dir: File) = runTest {
        val preferences = dataStore(dir)
        val profiles = DataStoreRequestProfileStore(preferences)
        val settings = DataStoreAiSettingsStore(preferences, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) })

        settings.setModel("gpt-6-luna")
        profiles.remember("gpt-6-luna", learned)
        profiles.remember("gpt-4o", RequestProfile.DETERMINISTIC)
        settings.setModel(EstimatePrompt.DEFAULT_MODEL)
        settings.recordCall()

        assertThat(settings.settings.first().model).isEqualTo(EstimatePrompt.DEFAULT_MODEL)
        assertThat(settings.settings.first().usedToday).isEqualTo(1)
        assertThat(profiles.profileFor("gpt-6-luna").first()).isEqualTo(learned)
        assertThat(profiles.profileFor("gpt-4o").first()).isEqualTo(RequestProfile.DETERMINISTIC)
    }

    private fun dataStore(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "ai.preferences_pb") }
}
