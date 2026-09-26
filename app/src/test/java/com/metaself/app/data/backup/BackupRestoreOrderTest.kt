package com.metaself.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.MealDao
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.data.weight.WeightDao
import com.metaself.app.data.health.HealthDayDao
import com.metaself.app.data.health.MovementCorrectionDao
import com.metaself.app.data.health.SleepDao
import com.metaself.app.data.health.WorkoutDao
import com.metaself.app.domain.backup.Backup
import com.metaself.app.domain.backup.BackupAi
import com.metaself.app.domain.backup.BackupArrival
import com.metaself.app.domain.backup.BackupFood
import com.metaself.app.domain.backup.BackupHealthDay
import com.metaself.app.domain.backup.BackupItem
import com.metaself.app.domain.backup.BackupMeal
import com.metaself.app.domain.backup.BackupMealComponent
import com.metaself.app.domain.backup.BackupMovementCorrection
import com.metaself.app.domain.backup.BackupNutrients
import com.metaself.app.domain.backup.BackupProfile
import com.metaself.app.domain.backup.BackupReminder
import com.metaself.app.domain.backup.BackupRevision
import com.metaself.app.domain.backup.BackupSavedMeal
import com.metaself.app.domain.backup.BackupSleep
import com.metaself.app.domain.backup.BackupSleepStage
import com.metaself.app.domain.backup.BackupWeight
import com.metaself.app.domain.backup.BackupWorkout
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.reminder.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.LocalDateTime

/**
 * The ORDER a restore writes in, and what it does when a write throws — with no database.
 *
 * What rolling back actually undoes is the real database's business and is proved in
 * `BackupRoundTripTest` (CI only). What is proved here, on any machine, is the shape that makes the
 * rollback mean something: every value is built before the first write, the settings are written
 * last and inside the transaction, the settings are put back whenever the transaction does not
 * commit, and "nothing was restored" is claimed only when that put-back worked.
 *
 * Every figure is invented.
 */
class BackupRestoreOrderTest {

    private val log = mutableListOf<String>()

    @Test
    fun `the settings are written inside the transaction, after every database write, and the alarm after the commit`() =
        runTest {
            restorer().restore(aFile())

            assertThat(log).containsExactly(
                "snapshot",
                "begin",
                "deleteAllMeals",
                "weights.deleteAll",
                "workouts.deleteAll",
                "sleep.deleteAll",
                "days.deleteAll",
                "corrections.deleteAll",
                "findOrCreate Yoghurt",
                "create Breakfast",
                "put",
                "insertMeal",
                "insertItems",
                "weights.upsert",
                "workouts.insertAll",
                "sleep.insertSession",
                "sleep.insertStages",
                "days.insertAll",
                "corrections.insertAll",
                "profile.save",
                "saveRevision",
                "saveArrival",
                "ai.setModel",
                "ai.setDailyCeiling",
                "reminders.save",
                "commit",
                "alarm.schedule",
            ).inOrder()
        }

    @Test
    fun `a settings write that throws rolls the database back and puts the settings back`() = runTest {
        val failure = thrownBy<NothingRestored> {
            restorer(profiles = Profiles(failingOn = "saveArrival")).restore(aFile())
        }

        assertThat(failure.cause).hasMessageThat().isEqualTo("disk full")
        assertThat(log).containsAtLeast("saveArrival", "rollback", "put back").inOrder()
        assertThat(log).doesNotContain("commit")
        assertThat(log.filter { it.startsWith("alarm") }).isEmpty()
    }

    @Test
    fun `a database write that throws midway rolls back and puts the settings back`() = runTest {
        val failure = thrownBy<NothingRestored> {
            restorer(meals = dao(failingOn = "insertItems")).restore(aFile())
        }

        assertThat(failure.cause).hasMessageThat().isEqualTo("disk full")
        assertThat(log).containsAtLeast("deleteAllMeals", "insertItems", "rollback", "put back")
            .inOrder()
        assertThat(log).doesNotContain("profile.save")
    }

    /** Recent problems must still name the real fault, not the wrapper. */
    @Test
    fun `what is refused names the real failure`() = runTest {
        val failure = thrownBy<NothingRestored> {
            restorer(meals = dao(failingOn = "deleteAllMeals")).restore(aFile())
        }

        assertThat(failure).hasMessageThat().isEqualTo("java.lang.IllegalStateException: disk full")
        assertThat(failure.stackTrace).isEqualTo(failure.cause!!.stackTrace)
    }

    @Test
    fun `a put-back that also fails does not claim that nothing changed`() = runTest {
        val failure = thrownBy<IllegalStateException> {
            restorer(
                profiles = Profiles(failingOn = "saveArrival"),
                snapshot = Snapshot(putBackFails = true),
            ).restore(aFile())
        }

        assertThat(failure).isNotInstanceOf(NothingRestored::class.java)
        assertThat(failure).hasMessageThat().isEqualTo("disk full")
        assertThat(failure.suppressed.single()).hasMessageThat().isEqualTo("put back failed")
    }

    /**
     * An alias no key can be made of throws while the file is being read — today after the wipe,
     * now before anything is written.
     */
    @Test
    fun `a file refused while it is being read touches nothing`() = runTest {
        val file = aFile().copy(foods = listOf(yoghurt().copy(alsoKnownAs = listOf("!!!"))))

        thrownBy<NothingRestored> { restorer().restore(file) }

        assertThat(log).isEmpty()
    }

    /**
     * The two refusals the old catches swallowed, now decided before the transaction: a caught throw
     * inside a nested transaction would roll the whole restore back and still report success.
     */
    @Test
    fun `a food or a meal named nothing a key can be made of is skipped, never handed to the store`() =
        runTest {
            val file = aFile().copy(
                foods = listOf(yoghurt(), yoghurt().copy(key = "|na", name = "!!!")),
                savedMeals = listOf(breakfast(), BackupSavedMeal(name = "?!")),
            )

            restorer().restore(file)

            assertThat(log.filter { it.startsWith("findOrCreate") })
                .containsExactly("findOrCreate Yoghurt")
            assertThat(log.filter { it.startsWith("create") }).containsExactly("create Breakfast")
            assertThat(log).contains("commit")
        }

    @Test
    fun `a restore that commits says what it restored`() = runTest {
        val result = restorer().restore(aFile())

        assertThat(result).isEqualTo(
            RestoreResult(meals = 1, weights = 1, hasProfile = true, workouts = 1, healthDays = 1),
        )
    }

    /** The bug this closes: only `health_days` rows were counted, so a night alone looked like nothing. */
    @Test
    fun `a file with only a night of sleep counts as having health data`() = runTest {
        val file = aFile().copy(healthDays = emptyList(), movementCorrections = emptyList())

        val result = restorer().restore(file)

        assertThat(result.healthDays).isEqualTo(1)
    }

    /** Same bug, the other row a restore also deletes: a correction alone with no daily summary. */
    @Test
    fun `a file with only a correction counts as having health data`() = runTest {
        val file = aFile().copy(healthDays = emptyList(), sleep = emptyList())

        val result = restorer().restore(file)

        assertThat(result.healthDays).isEqualTo(1)
    }

    /**
     * A file can hold the same workout or the same night twice — a re-export, or one hand-edited.
     * `prepare` resolves it before anything is written, or the unique index would abort the write
     * partway through the transaction.
     */
    @Test
    fun `a duplicated synced workout and a duplicated night restore each once`() = runTest {
        val workout = BackupWorkout(
            epochDay = TEST_EPOCH_DAY,
            startedAtMillis = 1_000,
            durationMinutes = 30,
            kind = "RUN",
            energySource = "BAND",
            source = "SYNCED",
            origin = "com.example.band",
            originId = "abc-1",
        )
        val night = BackupSleep(
            epochDay = TEST_EPOCH_DAY,
            startMillis = 1_000,
            endMillis = 3_000,
            origin = "com.example.band",
            recordId = "s-1",
        )
        val file = aFile().copy(workouts = listOf(workout, workout), sleep = listOf(night, night))

        val result = restorer().restore(file)

        assertThat(result.workouts).isEqualTo(1)
        assertThat(log.count { it == "sleep.insertSession" }).isEqualTo(1)
    }

    @Test
    fun `a health-record write that throws rolls back and puts the settings back`() = runTest {
        val failure = thrownBy<NothingRestored> {
            BackupRepository(
                meals = dao(),
                weights = dao(prefix = "weights."),
                workouts = dao(prefix = "workouts."),
                sleep = dao(prefix = "sleep.", failingOn = "insertStages"),
                days = dao(prefix = "days."),
                corrections = dao(prefix = "corrections."),
                profiles = Profiles(),
                reminders = Reminders(),
                scheduler = Scheduler(),
                ai = Ai(),
                foods = Foods(),
                savedMeals = SavedMeals(),
                transaction = Transaction(),
                snapshot = Snapshot(),
            ).restore(aFile())
        }

        assertThat(failure.cause).hasMessageThat().isEqualTo("disk full")
        assertThat(log).containsAtLeast("sleep.insertStages", "rollback", "put back").inOrder()
        assertThat(log).doesNotContain("profile.save")
    }

    /** What [block] threw, which must be a [T]. `assertThrows` takes no suspending block. */
    private inline fun <reified T : Throwable> thrownBy(block: () -> Unit): T {
        val thrown = runCatching(block).exceptionOrNull()
        assertThat(thrown).isInstanceOf(T::class.java)
        return thrown as T
    }

    // --- The file ---------------------------------------------------------------------------------

    private fun yoghurt() = BackupFood(
        key = "yoghurt|na",
        name = "Yoghurt",
        brand = "NA",
        per100g = BackupNutrients(60.0, 4.0, 5.0, 3.0, source = "TYPED"),
    )

    private fun breakfast() = BackupSavedMeal(
        name = "Breakfast",
        components = listOf(BackupMealComponent("yoghurt|na", 150.0, "GRAMS")),
    )

    /** `aProfile()`, as the file writes it. */
    private fun aFile() = Backup(
        profile = BackupProfile(180, 1980, "MALE", 80.0, "MODERATE", "LOSE", 0.5),
        foods = listOf(yoghurt()),
        savedMeals = listOf(breakfast()),
        meals = listOf(
            BackupMeal(
                epochDay = TEST_EPOCH_DAY,
                loggedAtMillis = 1_000,
                items = listOf(
                    BackupItem(
                        name = "Yoghurt",
                        kcal = 90,
                        proteinG = 6,
                        carbsG = 8,
                        fatG = 5,
                        source = "TYPED",
                        food = "yoghurt|na",
                    ),
                ),
                savedMeal = "Breakfast",
            ),
        ),
        weights = listOf(BackupWeight(TEST_EPOCH_DAY, 80.0)),
        revision = BackupRevision(TEST_EPOCH_DAY, 80.0, 2_000, 2_100),
        arrival = BackupArrival(75.0, TEST_EPOCH_DAY),
        reminder = BackupReminder(enabled = true, hour = 20, minute = 0),
        ai = BackupAi("some-model", 30),
        workouts = listOf(
            BackupWorkout(
                epochDay = TEST_EPOCH_DAY,
                startedAtMillis = 1_000,
                durationMinutes = 45,
                kind = "STRENGTH",
                energyKcal = 150,
                energySource = "MET_ESTIMATE",
                effort = "MODERATE",
                source = "TYPED",
            ),
        ),
        sleep = listOf(
            BackupSleep(
                epochDay = TEST_EPOCH_DAY,
                startMillis = 1_000,
                endMillis = 3_000,
                origin = "com.example.band",
                recordId = "s-1",
                stages = listOf(BackupSleepStage("DEEP", 1_000, 2_000)),
            ),
        ),
        healthDays = listOf(BackupHealthDay(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1_000)),
        movementCorrections = listOf(
            BackupMovementCorrection(epochDay = TEST_EPOCH_DAY, steps = 9_000, setAtMillis = 1_000),
        ),
    )

    // --- The stores, recording into one log ------------------------------------------------------

    private fun restorer(
        meals: MealDao = dao(),
        profiles: ProfileRepository = Profiles(),
        snapshot: SettingsSnapshot = Snapshot(),
    ) = BackupRepository(
        meals = meals,
        weights = dao(prefix = "weights."),
        workouts = dao(prefix = "workouts."),
        sleep = dao(prefix = "sleep."),
        days = dao(prefix = "days."),
        corrections = dao(prefix = "corrections."),
        profiles = profiles,
        reminders = Reminders(),
        scheduler = Scheduler(),
        ai = Ai(),
        foods = Foods(),
        savedMeals = SavedMeals(),
        transaction = Transaction(),
        snapshot = snapshot,
    )

    /** Runs the block, and says whether it would have committed or rolled back. */
    private inner class Transaction : DatabaseTransaction {
        override suspend fun run(block: suspend () -> Unit) {
            log += "begin"
            try {
                block()
            } catch (failure: Throwable) {
                log += "rollback"
                throw failure
            }
            log += "commit"
        }
    }

    private inner class Snapshot(private val putBackFails: Boolean = false) : SettingsSnapshot {
        override suspend fun take(): suspend () -> Unit {
            log += "snapshot"
            return {
                log += "put back"
                if (putBackFails) throw IllegalStateException("put back failed")
            }
        }
    }

    /**
     * A DAO as a proxy that records each call and answers what a table would. The reads are not
     * recorded: only writes are in question here.
     */
    private inline fun <reified T> dao(prefix: String = "", failingOn: String? = null): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            when {
                method.name == "toString" -> T::class.java.simpleName
                method.name == "hashCode" -> 0
                method.name == "equals" -> false
                method.name.startsWith("all") -> emptyList<Any>()
                else -> {
                    log += prefix + method.name
                    if (method.name == failingOn) throw IllegalStateException("disk full")
                    when (method.name) {
                        "insertMeal", "insertSession" -> 1L
                        "insertItems" -> listOf(1L)
                        else -> Unit
                    }
                }
            }
        } as T

    private inner class Foods(
        private val real: FakeFoodRepository = FakeFoodRepository(),
    ) : FoodRepository by real {
        override suspend fun findOrCreate(
            name: String,
            brand: String?,
            facts: FoodFacts,
            barcode: String?,
        ) = real.findOrCreate(name, brand, facts, barcode).also { log += "findOrCreate $name" }
    }

    private inner class SavedMeals(
        private val real: FakeSavedMealRepository = FakeSavedMealRepository(),
    ) : SavedMealRepository by real {
        override suspend fun create(name: String): MealResult {
            log += "create $name"
            return real.create(name)
        }

        override suspend fun put(
            mealId: Long,
            foodId: Long,
            amount: Double,
            countedAs: com.metaself.app.domain.food.CountedAs,
        ) {
            log += "put"
            real.put(mealId, foodId, amount, countedAs)
        }
    }

    private inner class Profiles(
        private val failingOn: String? = null,
        private val real: FakeProfileRepository = FakeProfileRepository(null),
    ) : ProfileRepository by real {
        private fun write(name: String) {
            log += name
            if (name == failingOn) throw IllegalStateException("disk full")
        }

        override suspend fun save(profile: com.metaself.app.domain.profile.Profile) =
            write("profile.save")

        override suspend fun saveRevision(revision: com.metaself.app.domain.target.TargetRevision) =
            write("saveRevision")

        override suspend fun markRevisionSeen() = write("markRevisionSeen")

        override suspend fun saveArrival(arrival: GoalArrival) = write("saveArrival")

        override suspend fun recordMilestones(
            reached: Map<com.metaself.app.domain.milestone.Milestone, Long>,
        ) = write("recordMilestones")
    }

    private inner class Reminders : ReminderStore {
        override val reminder: Flow<Reminder> = MutableStateFlow(Reminder())
        override suspend fun save(reminder: Reminder) {
            log += "reminders.save"
        }

        override suspend fun current(): Reminder = Reminder()
    }

    private inner class Scheduler : ReminderScheduler {
        override fun schedule(reminder: Reminder, now: LocalDateTime) {
            log += "alarm.schedule"
        }

        override fun cancel() {
            log += "alarm.cancel"
        }
    }

    private inner class Ai : AiSettingsStore {
        override val settings: Flow<AiSettings> = MutableStateFlow(AiSettings())
        override suspend fun setModel(model: String) {
            log += "ai.setModel"
        }

        override suspend fun setDailyCeiling(ceiling: Int) {
            log += "ai.setDailyCeiling"
        }

        override suspend fun recordCall() = Unit
    }
}
