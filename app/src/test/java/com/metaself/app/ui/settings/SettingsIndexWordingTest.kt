package com.metaself.app.ui.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.HealthRecordState
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.screen.settings.SettingsPage
import com.metaself.app.ui.screen.settings.SettingsUiState
import java.time.LocalDate
import org.junit.jupiter.api.Test

/**
 * The one status line under each row of the Settings index (D79). Every figure here is invented and
 * round: hours, reminder time, ratio, day counts, ceiling and problem counts alike.
 */
class SettingsIndexWordingTest {

    private val today = LocalDate.ofEpochDay(TEST_EPOCH_DAY)

    @Test
    fun `eating names the hours and the reminder time`() {
        val rule = WindowRule.Fixed(EatingWindow(10, 18, TEST_EPOCH_DAY))

        assertThat(SettingsIndexWording.eating(rule, Reminder(enabled = true, hour = 21, minute = 0)))
            .isEqualTo("10:00–18:00 · reminder at 21:00")
    }

    @Test
    fun `eating names a ratio by its figure`() {
        val rule = WindowRule.Measured(MeasuredWindow(14), fromEpochDay = TEST_EPOCH_DAY)

        assertThat(SettingsIndexWording.eating(rule, Reminder(enabled = false)))
            .isEqualTo("14/10 · reminder off")
    }

    @Test
    fun `eating with nothing set says so`() {
        assertThat(SettingsIndexWording.eating(null, Reminder(enabled = false)))
            .isEqualTo("No hours set · reminder off")
    }

    @Test
    fun `movement is on, with how far the health record reaches`() {
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 30))
            .isEqualTo("On · health record 30 days")
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 1))
            .isEqualTo("On · health record 1 day")
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 0)).isEqualTo("On")
    }

    @Test
    fun `movement off, or not available`() {
        assertThat(SettingsIndexWording.movement(StepAccess.NOT_PERMITTED, healthDays = 30))
            .isEqualTo("Off")
        assertThat(SettingsIndexWording.movement(StepAccess.UNAVAILABLE, healthDays = 0))
            .isEqualTo("Health Connect not available")
    }

    @Test
    fun `backups say where the copy goes and when the last one was`() {
        assertThat(SettingsIndexWording.backups(hasFolder = true, driveOn = true, lastBackup = today))
            .isEqualTo("Daily to a folder and to Drive · last copy 3 Sep")
        assertThat(SettingsIndexWording.backups(hasFolder = true, driveOn = false, lastBackup = null))
            .isEqualTo("Daily to a folder")
    }

    /** The date is the folder copy's; with no folder it would be a date about nothing on screen. */
    @Test
    fun `Drive alone has no folder date to show`() {
        assertThat(SettingsIndexWording.backups(hasFolder = false, driveOn = true, lastBackup = today))
            .isEqualTo("Daily to Drive")
    }

    @Test
    fun `no backup is not set up`() {
        assertThat(SettingsIndexWording.backups(hasFolder = false, driveOn = false, lastBackup = null))
            .isEqualTo("Not set up")
    }

    @Test
    fun `AI estimates says whether a key is saved, and the ceiling`() {
        assertThat(SettingsIndexWording.ai(hasKey = true, ceiling = 20))
            .isEqualTo("Key saved · up to 20 a day")
        assertThat(SettingsIndexWording.ai(hasKey = false, ceiling = 20)).isEqualTo("No key yet")
    }

    @Test
    fun `food database says whether an account is signed in`() {
        assertThat(SettingsIndexWording.foodDatabase(signedIn = true))
            .isEqualTo("Open Food Facts · signed in")
        assertThat(SettingsIndexWording.foodDatabase(signedIn = false)).isEqualTo("Not signed in")
    }

    @Test
    fun `problems are counted`() {
        assertThat(SettingsIndexWording.problems(3)).isEqualTo("3 recent")
        assertThat(SettingsIndexWording.problems(0)).isEqualTo("None")
    }

    @Test
    fun `movement says when some kinds are not allowed`() {
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 30, someNotAllowed = true))
            .isEqualTo("On · health record 30 days · some not allowed")
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 0, someNotAllowed = true))
            .isEqualTo("On · some not allowed")
    }

    /** The suffix says something about steps that ARE allowed; it has nothing to add when they are not. */
    @Test
    fun `not allowed makes no difference unless steps are granted`() {
        assertThat(SettingsIndexWording.movement(StepAccess.NOT_PERMITTED, healthDays = 0, someNotAllowed = true))
            .isEqualTo("Off")
        assertThat(
            SettingsIndexWording.movement(StepAccess.UNAVAILABLE, healthDays = 0, someNotAllowed = true),
        ).isEqualTo("Health Connect not available")
    }

    @Test
    fun `no row has a status before the page has loaded anything`() {
        SettingsPage.entries.forEach { page ->
            assertThat(SettingsIndexWording.statusOf(page, SettingsUiState())).isNull()
        }
    }

    @Test
    fun `eating has no status until the window has been read, even once loaded`() {
        val state = SettingsUiState(loaded = true, windowRead = false)

        assertThat(SettingsIndexWording.statusOf(SettingsPage.EATING, state)).isNull()
    }

    @Test
    fun `movement has no status until steps have been read, even once loaded`() {
        val state = SettingsUiState(loaded = true, stepsRead = false)

        assertThat(SettingsIndexWording.statusOf(SettingsPage.MOVEMENT, state)).isNull()
    }

    /** Backups, AI, Food database, Problems and the trainer's instructions need only [SettingsUiState.loaded]. */
    @Test
    fun `the other rows show as soon as the page has loaded`() {
        val state = SettingsUiState(loaded = true, windowRead = false, stepsRead = false, hasKey = true, dailyCeiling = 20)

        assertThat(SettingsIndexWording.statusOf(SettingsPage.BACKUPS, state)).isEqualTo("Not set up")
        assertThat(SettingsIndexWording.statusOf(SettingsPage.AI, state)).isEqualTo("Key saved · up to 20 a day")
        assertThat(SettingsIndexWording.statusOf(SettingsPage.FOOD_DATABASE, state)).isEqualTo("Not signed in")
        assertThat(SettingsIndexWording.statusOf(SettingsPage.PROBLEMS, state)).isEqualTo("None")
        assertThat(SettingsIndexWording.statusOf(SettingsPage.TRAINER_INSTRUCTIONS, state)).isEqualTo("A testing tool · stores nothing")
    }

    @Test
    fun `eating and movement show once each has been read`() {
        val rule = WindowRule.Fixed(EatingWindow(10, 18, TEST_EPOCH_DAY))
        val state = SettingsUiState(
            loaded = true,
            windowRead = true,
            windowRule = rule,
            reminder = Reminder(enabled = false),
            stepsRead = true,
            stepAccess = StepAccess.GRANTED,
            healthRecord = HealthRecordState(days = 5, notAllowed = setOf(HealthKind.HEART_RATE)),
        )

        assertThat(SettingsIndexWording.statusOf(SettingsPage.EATING, state))
            .isEqualTo("10:00–18:00 · reminder off")
        assertThat(SettingsIndexWording.statusOf(SettingsPage.MOVEMENT, state))
            .isEqualTo("On · health record 5 days · some not allowed")
    }
}
