package com.metaself.app.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
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

    private fun backups(
        daos: Daos,
        profiles: ProfileRepository = FakeProfileRepository(aProfile()),
        snapshot: SettingsSnapshot = SettingsSnapshot { {} },
    ) =
        BackupRepository(
            meals = daos.meals,
            weights = daos.weights,
            profiles = profiles,
            reminders = Reminders(),
            scheduler = Scheduler(),
            ai = Ai(),
            foods = FakeFoodRepository(),
            savedMeals = FakeSavedMealRepository(),
            transaction = object : DatabaseTransaction {
                override suspend fun run(block: suspend () -> Unit) = block()
            },
            snapshot = snapshot,
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
    ): SettingsViewModel {
        val backups = backups(daos, profiles, snapshot)
        val folder = BackupFolder(context, problems)
        val drive = DriveBackup(backups, DriveAccess(context, problems), problems)
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
        )
    }

    /**
     * The two tables a backup reads and a restore replaces, as proxies: every call answers an empty
     * table, except the ones named in [failing], which throw as a full or broken database would.
     */
    private class Daos(failing: Set<String> = emptySet()) {
        val meals: MealDao = table(failing)
        val weights: WeightDao = table(failing)

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
                method.name == "allMeals" || method.name == "all" -> emptyList<Any>()
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

    private class Steps(private val failing: Boolean = false) : StepSource {
        override suspend fun access(): StepAccess {
            if (failing) throw IllegalStateException("health connect gone")
            return StepAccess.UNAVAILABLE
        }

        override suspend fun history(from: LocalDate, to: LocalDate): List<DayMovement> = emptyList()
    }

    private companion object {
        const val SENT_AS = "gpt-6-luna works: no temperature, medium thinking, strict format."
    }
}
