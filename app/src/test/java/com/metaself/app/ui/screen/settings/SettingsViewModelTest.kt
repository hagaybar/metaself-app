package com.metaself.app.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.InMemoryLetterSettingsStore
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.ai.ApiKeyStore
import com.metaself.app.data.backup.AutomaticBackup
import com.metaself.app.data.backup.BackupCodec
import com.metaself.app.data.backup.BackupFiles
import com.metaself.app.data.backup.BackupFolder
import com.metaself.app.data.backup.BackupRepository
import com.metaself.app.data.backup.SettingsSnapshot
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.day.MealDao
import com.metaself.app.data.drive.DriveAccess
import com.metaself.app.data.drive.DriveBackup
import com.metaself.app.data.drive.DriveHttp
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.health.ArchiveRestore
import com.metaself.app.data.health.ArchiveWrite
import com.metaself.app.data.health.HealthBookkeepingDao
import com.metaself.app.data.health.HealthDayDao
import com.metaself.app.data.health.MovementCorrectionDao
import com.metaself.app.data.health.ReadingsArchive
import com.metaself.app.data.health.SleepDao
import com.metaself.app.data.health.SessionSplitDao
import com.metaself.app.data.health.WorkoutDao
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.data.movement.StepSource
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.reminder.ReminderNotifier
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.data.trainer.TrainerDao
import com.metaself.app.data.weight.WeightDao
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.aProposal
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.movement.MovementWording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * An action on the settings page that throws says so, under the part it was started from, and the
 * app stays up.
 *
 * Robolectric, because the backup folder and Drive take the application's context — neither is
 * touched by these tests, but both have to be built. JUnit 4 for that reason: `org.junit.Test`, never
 * `org.junit.jupiter.api.Test`. An exception that escaped the guard would fail [runTest] on the test
 * Main dispatcher, so each test here is also the proof that the net holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val problems = RecordingProblemLog()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a key that cannot be saved says so under the key, and the problem is on the page`() = runTest {
        val viewModel = viewModel(keys = Keys(failing = true))
        watch(viewModel)

        viewModel.saveKey("sk-invented")
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.KEY, ActionRefused.NOTHING_CHANGED))
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
        // The sentence points at Recent problems, which is on this same page.
        assertThat(viewModel.state.value.problems.single()).contains("refused: ")
    }

    @Test
    fun `a model or a ceiling that cannot be saved says so under its own field`() = runTest {
        val viewModel = viewModel(ai = Ai(failing = true))
        watch(viewModel)

        viewModel.setModel("an-invented-model")
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed?.part).isEqualTo(SettingsPart.MODEL)

        viewModel.setDailyCeiling(12)
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.CEILING, ActionRefused.NOTHING_CHANGED))
    }

    /** Saved, then the alarm: the setting stands even though the alarm threw, and it says so. */
    @Test
    fun `a reminder whose alarm cannot be set says it may have partly happened`() = runTest {
        val reminders = Reminders()
        val viewModel = viewModel(reminders = reminders, scheduler = Scheduler(failing = true))
        watch(viewModel)

        viewModel.setReminder(Reminder(enabled = true, hour = 20, minute = 0))
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.REMINDER, ActionRefused.MAYBE_PARTIAL))
        assertThat(reminders.current().enabled).isTrue()
    }

    /** Two secrets written one by one, so half an account is possible; clearing is one write. */
    @Test
    fun `the food database account, refused, says which is true`() = runTest {
        val account = Account(failing = true)
        val viewModel = viewModel(account = account)
        watch(viewModel)

        viewModel.saveOffAccount("someone", "invented")
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.OFF_ACCOUNT, ActionRefused.MAYBE_PARTIAL))

        viewModel.clearOffAccount()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.OFF_ACCOUNT, ActionRefused.NOTHING_CHANGED))
    }

    @Test
    fun `a window that cannot be set or cleared says so under the window`() = runTest {
        val profiles = object : ProfileRepository by FakeProfileRepository(aProfile()) {
            override suspend fun addWindowRule(rule: WindowRule) = throw IllegalStateException("disk full")
            override suspend fun clearWindowRules() = throw IllegalStateException("disk full")
        }
        val viewModel = viewModel(profiles = profiles)
        watch(viewModel)

        viewModel.setEatingWindow(9, 19)
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.WINDOW, ActionRefused.NOTHING_CHANGED))

        viewModel.setMeasuredWindow(16)
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed?.part).isEqualTo(SettingsPart.WINDOW)

        viewModel.clearEatingWindow()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed?.part).isEqualTo(SettingsPart.WINDOW)
        assertThat(problems.recorded).hasSize(3)
    }

    /** Nobody asked for these; they are written down, and nothing is said. */
    @Test
    fun `the page's own reads, refused, are recorded and say nothing`() = runTest {
        val profiles = object : ProfileRepository by FakeProfileRepository(aProfile()) {
            override val windowRules: Flow<List<WindowRule>> =
                flow { throw IllegalStateException("unreadable") }
        }
        val viewModel = viewModel(profiles = profiles, steps = Steps(failing = true))
        watch(viewModel)

        viewModel.refreshWindow()
        viewModel.refreshSteps()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed).isNull()
        assertThat(problems.recorded.map { it.kind }).containsExactly("refused", "refused")
        assertThat(viewModel.state.value.problems).hasSize(2)
        // A refused read still finished: the index must stop hiding the row's status behind it.
        assertThat(viewModel.state.value.windowRead).isTrue()
        assertThat(viewModel.state.value.stepsRead).isTrue()
    }

    /**
     * `loaded` is true as soon as the page's state has combined once, well before either of its own
     * reads has run — those are asked for separately, by the screen, once it is on screen.
     */
    @Test
    fun `loaded is true from the first state, before the window or steps have been read`() = runTest {
        val viewModel = viewModel()
        watch(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.state.value.loaded).isTrue()
        assertThat(viewModel.state.value.windowRead).isFalse()
        assertThat(viewModel.state.value.stepsRead).isFalse()
    }

    /** The ordinary case: both reads succeed, and both mark themselves read. */
    @Test
    fun `a window and a step read that succeed are marked read too`() = runTest {
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.refreshWindow()
        viewModel.refreshSteps()
        advanceUntilIdle()

        assertThat(viewModel.state.value.windowRead).isTrue()
        assertThat(viewModel.state.value.stepsRead).isTrue()
    }

    /**
     * The band's calories are counted over the same days as the days they are "of": the days before
     * today. Today counted in one and not the other could claim one day more than there were. Every
     * figure is invented.
     */
    @Test
    fun `the band's calorie days are counted over the days before today, as the days seen are`() = runTest {
        val days = (TEST_EPOCH_DAY - 30L..TEST_EPOCH_DAY.toLong()).map {
            DayMovement(epochDay = it, steps = 5_000, activeKcal = 300)
        }
        val viewModel = viewModel(steps = Steps(days = days))
        watch(viewModel)

        viewModel.refreshSteps()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.stepDaysSoFar).isEqualTo(30)
        assertThat(state.daysWithBandEnergy).isEqualTo(30)
        assertThat(MovementWording.bandEnergy(state.daysWithBandEnergy, state.stepDaysSoFar))
            .startsWith("Your band reported calories on 30 of the last 30 days")
    }

    @Test
    fun `forgetting the folder or switching Drive off, refused, says so under the backup`() = runTest {
        val profiles = object : ProfileRepository by FakeProfileRepository(aProfile()) {
            override suspend fun saveBackupFolder(uri: String?) = throw IllegalStateException("disk full")
            override suspend fun setDriveBackup(on: Boolean) = throw IllegalStateException("disk full")
        }
        val viewModel = viewModel(profiles = profiles)
        watch(viewModel)

        viewModel.forgetBackupFolder()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.NOTHING_CHANGED))

        viewModel.setDriveBackup(false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.NOTHING_CHANGED))
    }

    /** The copy is written before the day is noted, so one that throws may have left a copy. */
    @Test
    fun `a copy to the folder that throws says it may have partly happened`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        profiles.saveBackupFolder("content://invented/folder")
        val viewModel = viewModel(profiles = profiles, daos = Daos(failing = setOf("allMeals")))
        watch(viewModel)

        viewModel.backUpNow()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.MAYBE_PARTIAL))
    }

    /** Gathering the record throws before anything is written; the buttons come back. */
    @Test
    fun `an export that throws changed nothing and gives the buttons back`() = runTest {
        val viewModel = viewModel(daos = Daos(failing = setOf("allMeals")))
        watch(viewModel)

        viewModel.exportTo(Uri.parse("content://invented/file"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.NOTHING_CHANGED))
        assertThat(viewModel.state.value.busy).isFalse()
    }

    @Test
    fun `a restore that cannot count what is here could not be opened, and asks nothing`() = runTest {
        val files = Files(contents = BackupCodec.encode(backups(Daos()).export(0)))
        val viewModel = viewModel(files = files, daos = Daos(failing = setOf("allMeals")))
        watch(viewModel)

        viewModel.offerRestoreFrom(Uri.parse("content://invented/file"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.COULD_NOT_OPEN))
        assertThat(viewModel.state.value.pendingRestore).isNull()
        assertThat(viewModel.state.value.busy).isFalse()
    }

    /**
     * A restore is all or nothing: one that throws rolled the database back and put the settings
     * back, so it says nothing was changed.
     */
    @Test
    fun `a restore that throws says nothing was changed and gives the buttons back`() = runTest {
        val files = Files(contents = BackupCodec.encode(backups(Daos()).export(0)))
        val viewModel = viewModel(files = files, daos = Daos(failing = setOf("deleteAllMeals")))
        watch(viewModel)
        viewModel.offerRestoreFrom(Uri.parse("content://invented/file"))
        advanceUntilIdle()
        assertThat(viewModel.state.value.pendingRestore).isNotNull()

        viewModel.confirmRestore()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.NOTHING_CHANGED))
        assertThat(viewModel.state.value.busy).isFalse()
        assertThat(viewModel.state.value.pendingRestore).isNull()
    }

    /** Only when the settings could not be put back either may part of it have happened. */
    @Test
    fun `a restore whose settings could not be put back says it may have partly happened`() = runTest {
        val files = Files(contents = BackupCodec.encode(backups(Daos()).export(0)))
        val viewModel = viewModel(
            files = files,
            daos = Daos(failing = setOf("deleteAllMeals")),
            snapshot = { { throw IllegalStateException("disk full") } },
        )
        watch(viewModel)
        viewModel.offerRestoreFrom(Uri.parse("content://invented/file"))
        advanceUntilIdle()

        viewModel.confirmRestore()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.MAYBE_PARTIAL))
        assertThat(viewModel.state.value.busy).isFalse()
    }

    /** D71: the daily file has no raw readings, so Drive's months are offered after a restore. */
    @Test
    fun `after a restore with Drive on, the months in Drive are offered`() = runTest {
        val archive = Archive(months = 3)
        val viewModel = restoredWith(archive, driveOn = true)

        assertThat(viewModel.state.value.pendingArchive)
            .isEqualTo("Also bring back 3 months of detailed readings from Drive?")
        assertThat(archive.calls).containsExactly("count")
    }

    @Test
    fun `after a restore with Drive off, nothing is offered and Drive is not asked`() = runTest {
        val archive = Archive(months = 3)
        val viewModel = restoredWith(archive, driveOn = false)

        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(archive.calls).isEmpty()
    }

    @Test
    fun `no months in Drive, or no answer, and nothing is offered`() = runTest {
        assertThat(restoredWith(Archive(months = 0), driveOn = true).state.value.pendingArchive).isNull()
        assertThat(restoredWith(Archive(months = null), driveOn = true).state.value.pendingArchive).isNull()
    }

    @Test
    fun `bringing the months back says what came back in numbers`() = runTest {
        val archive = Archive(
            months = 2,
            result = ArchiveRestore(months = 2, readings = 1_200, unreadable = 0, unreachable = 0),
        )
        val viewModel = restoredWith(archive, driveOn = true)

        viewModel.confirmArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.backupMessage).isEqualTo("Brought back 2 months: 1,200 readings.")
        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(viewModel.state.value.busy).isFalse()
        assertThat(archive.calls).containsExactly("count", "restore").inOrder()
    }

    @Test
    fun `Drive not answering when bringing them back says so`() = runTest {
        val viewModel = restoredWith(Archive(months = 2, result = null), driveOn = true)

        viewModel.confirmArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.backupMessage)
            .isEqualTo("Drive could not be reached; the detailed readings were not brought back.")
        assertThat(viewModel.state.value.busy).isFalse()
    }

    @Test
    fun `no brings nothing back`() = runTest {
        val archive = Archive(months = 2)
        val viewModel = restoredWith(archive, driveOn = true)

        viewModel.cancelArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(archive.calls).doesNotContain("restore")
    }

    /**
     * A way back besides the offer after a restore: asked from the Drive controls on Backups, the
     * same question is put there, and what came of it is said there too.
     */
    @Test
    fun `asking from the Drive controls puts the same question there`() = runTest {
        val archive = Archive(
            months = 3,
            result = ArchiveRestore(months = 3, readings = 1_200, unreadable = 0, unreachable = 0),
        )
        val viewModel = viewModel(archive = archive)
        watch(viewModel)

        viewModel.offerArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingArchive)
            .isEqualTo("Also bring back 3 months of detailed readings from Drive?")
        assertThat(viewModel.state.value.archiveFromDrive).isTrue()

        viewModel.confirmArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.archiveMessage).isEqualTo("Brought back 3 months: 1,200 readings.")
        assertThat(viewModel.state.value.backupMessage).isNull()
        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(archive.calls).containsExactly("count", "restore").inOrder()
    }

    @Test
    fun `asking from the Drive controls with no months in Drive says so and asks nothing`() = runTest {
        val viewModel = viewModel(archive = Archive(months = 0))
        watch(viewModel)

        viewModel.offerArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(viewModel.state.value.archiveMessage).isEqualTo("No detailed readings were found in Drive.")
    }

    @Test
    fun `asking from the Drive controls with Drive not answering says so and asks nothing`() = runTest {
        val viewModel = viewModel(archive = Archive(months = null))
        watch(viewModel)

        viewModel.offerArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(viewModel.state.value.archiveMessage)
            .isEqualTo("Drive could not be reached; the detailed readings were not brought back.")
    }

    /** A stale offer from an earlier ask must not sit alongside a fresh answer that replaces it. */
    @Test
    fun `asking again clears a stale offer before a fresh answer replaces it`() = runTest {
        val archive = Archive(months = 3)
        val viewModel = viewModel(archive = archive)
        watch(viewModel)

        viewModel.offerArchive()
        advanceUntilIdle()
        assertThat(viewModel.state.value.pendingArchive).isNotNull()

        archive.months = null
        viewModel.offerArchive()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingArchive).isNull()
        assertThat(viewModel.state.value.archiveMessage)
            .isEqualTo("Drive could not be reached; the detailed readings were not brought back.")
    }

    /** The offer after a restore stays with the restore, under Backup. */
    @Test
    fun `the offer after a restore is not put with the Drive controls`() = runTest {
        val viewModel = restoredWith(Archive(months = 2), driveOn = true)

        assertThat(viewModel.state.value.pendingArchive).isNotNull()
        assertThat(viewModel.state.value.archiveFromDrive).isFalse()
    }

    @Test
    fun `a test that throws says so under the button and gives the button back`() = runTest {
        val viewModel = viewModel(estimator = Throws())
        watch(viewModel)

        viewModel.test()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed)
            .isEqualTo(SettingsRefusal(SettingsPart.TEST, ActionRefused.NOTHING_CHANGED))
        assertThat(viewModel.state.value.testing).isFalse()
    }

    /** D57 §6: after an answer, one line says what the saved model is now sent. */
    @Test
    fun `a test that worked says how the answered request was sent, as the answer carried it`() = runTest {
        val viewModel = viewModel(estimator = Answers(SENT_AS))
        watch(viewModel)

        viewModel.test()
        advanceUntilIdle()

        assertThat(viewModel.state.value.testLearned).isEqualTo(SENT_AS)
    }

    @Test
    fun `an answer that does not say how it was sent adds no line`() = runTest {
        val viewModel = viewModel(estimator = Answers(sentAs = null))
        watch(viewModel)

        viewModel.test()
        advanceUntilIdle()

        assertThat(viewModel.state.value.testResult).startsWith("Connected.")
        assertThat(viewModel.state.value.testLearned).isNull()
    }

    @Test
    fun `a test that failed says nothing about what the model is sent`() = runTest {
        val viewModel = viewModel(estimator = Throws())
        watch(viewModel)

        viewModel.test()
        advanceUntilIdle()

        assertThat(viewModel.state.value.testLearned).isNull()
    }

    /** What is on screen is always about the latest thing he did. */
    @Test
    fun `the failure goes when dismissed, and when the next action starts`() = runTest {
        val keys = Keys(failing = true)
        val viewModel = viewModel(keys = keys)
        watch(viewModel)
        viewModel.saveKey("sk-invented")
        advanceUntilIdle()

        viewModel.dismissFailure()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed).isNull()

        viewModel.saveKey("sk-invented")
        advanceUntilIdle()
        keys.failing = false
        viewModel.saveKey("sk-invented")
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed).isNull()
    }

    /** The state is shared while watched, as the screen watches it. */
    private fun TestScope.watch(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    /** A view model that has just restored a file, with Drive backup [driveOn]. */
    private suspend fun TestScope.restoredWith(archive: Archive, driveOn: Boolean): SettingsViewModel {
        val profiles = FakeProfileRepository(aProfile())
        profiles.setDriveBackup(driveOn)
        val files = Files(contents = BackupCodec.encode(backups(Daos()).export(0)))
        val viewModel = viewModel(files = files, profiles = profiles, archive = archive)
        watch(viewModel)
        viewModel.offerRestoreFrom(Uri.parse("content://invented/file"))
        advanceUntilIdle()
        viewModel.confirmRestore()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed).isNull()
        return viewModel
    }

    private class Archive(
        var months: Int?,
        private val result: ArchiveRestore? = null,
    ) : ReadingsArchive {
        val calls = mutableListOf<String>()

        override suspend fun monthsInDrive(): Int? {
            calls += "count"
            return months
        }

        override suspend fun restoreAll(): ArchiveRestore? {
            calls += "restore"
            return result
        }

        override suspend fun writeOutOfDate(): ArchiveWrite {
            calls += "write"
            return ArchiveWrite.NothingDue
        }
    }

    private fun backups(
        daos: Daos,
        profiles: ProfileRepository = FakeProfileRepository(aProfile()),
        snapshot: SettingsSnapshot = SettingsSnapshot { {} },
    ) =
        BackupRepository(
            meals = daos.meals,
            weights = daos.weights,
            workouts = daos.workouts,
            sleep = daos.sleep,
            days = daos.days,
            corrections = daos.corrections,
            bookkeeping = daos.bookkeeping,
            trainer = daos.trainer,
            splits = daos.splits,
            profiles = profiles,
            reminders = Reminders(),
            scheduler = Scheduler(),
            ai = Ai(),
            aboutMe = InMemoryAboutMeStore(),
            foods = FakeFoodRepository(),
            savedMeals = FakeSavedMealRepository(),
            transaction = object : DatabaseTransaction {
                override suspend fun run(block: suspend () -> Unit) = block()
            },
            snapshot = snapshot,
            letterSettings = InMemoryLetterSettingsStore(),
        )

    private fun viewModel(
        keys: ApiKeyStore = Keys(),
        ai: AiSettingsStore = Ai(),
        estimator: MealEstimator = Throws(),
        reminders: ReminderStore = Reminders(),
        scheduler: ReminderScheduler = Scheduler(),
        files: BackupFiles = Files(),
        account: OffAccountSecrets = Account(),
        profiles: ProfileRepository = FakeProfileRepository(aProfile()),
        steps: StepSource = Steps(),
        daos: Daos = Daos(),
        snapshot: SettingsSnapshot = SettingsSnapshot { {} },
        archive: ReadingsArchive = ReadingsArchive.NONE,
    ): SettingsViewModel {
        val backups = backups(daos, profiles, snapshot)
        val folder = BackupFolder(context, problems)
        val drive = DriveBackup(backups, DriveAccess(context, problems), problems, DriveHttp())
        return SettingsViewModel(
            keys = keys,
            settings = ai,
            estimator = estimator,
            problems = problems,
            reminders = reminders,
            scheduler = scheduler,
            notifier = object : ReminderNotifier {
                override fun postDailyReminderNow() = Unit
            },
            backups = backups,
            files = files,
            offAccount = account,
            profiles = profiles,
            backupFolder = folder,
            automaticBackup = AutomaticBackup(profiles, backups, folder, drive),
            today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) },
            now = Now { 0L },
            steps = steps,
            meals = InMemoryMealRepository(),
            drive = drive,
            archive = archive,
        )
    }

    /**
     * The two tables a backup reads and a restore replaces, as proxies: every call answers an empty
     * table, except the ones named in [failing], which throw as a full or broken database would.
     */
    private class Daos(failing: Set<String> = emptySet()) {
        val meals: MealDao = table(failing)
        val weights: WeightDao = table(failing)
        val workouts: WorkoutDao = table(failing)
        val sleep: SleepDao = table(failing)
        val days: HealthDayDao = table(failing)
        val corrections: MovementCorrectionDao = table(failing)
        val bookkeeping: HealthBookkeepingDao = table(failing)
        val trainer: TrainerDao = table(failing)
        val splits: SessionSplitDao = table(failing)

        private inline fun <reified T> table(failing: Set<String>): T = Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, _ ->
            when {
                method.name in failing -> throw IllegalStateException("disk full")
                method.name == "toString" -> T::class.java.simpleName
                method.name == "hashCode" -> 0
                method.name == "equals" -> false
                List::class.java.isAssignableFrom(method.returnType) -> emptyList<Any>()
                // A suspend function's declared return is Object; these are the reads of a table.
                method.name.startsWith("all") -> emptyList<Any>()
                method.name == "insertSession" -> 1L
                else -> Unit
            }
        } as T
    }

    private class Keys(var failing: Boolean = false) : ApiKeyStore {
        override val key: Flow<String?> = MutableStateFlow("sk-invented")
        override suspend fun save(key: String) {
            if (failing) throw IllegalStateException("keystore unavailable")
        }

        override suspend fun clear() {
            if (failing) throw IllegalStateException("keystore unavailable")
        }
    }

    private class Ai(private val failing: Boolean = false) : AiSettingsStore {
        override val settings: Flow<AiSettings> = MutableStateFlow(AiSettings())
        override suspend fun setModel(model: String) {
            if (failing) throw IllegalStateException("disk full")
        }

        override suspend fun setDailyCeiling(ceiling: Int) {
            if (failing) throw IllegalStateException("disk full")
        }

        override suspend fun recordCall() = Unit
    }

    private class Answers(private val sentAs: String?) : MealEstimator {
        override suspend fun estimate(description: String, moreDetail: String?): EstimateResult =
            EstimateResult.Proposed(aProposal(), sentAs = sentAs)
    }

    private class Throws : MealEstimator {
        override suspend fun estimate(description: String, moreDetail: String?): EstimateResult =
            throw IllegalStateException("broken")
    }

    private class Reminders : ReminderStore {
        private val state = MutableStateFlow(Reminder())
        override val reminder: Flow<Reminder> = state
        override suspend fun save(reminder: Reminder) {
            state.value = reminder
        }

        override suspend fun current(): Reminder = state.value
    }

    private class Scheduler(private val failing: Boolean = false) : ReminderScheduler {
        override fun schedule(reminder: Reminder, now: LocalDateTime) {
            if (failing) throw SecurityException("exact alarms not allowed")
        }

        override fun cancel() = Unit
    }

    private class Files(private val contents: String? = null) : BackupFiles {
        override suspend fun read(uri: Uri): String? = contents
        override suspend fun write(uri: Uri, text: String): Boolean = true
    }

    /** The first secret goes in, the second throws: the half-account a real failure could leave. */
    private class Account(private val failing: Boolean = false) : OffAccountSecrets {
        override val username = MutableStateFlow<String?>(null)
        override val password = MutableStateFlow<String?>(null)
        override suspend fun save(username: String, password: String) {
            this.username.value = username
            if (failing) throw IllegalStateException("keystore unavailable")
            this.password.value = password
        }

        override suspend fun clear() {
            if (failing) throw IllegalStateException("keystore unavailable")
            username.value = null
            password.value = null
        }
    }

    private class Steps(
        private val failing: Boolean = false,
        private val days: List<DayMovement>? = null,
    ) : StepSource {
        override suspend fun access(): StepAccess {
            if (failing) throw IllegalStateException("health connect gone")
            return if (days == null) StepAccess.UNAVAILABLE else StepAccess.GRANTED
        }

        override suspend fun history(from: LocalDate, to: LocalDate): List<DayMovement> =
            days.orEmpty().filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }
    }

    private companion object {
        const val SENT_AS = "gpt-6-luna works: no temperature, medium thinking, strict format."
    }
}
