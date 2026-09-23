package com.metaself.app.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.DataStoreAiSettingsStore
import com.metaself.app.data.profile.DataStoreProfileRepository
import com.metaself.app.data.reminder.DataStoreReminderStore
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.target.TargetRevision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

/**
 * The settings half of a restore, put back — against a real DataStore, the one file the profile,
 * the AI settings and the reminder share.
 *
 * Every figure here is invented.
 */
class DataStoreSettingsSnapshotTest {

    @Test
    fun `everything a restore writes is undone, including keys that were not there`(
        @TempDir dir: File,
    ) = runTest {
        val store = dataStore(dir)
        val profiles = DataStoreProfileRepository(store)
        val ai = DataStoreAiSettingsStore(store, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) })
        val reminders = DataStoreReminderStore(store)
        profiles.save(aProfile())
        profiles.saveRevision(TargetRevision(TEST_EPOCH_DAY - 7, 80.0, 2_000, 2_100))
        ai.setModel("model-before")
        val before = store.data.first()

        val putBack = DataStoreSettingsSnapshot(store).take()
        // What a restore writes: every one of these either replaces a value or adds one that was
        // absent, and one of them — the seen flag — cannot be un-set through its own setter.
        profiles.save(aProfile(weightKg = 90.0))
        profiles.saveRevision(TargetRevision(TEST_EPOCH_DAY, 90.0, 2_400, 2_500))
        profiles.markRevisionSeen()
        profiles.saveArrival(GoalArrival(targetKg = 75.0, epochDay = TEST_EPOCH_DAY))
        profiles.recordMilestones(mapOf(Milestone.firstKg to TEST_EPOCH_DAY))
        ai.setModel("model-from-the-file")
        ai.setDailyCeiling(10)
        reminders.save(Reminder(enabled = true, hour = 7, minute = 30))

        putBack()

        assertThat(store.data.first().asMap()).isEqualTo(before.asMap())
        assertThat(profiles.profile.first()).isEqualTo(aProfile())
        assertThat(profiles.revisionSeen.first()).isFalse()
        assertThat(profiles.arrival.first()).isNull()
        assertThat(profiles.milestones.first()).isEmpty()
        assertThat(ai.settings.first().model).isEqualTo("model-before")
        assertThat(reminders.current()).isEqualTo(Reminder())
    }

    @Test
    fun `an empty store is put back empty`(@TempDir dir: File) = runTest {
        val store = dataStore(dir)
        val putBack = DataStoreSettingsSnapshot(store).take()

        DataStoreProfileRepository(store).save(aProfile())
        putBack()

        assertThat(store.data.first().asMap()).isEmpty()
    }

    private fun dataStore(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "profile.preferences_pb") }
}
