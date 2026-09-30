package com.metaself.app.ui.settings

import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.day.ReminderWording
import com.metaself.app.ui.screen.settings.SettingsPage
import com.metaself.app.ui.screen.settings.SettingsUiState
import com.metaself.app.ui.window.WindowWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one status line under each row of the Settings index (D79).
 *
 * Counts and states, never reassurance — the rule the backup wording keeps: "Daily to a folder",
 * not a claim that everything is safe, which is something he cannot check from a line of text.
 */
object SettingsIndexWording {

    /** The rule in force, by its hours or its ratio, and the reminder. */
    fun eating(rule: WindowRule?, reminder: Reminder): String {
        val hours = when (rule) {
            null -> "No hours set"
            is WindowRule.Fixed -> String.format(
                Locale.US,
                "%02d:00–%02d:00",
                rule.window.startHour,
                rule.window.endHour,
            )
            is WindowRule.Measured -> WindowWording.ratio(rule.window)
        }
        val reminded = if (reminder.enabled) {
            "reminder at ${ReminderWording.clock(reminder)}"
        } else {
            "reminder off"
        }
        return "$hours · $reminded"
    }

    /**
     * Whether health data can be read, how many days the stored record holds, and — with
     * [someNotAllowed] — that some of the kinds Health Connect could offer are not among them, so an
     * "On" line never claims more than it does.
     */
    fun movement(access: StepAccess, healthDays: Int, someNotAllowed: Boolean = false): String {
        val base = when (access) {
            StepAccess.UNAVAILABLE -> "Health Connect not available"
            StepAccess.NOT_PERMITTED -> "Off"
            StepAccess.GRANTED -> when {
                healthDays <= 0 -> "On"
                healthDays == 1 -> "On · health record 1 day"
                else -> "On · health record $healthDays days"
            }
        }
        return if (access == StepAccess.GRANTED && someNotAllowed) "$base · some not allowed" else base
    }

    /**
     * Where the daily copy goes, and when the folder's last one was written. The date is the folder
     * copy's, so it is said only with a folder.
     */
    fun backups(hasFolder: Boolean, driveOn: Boolean, lastBackup: LocalDate?): String {
        val where = when {
            hasFolder && driveOn -> "Daily to a folder and to Drive"
            hasFolder -> "Daily to a folder"
            driveOn -> "Daily to Drive"
            else -> return "Not set up"
        }
        val last = lastBackup?.takeIf { hasFolder } ?: return where
        return "$where · last copy ${last.format(DAY)}"
    }

    fun ai(hasKey: Boolean, ceiling: Int): String =
        if (hasKey) "Key saved · up to $ceiling a day" else "No key yet"

    /** Signed in means a password is saved: the same test the Food database page makes. */
    fun foodDatabase(signedIn: Boolean): String =
        if (signedIn) "Open Food Facts · signed in" else "Not signed in"

    /** D106: nothing to count; what the page is, and that it keeps nothing. */
    const val TRAINER_INSTRUCTIONS = "A testing tool · stores nothing"

    fun problems(count: Int): String = if (count == 0) "None" else "$count recent"

    /**
     * The one line the index draws for [page], or null when there is nothing true yet to say.
     *
     * Null before [SettingsUiState.loaded]: that is the state a `StateFlow` starts in, before its
     * first real value, and every field it would be built from is still a made-up zero — "No hours
     * set", "Not set up" and the rest would all be lies about a page that has not been asked yet.
     * Eating waits further, for [SettingsUiState.windowRead], and Movement for
     * [SettingsUiState.stepsRead]: those two lines come from a read the page does for itself after it
     * opens, and are false exactly as long as the others until that read finishes.
     */
    fun statusOf(page: SettingsPage, state: SettingsUiState): String? {
        if (!state.loaded) return null
        return when (page) {
            SettingsPage.EATING ->
                if (!state.windowRead) null else eating(state.windowRule, state.reminder)

            SettingsPage.MOVEMENT -> if (!state.stepsRead) {
                null
            } else {
                movement(
                    access = state.stepAccess,
                    healthDays = state.healthRecord.days,
                    someNotAllowed = state.healthRecord.notAllowed.isNotEmpty(),
                )
            }

            SettingsPage.BACKUPS -> backups(state.hasBackupFolder, state.driveOn, state.lastBackup)
            SettingsPage.AI -> ai(state.hasKey, state.dailyCeiling)
            SettingsPage.FOOD_DATABASE -> foodDatabase(state.hasOffPassword)
            SettingsPage.PROBLEMS -> problems(state.problems.size)
            SettingsPage.TRAINER_INSTRUCTIONS -> TRAINER_INSTRUCTIONS
        }
    }

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
}
