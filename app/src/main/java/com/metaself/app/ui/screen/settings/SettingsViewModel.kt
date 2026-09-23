package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.ai.ApiKeyStore
import android.net.Uri
import com.metaself.app.data.backup.AutomaticBackup
import com.metaself.app.data.backup.BackupCodec
import com.metaself.app.data.backup.BackupFolder
import com.metaself.app.data.backup.BackupOutcome
import com.metaself.app.data.backup.BackupFiles
import com.metaself.app.data.backup.BackupRepository
import com.metaself.app.data.backup.RestoreResult
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.drive.DriveBackup
import com.metaself.app.data.drive.DriveOutcome
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.data.movement.StepSource
import com.metaself.app.domain.movement.NormalDay
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.domain.window.MealRead
import com.metaself.app.domain.window.WindowRules
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.backup.BackupSchedule
import com.metaself.app.ui.settings.AutomaticBackupWording
import java.time.LocalDate
import com.metaself.app.data.secret.SecretStore
import com.metaself.app.data.reminder.ReminderNotifier
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.domain.backup.Backup
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.ui.propose.ProposalWording
import com.metaself.app.ui.settings.BackupWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The key, the model, the daily ceiling, and one button that proves the whole chain works.
 *
 * The Test button is the ONLY verification the encrypted key store will ever get: no test on the
 * development machine can exercise the Android keystore. It therefore saves the key and then reads
 * it back through the store rather than using the value still in memory, or it would prove nothing.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keys: ApiKeyStore,
    private val settings: AiSettingsStore,
    private val estimator: MealEstimator,
    private val problems: ProblemLog,
    private val reminders: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val backups: BackupRepository,
    private val files: BackupFiles,
    private val secrets: SecretStore,
    private val profiles: ProfileRepository,
    private val backupFolder: BackupFolder,
    private val automaticBackup: AutomaticBackup,
    private val today: Today,
    /**
     * The moment, for the measured window's tally alone.
     *
     * A stretch is open until the fast completes, so how many of them have been judged depends on
     * what time it is now. Injected for the reason [Today] is: a clock read inside a calculation is
     * what makes the calculation untestable.
     */
    private val now: Now,
    private val steps: StepSource,
    private val meals: MealRepository,
    private val drive: DriveBackup,
) : ViewModel() {

    private val driveMessage = MutableStateFlow<String?>(null)

    /**
     * Google's own consent screen, waiting to be shown.
     *
     * A view model cannot start an activity, so the screen is handed up to something that can and
     * cleared once it has been. Shown once, the first time he turns Drive on.
     */
    private val _consent = MutableStateFlow<android.content.IntentSender?>(null)
    val consent: StateFlow<android.content.IntentSender?> = _consent.asStateFlow()

    fun consentShown() {
        _consent.value = null
    }

    private val windowState = MutableStateFlow(Triple<WindowRule?, Int, Int>(null, 0, 0))

    private data class Steps(
        val access: StepAccess = StepAccess.UNAVAILABLE,
        val hasNormal: Boolean = false,
        val daysSoFar: Int = 0,
        val earliest: LocalDate? = null,
        val daysWithEnergy: Int = 0,
    )

    private val stepState = MutableStateFlow(Steps())

    private val automaticMessage = MutableStateFlow<String?>(null)

    private val backupMessage = MutableStateFlow<String?>(null)
    private val pendingRestore = MutableStateFlow<PendingRestore?>(null)
    private val busy = MutableStateFlow(false)

    private val testing = MutableStateFlow(false)
    private val testResult = MutableStateFlow<String?>(null)
    private val problemLines = MutableStateFlow(readProblems())

    val state: StateFlow<SettingsUiState> = combine(
        keys.key,
        settings.settings,
        testing,
        testResult,
        problemLines,
    ) { key, aiSettings, isTesting, result, lines ->
        SettingsUiState(
            problems = lines,
            hasKey = !key.isNullOrBlank(),
            model = aiSettings.model,
            dailyCeiling = aiSettings.dailyCeiling,
            usedToday = aiSettings.usedToday,
            testing = isTesting,
            testResult = result,
        )
    }.combine(reminders.reminder) { current, reminder ->
        current.copy(reminder = reminder)
    }.combine(
        combine(
            secrets.watch(SecretStore.OFF_USERNAME),
            secrets.watch(SecretStore.OFF_PASSWORD),
        ) { username, password -> username.orEmpty() to !password.isNullOrBlank() },
    ) { current, (username, hasPassword) ->
        current.copy(offUsername = username, hasOffPassword = hasPassword)
    }.combine(
        combine(profiles.driveBackupOn, driveMessage) { on, message -> on to message },
    ) { current, (on, message) ->
        current.copy(driveOn = on, driveMessage = message)
    }.combine(windowState) { current, (rule, kept, judged) ->
        current.copy(windowRule = rule, windowKept = kept, windowJudged = judged)
    }.combine(stepState) { current, steps ->
        current.copy(
            stepAccess = steps.access,
            hasStepNormal = steps.hasNormal,
            stepDaysSoFar = steps.daysSoFar,
            earliestStepDay = steps.earliest,
            daysWithBandEnergy = steps.daysWithEnergy,
        )
    }.combine(
        combine(
            profiles.backupFolderUri,
            profiles.lastBackupEpochDay,
            automaticMessage,
        ) { folder, lastDay, message ->
            Triple(folder != null, lastDay?.let(LocalDate::ofEpochDay), message)
        },
    ) { current, (hasFolder, lastDay, message) ->
        current.copy(
            hasBackupFolder = hasFolder,
            lastBackup = lastDay,
            automaticBackupMessage = message,
        )
    }.combine(
        combine(backupMessage, pendingRestore, busy) { message, pending, working ->
            Triple(message, pending, working)
        },
    ) { current, (message, pending, working) ->
        current.copy(
            backupMessage = message,
            pendingRestore = pending?.question,
            busy = working,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    fun saveKey(key: String) {
        viewModelScope.launch {
            keys.save(key)
            testResult.value = null
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            keys.clear()
            testResult.value = null
        }
    }

    fun setModel(model: String) {
        viewModelScope.launch { settings.setModel(model) }
    }

    fun setDailyCeiling(ceiling: Int) {
        viewModelScope.launch { settings.setDailyCeiling(ceiling) }
    }

    /**
     * Turn the reminder on or off, or move it.
     *
     * The alarm is set from here rather than from a flow, so that switching it off cancels it at
     * the moment the owner switches it off. Off means off: there is no other notification in this
     * app and nothing that re-enables itself.
     */
    fun setReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminders.save(reminder)
            if (reminder.enabled) scheduler.schedule(reminder) else scheduler.cancel()
        }
    }

    /**
     * The owner's Open Food Facts account, so he can send products up (D24).
     *
     * Stored exactly as the API key is: encrypted against the phone's keystore, excluded from
     * Android's backup, and never shown back. Saved together, cleared together — half an account is
     * an account that fails at the moment it is needed.
     */
    fun saveOffAccount(username: String, password: String) {
        viewModelScope.launch {
            secrets.save(SecretStore.OFF_USERNAME, username)
            secrets.save(SecretStore.OFF_PASSWORD, password)
        }
    }

    fun clearOffAccount() {
        viewModelScope.launch {
            secrets.clear(SecretStore.OFF_USERNAME, SecretStore.OFF_PASSWORD)
        }
    }

    /**
     * The window in force, and how the days it governed have gone.
     *
     * Counted from the record every time this screen opens, the way the streak is (D13), so a day
     * filled in late repairs the count and nothing can drift out of step with what he ate.
     */
    fun refreshWindow() {
        viewModelScope.launch {
            val rules = profiles.windowRules.first()
            val todayEpochDay = today().toEpochDay()
            val inForce = WindowRules.inForceOn(rules, todayEpochDay)
            if (inForce == null) {
                windowState.value = Triple(null, 0, 0)
                return@launch
            }

            // The later of the rule's own start day and the start of the fortnight. The gate can
            // only ever move a rule's start day FORWARD, never back, so a rule still never applies
            // backwards: `maxOf` of the rule's day and a day inside the fortnight cannot be earlier
            // than the rule's day. There is no view-model test for this — `SettingsViewModel` takes
            // `SecretStore`, whose constructor opens EncryptedSharedPreferences against the Android
            // keystore, which neither JUnit 5 nor Robolectric can provide on this box, which is why
            // this class has never had one. It is checked by reading, and by the settings render
            // test over the state this produces.
            val from = maxOf(inForce.fromEpochDay, todayEpochDay - COUNTED_DAYS)
            val zone = ZoneId.systemDefault()

            // Two kinds, two units. The fixed hours are a statement about a calendar day and keep
            // their day tally; a ratio is a statement about hours and counts eating stretches
            // bounded by the fast (design §3.1). The screen says which it is in as many words, so
            // the number is never read in the wrong unit.
            val (kept, judged) = when (inForce) {
                is WindowRule.Fixed -> WindowRules.daysKept(
                    rules = rules,
                    mealsByDay = (from..todayEpochDay).associateWith { day ->
                        meals.observeDay(day).first()
                    },
                    fromEpochDay = from,
                    toEpochDay = todayEpochDay,
                    zone = zone,
                    nowMillis = now(),
                )

                // Since the day the ratio was set, and no fortnight (D32), to agree with the day
                // screen's "Kept 10 of 14 stretches since 3 Sep". Two days further back than that,
                // because the walk has to see the input before the first one to know whether it
                // OPENS a stretch; the rule's own gate keeps anything begun earlier out.
                is WindowRule.Measured -> WindowRules.stretchesKept(
                    rule = inForce,
                    meals = (inForce.fromEpochDay - LEAD_IN_DAYS..todayEpochDay).flatMap { day ->
                        meals.observeDay(day).first()
                    },
                    // The days read, said out loud: a stretch still running when the read stopped
                    // is left open rather than counted as a kept or a broken one.
                    read = MealRead.OverDays(inForce.fromEpochDay - LEAD_IN_DAYS, todayEpochDay),
                    zone = zone,
                    nowMillis = now(),
                )
            }
            windowState.value = Triple(inForce, kept, judged)
        }
    }

    /** Set it, from today. It never reaches backwards; that is the rule (D27). */
    fun setEatingWindow(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            profiles.addWindowRule(
                WindowRule.Fixed(
                    EatingWindow(startHour, endHour, fromEpochDay = today().toEpochDay()),
                ),
            )
            refreshWindow()
        }
    }

    /**
     * Set a ratio instead, also from today.
     *
     * Written fasting first: 16 here means sixteen hours fasting and eight eating (D29). It reaches
     * backwards no further than the hours do — same day, same gate — and saving one today replaces
     * whichever kind was set today, which is the owner choosing which he is keeping.
     */
    fun setMeasuredWindow(fastingHours: Int) {
        viewModelScope.launch {
            profiles.addWindowRule(
                WindowRule.Measured(
                    window = MeasuredWindow(fastingHours),
                    fromEpochDay = today().toEpochDay(),
                ),
            )
            refreshWindow()
        }
    }

    fun clearEatingWindow() {
        viewModelScope.launch {
            profiles.clearWindowRules()
            refreshWindow()
        }
    }

    /**
     * What the step reader can currently see.
     *
     * Called when the screen opens and again after he grants permission, so the line on screen is
     * about the phone as it is now rather than as it was when the app started.
     */
    fun refreshSteps() {
        viewModelScope.launch {
            val access = steps.access()
            if (access != StepAccess.GRANTED) {
                stepState.value = Steps(access = access)
                return@launch
            }

            val todayDate = today()
            val history = steps.history(
                from = todayDate.minusDays(NormalDay.WINDOW_DAYS.toLong()),
                to = todayDate,
            )
            val before = history.filter { it.epochDay < todayDate.toEpochDay() }
            stepState.value = Steps(
                access = access,
                hasNormal = NormalDay.steps(history, todayDate.toEpochDay()) != null,
                daysSoFar = before.size,
                earliest = before.minByOrNull { it.epochDay }
                    ?.let { LocalDate.ofEpochDay(it.epochDay) },
                daysWithEnergy = history.count { it.activeKcal != null },
            )
        }
    }

    /**
     * Keep the folder the owner picked, with a permission that survives a reboot.
     *
     * Without the persistable grant, automatic backup would work exactly once and then quietly
     * stop, which is worse than not offering it — he would believe he had a copy.
     */
    fun useBackupFolder(uri: Uri) {
        viewModelScope.launch {
            if (!backupFolder.remember(uri)) {
                automaticMessage.value = AutomaticBackupWording.outcome(
                    BackupOutcome.Failed("the folder could not be kept"),
                )
                return@launch
            }
            profiles.saveBackupFolder(uri.toString())
            // Written straight away rather than waiting for tomorrow: he has just asked for this,
            // and a feature that appears to do nothing for a day looks broken.
            backUpNow()
        }
    }

    fun forgetBackupFolder() {
        viewModelScope.launch {
            profiles.saveBackupFolder(null)
            automaticMessage.value = null
        }
    }

    /**
     * Turn the Drive copy on or off, and write one immediately when turning it on.
     *
     * Written straight away for the same reason the folder is: he has just asked for this, and a
     * feature that appears to do nothing until tomorrow looks broken. It is also the only way he
     * can see the consent screen, which Google shows once.
     */
    fun setDriveBackup(on: Boolean) {
        viewModelScope.launch {
            profiles.setDriveBackup(on)
            driveMessage.value = null
            if (on) driveNow()
        }
    }

    /**
     * Write to Drive now. Also how the first consent screen gets asked for.
     *
     * @param afterConsent true when this is the retry that follows Google's screen. **If consent is
     *   still wanted at that point, it is NOT asked for again.** The first version retried
     *   unconditionally, so an account that could never be granted produced an endless loop — the
     *   picker appearing, being answered, and appearing again — with the real reason never reaching
     *   the screen or the problem log, because nothing ever gave up long enough to report it.
     */
    fun driveNow(afterConsent: Boolean = false) {
        viewModelScope.launch {
            val outcome = drive.write(today(), System.currentTimeMillis())

            if (outcome is DriveOutcome.NeedsConsent) {
                if (afterConsent) {
                    problems.record(
                        kind = "drive",
                        detail = "consent was answered and Drive still refused: the OAuth client " +
                            "does not match this app, or the Drive API is not enabled",
                    )
                    driveMessage.value = AutomaticBackupWording.CONSENT_DID_NOT_TAKE
                } else {
                    _consent.value = outcome.request
                }
                return@launch
            }

            driveMessage.value = AutomaticBackupWording.drive(outcome)
        }
    }

    /** He closed Google's screen without agreeing. Said plainly rather than retried. */
    fun consentDeclined() {
        driveMessage.value = AutomaticBackupWording.CONSENT_DECLINED
    }

    /** Take the copy now, so he can see it work rather than take it on trust. */
    fun backUpNow() {
        viewModelScope.launch {
            val folderUri = profiles.backupFolderUri.first()
            if (folderUri == null) {
                automaticMessage.value = null
                return@launch
            }
            val backup = backups.export(System.currentTimeMillis())
            val outcome = backupFolder.write(
                folderUri = Uri.parse(folderUri),
                fileName = BackupSchedule.fileNameFor(today().toString()),
                contents = BackupCodec.encode(backup),
            )
            if (outcome is BackupOutcome.Written) {
                profiles.saveLastBackupDay(today().toEpochDay())
            }
            automaticMessage.value = AutomaticBackupWording.outcome(outcome)
        }
    }

    /** Write everything to the file the owner picked. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            backupMessage.value = null
            val backup = backups.export(System.currentTimeMillis())
            val written = files.write(uri, BackupCodec.encode(backup))
            backupMessage.value = if (written) {
                BackupWording.saved(
                    RestoreResult(backup.meals.size, backup.weights.size, backup.profile != null),
                ) + " " + BackupWording.KEY_NOT_INCLUDED
            } else {
                BackupWording.COULD_NOT_WRITE
            }
            busy.value = false
        }
    }

    /**
     * Read the file and ask before doing anything with it.
     *
     * Nothing is written here. A restore replaces what is on the phone, so the file is read, both
     * sides are counted, and the owner is told what he is about to lose before he agrees to it.
     */
    fun offerRestoreFrom(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            backupMessage.value = null
            pendingRestore.value = null

            val backup = files.read(uri)?.let { BackupCodec.decode(it) }
            if (backup == null) {
                backupMessage.value = BackupWording.UNREADABLE
                busy.value = false
                return@launch
            }

            val here = backups.whatIsHere()
            pendingRestore.value = PendingRestore(
                backup = backup,
                question = BackupWording.confirmReplacing(
                    here = here,
                    incoming = RestoreResult(
                        backup.meals.size,
                        backup.weights.size,
                        backup.profile != null,
                    ),
                ),
            )
            busy.value = false
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore.value ?: return
        viewModelScope.launch {
            busy.value = true
            pendingRestore.value = null
            val result = backups.restore(pending.backup)
            backupMessage.value =
                BackupWording.restored(result) + " " + BackupWording.KEY_NOT_INCLUDED
            busy.value = false
        }
    }

    fun cancelRestore() {
        pendingRestore.value = null
    }

    fun dismissBackupMessage() {
        backupMessage.value = null
    }

    private data class PendingRestore(val backup: Backup, val question: String)

    /**
     * Post the reminder now, so it can be checked without waiting for a day with nothing in it.
     *
     * The same notification, on the same channel, triggered by the owner rather than by the clock —
     * it does not become a second notification and D15 stands. It exists for exactly the reason the
     * API key's Test button exists: no test on the build machine can prove that a notification
     * arrives on a phone, so the only verification available is the owner pressing something and
     * looking.
     */
    fun sendReminderNow() {
        notifier.postDailyReminderNow()
    }

    /**
     * Send the smallest real request there is and report what came back.
     *
     * "one apple" rather than a ping, because a ping proves the network and this must prove the
     * whole chain: the key was stored, the key was read back, the request was accepted, and the
     * reply was in the shape this app demands.
     */
    fun test() {
        viewModelScope.launch {
            testing.value = true
            testResult.value = null
            val result = estimator.estimate("one apple")
            problemLines.value = readProblems()
            testResult.value = when (result) {
                is EstimateResult.Proposed -> "Connected. It answered with " +
                    "${result.proposal.items.size} item and " +
                    "${result.proposal.totalKcal} kcal."

                else -> ProposalWording.failure(result)
            }
            testing.value = false
        }
    }

    /** Everything the log holds, as one block the owner can copy in one go. */
    fun problemsAsText(): String = problems.recent()
        .joinToString(System.lineSeparator()) { "${format(it.whenMillis)}  ${it.kind}: ${it.detail}" }

    fun clearProblems() {
        problems.clear()
        problemLines.value = readProblems()
    }

    private fun readProblems(): List<String> = problems.recent()
        .map { "${format(it.whenMillis)}  ${it.kind}: ${it.detail}" }

    private fun format(millis: Long): String = DateTimeFormatter
        .ofPattern("d MMM HH:mm", Locale.UK)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        /** A fortnight is enough to see whether a decision is holding, and short enough to matter. */
        const val COUNTED_DAYS = 13L

        /**
         * How far past the start of the counted span the measured window has to read.
         *
         * Two calendar days, the reasoning being [WindowRules.stretches]'s: a fast is at most
         * twenty-three hours, so an input more than a day before the first one of a range has
         * already closed whatever stretch it was in. It buys knowing whether the range's first
         * input opens a stretch or continues one begun the evening before.
         */
        const val LEAD_IN_DAYS = 2L

        const val STOP_TIMEOUT_MS = 5_000L
    }
}
