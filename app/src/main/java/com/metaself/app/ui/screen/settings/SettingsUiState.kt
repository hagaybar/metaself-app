package com.metaself.app.ui.screen.settings

import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.ActionRefused
import java.time.LocalDate

/**
 * What the settings screen is showing.
 *
 * [hasKey] rather than the key itself: once saved, a key is never shown back. There is nothing the
 * owner can do with seeing it that he cannot do by pasting a new one, and a credential on screen is
 * a credential in a screenshot.
 */
data class SettingsUiState(
    val problems: List<String> = emptyList(),
    val hasKey: Boolean = false,
    val model: String = "",
    val dailyCeiling: Int = 0,
    val usedToday: Int = 0,
    val testing: Boolean = false,
    val testResult: String? = null,
    /** After a Test it that worked, what the saved model is sent (D57 §6). */
    val testLearned: String? = null,
    /** The one notification this app is allowed to send (D15). */
    val reminder: Reminder = Reminder(),
    /** What the last save or restore did, in counts. */
    val backupMessage: String? = null,
    /** A restore waiting to be agreed to, because it will destroy what is here. */
    val pendingRestore: String? = null,
    val busy: Boolean = false,
    /** Whether an Open Food Facts account is set. Never the password itself. */
    val offUsername: String = "",
    val hasOffPassword: Boolean = false,
    /** The folder automatic backups go to, and when the last one was written (D26). */
    val hasBackupFolder: Boolean = false,
    val lastBackup: LocalDate? = null,
    val automaticBackupMessage: String? = null,
    /** Whether steps can be read, and how far along the normal-day baseline is (D12). */
    val stepAccess: StepAccess = StepAccess.UNAVAILABLE,
    val hasStepNormal: Boolean = false,
    val stepDaysSoFar: Int = 0,
    val earliestStepDay: LocalDate? = null,
    val daysWithBandEnergy: Int = 0,
    /** The rule in force, of either kind, and how it has been going (D27, D29). */
    val windowRule: WindowRule? = null,
    val windowKept: Int = 0,
    val windowJudged: Int = 0,
    /** Whether the daily copy also goes to Drive, and what it last did. */
    val driveOn: Boolean = false,
    val driveMessage: String? = null,
    /** The last action here that threw rather than finishing, drawn under the part it came from. */
    val failed: SettingsRefusal? = null,
)

/** The parts of the settings page an action can be started from, each with its own title. */
enum class SettingsPart { WINDOW, OFF_ACCOUNT, BACKUP, REMINDER, KEY, MODEL, CEILING, TEST }

/** What [part] should say: that the action started there threw, and which of the sentences is true. */
data class SettingsRefusal(val part: SettingsPart, val refused: ActionRefused)
