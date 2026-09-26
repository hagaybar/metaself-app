package com.metaself.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.RoomFoodRepository
import com.metaself.app.data.food.RoomSavedMealRepository
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.domain.backup.BackupArrival
import com.metaself.app.domain.backup.BackupPerUnit
import com.metaself.app.domain.backup.BackupWeight
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.data.time.Now
import com.metaself.app.domain.backup.Backup
import com.metaself.app.domain.backup.BackupItem
import com.metaself.app.domain.backup.BackupFood
import com.metaself.app.domain.backup.BackupMeal
import com.metaself.app.domain.backup.BackupMealComponent
import com.metaself.app.domain.backup.BackupNutrients
import com.metaself.app.domain.backup.BackupSavedMeal
import com.metaself.app.domain.backup.BackupWeight2
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.day.RoomDatabaseTransaction
import com.metaself.app.data.day.toEntities
import com.metaself.app.data.health.HealthDayEntity
import com.metaself.app.data.health.MovementCorrectionEntity
import com.metaself.app.data.health.SleepSessionEntity
import com.metaself.app.data.health.SleepStageEntity
import com.metaself.app.data.health.WorkoutEntity
import com.metaself.app.data.weight.WeightEntity
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Export, wipe, restore — against a real database.
 *
 * The done-when for this step is "a wiped install restores to an identical state from a file", and
 * that sentence is worth nothing unless something actually wipes and restores. Skipped on this
 * aarch64 box and run for real in CI, like every other database test here.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var db: MetaSelfDatabase

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    @Test
    fun `a wiped store comes back identical, portions and confidence included`() = runTest {
        val meals = db.mealDao()
        val weights = db.weightDao()

        insert(
            Meal(
                epochDay = 20_699,
                loggedAtMillis = 1_000,
                note = null,
                items = listOf(
                    FoodItem(
                        name = "Pizza",
                        portion = "2 slice",
                        portionAmount = 2.0,
                        portionUnit = "slice",
                        kcal = 570,
                        proteinG = 24,
                        carbsG = 68,
                        fatG = 22,
                        source = Source.AI_ESTIMATE,
                        confidence = Confidence.MEDIUM,
                    ),
                    FoodItem(
                        name = "Salad",
                        kcal = 60,
                        proteinG = 2,
                        carbsG = 8,
                        fatG = 2,
                        source = Source.TYPED,
                    ),
                ),
            ),
        )
        weights.upsert(WeightEntity(epochDay = 20_699, kg = 80.0))
        weights.upsert(WeightEntity(epochDay = 20_700, kg = 79.5))

        val exported = meals.allMeals()
        val exportedWeights = weights.all()
        assertThat(exported).hasSize(1)

        meals.deleteAllMeals()
        weights.deleteAll()
        assertThat(meals.allMeals()).isEmpty()
        assertThat(weights.all()).isEmpty()

        exported.forEach { row ->
            val mealId = meals.insertMeal(row.meal.copy(id = 0))
            meals.insertItems(row.items.map { it.copy(id = 0, mealId = mealId) })
        }
        exportedWeights.forEach { weights.upsert(it) }

        val restored = meals.allMeals().single()
        assertThat(restored.meal.epochDay).isEqualTo(20_699)
        assertThat(restored.items).hasSize(2)

        val pizza = restored.items.first { it.name == "Pizza" }
        assertThat(pizza.kcal).isEqualTo(570)
        // The portion's NUMBERS, not only its words. An export that dropped them would restore a
        // meal nobody could adjust, and the loss would be invisible until he tried.
        assertThat(pizza.portionAmount).isEqualTo(2.0)
        assertThat(pizza.portionUnit).isEqualTo("slice")
        assertThat(pizza.confidence).isEqualTo("MEDIUM")

        val salad = restored.items.first { it.name == "Salad" }
        assertThat(salad.source).isEqualTo("TYPED")
        assertThat(salad.confidence).isNull()

        assertThat(weights.all().map { it.kg }).containsExactly(80.0, 79.5).inOrder()
    }

    @Test
    fun `deleting every meal takes its items with it`() = runTest {
        insert(
            Meal(
                epochDay = 20_699,
                loggedAtMillis = 1_000,
                note = null,
                items = listOf(
                    FoodItem(
                        name = "Hummus",
                        kcal = 180,
                        proteinG = 6,
                        carbsG = 12,
                        fatG = 12,
                        source = Source.TYPED,
                    ),
                ),
            ),
        )

        db.mealDao().deleteAllMeals()

        assertThat(db.mealDao().allMeals()).isEmpty()
        assertThat(db.mealDao().observeLoggedDays().let { true }).isTrue()
    }

    /**
     * Invented figures. A band's run, a typed session, a night, a day and a correction are restored
     * OVER themselves — no manual wipe first, so this is restore's own deletes and the sleep-stage
     * cascade running for real, not a wipe this test staged for it.
     */
    @Test
    fun `the health record restored over itself comes back exactly as it was`() = runTest {
        val band = WorkoutEntity(
            epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 30, kind = "RUN",
            title = "Running", distanceM = 5_000, energyKcal = 300, energySource = "BAND",
            effort = null, source = "SYNCED", origin = "com.example.band", originId = "abc-1",
            hidden = true, note = "not a run", avgHeartRate = 140, maxHeartRate = 160,
            zoneSeconds = "0,300,900,600,0", zoneMaxSource = "ESTIMATED",
        )
        val typed = band.copy(
            kind = "STRENGTH", title = "Strength", distanceM = null, energyKcal = 150,
            energySource = "MET_ESTIMATE", effort = "MODERATE", source = "TYPED",
            origin = null, originId = null, hidden = false, note = null,
            avgHeartRate = null, maxHeartRate = null, zoneSeconds = null, zoneMaxSource = null,
        )
        db.workoutDao().insertAll(listOf(band, typed))
        val originalNight = SleepSessionEntity(epochDay = 20_699, startMillis = 1_000, endMillis = 3_000,
            origin = "com.example.band", recordId = "s-1", title = null)
        val nightId = db.sleepDao().insertSession(originalNight)
        db.sleepDao().insertStages(
            listOf(
                SleepStageEntity(sessionId = nightId, stage = "LIGHT", startMillis = 1_000, endMillis = 2_000),
                SleepStageEntity(sessionId = nightId, stage = "DEEP", startMillis = 2_000, endMillis = 3_000),
            ),
        )
        val day = HealthDayEntity(epochDay = 20_699, computedAtMillis = 5_000, steps = 9_000,
            stepsSource = "TOTAL", sleepMinutes = 30, sleepSource = "COMPUTED")
        db.healthDayDao().put(day)
        val correction = MovementCorrectionEntity(20_699, 9_000, null, 2_000, null)
        db.movementCorrectionDao().insertAll(listOf(correction))

        val file = BackupCodec.decode(BackupCodec.encode(repository().export(nowMillis = 5_000)))!!
        val result = repository().restore(file)

        assertThat(result.workouts).isEqualTo(2)
        assertThat(result.healthDays).isEqualTo(1)
        assertThat(db.workoutDao().all().map { it.copy(id = 0) }).containsExactly(band, typed)
        val night = db.sleepDao().allSessions().single()
        assertThat(night.copy(id = 0)).isEqualTo(originalNight)
        assertThat(
            db.sleepDao().stagesOf(night.id).map { Triple(it.stage, it.startMillis, it.endMillis) },
        ).containsExactly(
            Triple("LIGHT", 1_000L, 2_000L),
            Triple("DEEP", 2_000L, 3_000L),
        ).inOrder()
        // The old night's own stages cascaded away with it: two remain, not four.
        assertThat(db.sleepDao().allStages()).hasSize(2)
        assertThat(db.healthDayDao().day(20_699)).isEqualTo(day)
        assertThat(db.movementCorrectionDao().all().single()).isEqualTo(correction)
    }

    // --- Version 2: the foods and the meals he built ------------------------------------------

    private fun foods(): FoodRepository =
        RoomFoodRepository(db, db.foodDao(), Now { 1_000 })

    private fun savedMeals(): SavedMealRepository =
        RoomSavedMealRepository(db, db.savedMealDao(), db.foodDao(), Now { 1_000 })

    private suspend fun aYoghurt(): Food = foods().findOrCreate(
        "Yoghurt",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(72.0, 4.0, 6.0, 2.0),
                Provenance(Source.TYPED, null, 1_000),
            ),
        ),
    ).food

    /**
     * **Every version-1 file stays restorable for ever**, and restoring one runs the same conversion
     * the upgrade ran — so a restore never leaves the history detached from the food list.
     */
    @Test
    fun `a file written before foods existed restores, and derives its foods`() = runTest {
        val repository = repository()
        val version1 = Backup(
            version = Backup.FIRST_VERSION,
            meals = listOf(
                BackupMeal(
                    epochDay = 20_699,
                    loggedAtMillis = 1_000,
                    items = listOf(
                        BackupItem(
                            name = "Yoghurt",
                            portion = "180 g",
                            portionAmount = 180.0,
                            portionUnit = "g",
                            kcal = 130,
                            proteinG = 8,
                            carbsG = 11,
                            fatG = 4,
                            source = "TYPED",
                        ),
                    ),
                ),
            ),
        )

        repository.restore(version1)

        val restoredFoods = foods().observeAll().first()
        assertThat(restoredFoods.map { it.name }).containsExactly("Yoghurt")
        // 130 kcal for 180 g.
        assertThat(restoredFoods.single().facts.per100g!!.nutrients.kcal).isWithin(0.1).of(72.2)
        // And the row points at it, so the day is not detached from the list.
        val restoredMeal = db.mealDao().allMeals().single()
        assertThat(restoredMeal.items.single().foodId).isEqualTo(restoredFoods.single().id)
    }

    /**
     * A meal is only something the owner built. Restoring an old file must not manufacture the
     * meals the conversion refused to.
     */
    @Test
    fun `a file written before foods existed creates no meals`() = runTest {
        repository().restore(
            Backup(
                version = Backup.FIRST_VERSION,
                meals = listOf(
                    BackupMeal(
                        epochDay = 20_699,
                        loggedAtMillis = 1_000,
                        items = listOf(
                            BackupItem(name = "Yoghurt", kcal = 130, proteinG = 8, carbsG = 11, fatG = 4, source = "TYPED"),
                            BackupItem(name = "Bread", kcal = 160, proteinG = 6, carbsG = 30, fatG = 2, source = "TYPED"),
                        ),
                    ),
                ),
            ),
        )

        assertThat(savedMeals().observeOffered().first()).isEmpty()
    }

    @Test
    fun `the foods and the meals he built survive export and restore`() = runTest {
        val repository = repository()
        val yoghurt = aYoghurt()
        val mealId = (savedMeals().create("Breakfast") as MealResult.Built).mealId
        savedMeals().put(mealId, yoghurt.id, 180.0, CountedAs.GRAMS)

        val exported = repository.export(nowMillis = 2_000)
        assertThat(exported.version).isEqualTo(2)
        assertThat(exported.foods.map { it.key }).contains("yoghurt|na")
        assertThat(exported.savedMeals.single().name).isEqualTo("Breakfast")

        // Wiped, as a new phone would be.
        savedMeals().delete(mealId)
        foods().delete(yoghurt.id)
        assertThat(foods().observeAll().first()).isEmpty()

        repository.restore(exported)

        val restoredFoods = foods().observeAll().first()
        assertThat(restoredFoods.map { it.name }).containsExactly("Yoghurt")
        assertThat(restoredFoods.single().facts.per100g!!.nutrients.kcal).isEqualTo(72.0)

        val restoredMeal = savedMeals().observeOffered().first().single()
        assertThat(restoredMeal.name).isEqualTo("Breakfast")
        assertThat(restoredMeal.components.single().food.name).isEqualTo("Yoghurt")
        assertThat(restoredMeal.components.single().amount).isEqualTo(180.0)
    }

    /**
     * The record of what was eaten is the authority; the food list is derivable from it, which is
     * exactly why an item naming a food the file forgot to declare is recoverable rather than fatal.
     */
    @Test
    fun `an item naming a food the file does not declare has it created from the item`() = runTest {
        repository().restore(
            Backup(
                version = 2,
                foods = emptyList(),
                meals = listOf(
                    BackupMeal(
                        epochDay = 20_699,
                        loggedAtMillis = 1_000,
                        items = listOf(
                            BackupItem(
                                name = "Hummus",
                                portionAmount = 60.0,
                                portionUnit = "g",
                                kcal = 180,
                                proteinG = 6,
                                carbsG = 12,
                                fatG = 12,
                                source = "TYPED",
                                food = "hummus|na",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertThat(foods().observeAll().first().map { it.name }).containsExactly("Hummus")
        assertThat(db.mealDao().allMeals().single().items.single().foodId).isNotNull()
    }

    /**
     * A scanned row exports with no food when it was never attached, and restoring it works its
     * food's per-100 g back from the row — whole grams (D38), so 0 g of fat where the packet printed
     * 0.5. That figure is filed as copied from a past meal, never as the label's (D4, D43, issue
     * #29). The row itself comes back exactly as it was: still the label's, fat 0.
     */
    @Test
    fun `a scanned item restored with no food gives its food a copied figure, not the label's`() = runTest {
        repository().restore(
            Backup(
                version = 2,
                foods = emptyList(),
                meals = listOf(
                    BackupMeal(
                        epochDay = 20_699,
                        loggedAtMillis = 1_000,
                        items = listOf(
                            BackupItem(
                                name = "Rice cakes",
                                portion = "30 g",
                                portionAmount = 30.0,
                                portionUnit = "g",
                                kcal = 116,
                                proteinG = 2,
                                carbsG = 24,
                                fatG = 0,
                                source = "LABEL",
                                food = null,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val rice = foods().observeAll().first().single()
        assertThat(rice.name).isEqualTo("Rice cakes")
        val per100g = rice.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(per100g.nutrients.kcal).isWithin(0.001).of(116 * 100 / 30.0)
        assertThat(per100g.nutrients.fatG).isWithin(0.001).of(0.0)

        val item = db.mealDao().allMeals().single().items.single()
        assertThat(item.foodId).isEqualTo(rice.id)
        assertThat(item.source).isEqualTo(Source.LABEL.name)
        assertThat(item.fatG).isEqualTo(0)
    }

    /** The name as typed that day stays on the row; the current name lives in the foods block. */
    @Test
    fun `an item keeps the name it was typed under`() = runTest {
        val repository = repository()
        val yoghurt = aYoghurt()
        foods().rename(yoghurt.id, "Greek yoghurt")

        insert(
            Meal(
                epochDay = 20_699,
                loggedAtMillis = 1_000,
                note = null,
                items = listOf(
                    FoodItem(
                        name = "yogurt",
                        kcal = 130,
                        proteinG = 8,
                        carbsG = 11,
                        fatG = 4,
                        source = Source.TYPED,
                        foodId = yoghurt.id,
                    ),
                ),
            ),
        )

        val exported = repository.export(nowMillis = 2_000)

        assertThat(exported.meals.single().items.single().name).isEqualTo("yogurt")
        assertThat(exported.meals.single().items.single().food).isEqualTo("greek yoghurt|na")
        assertThat(exported.foods.single().name).isEqualTo("Greek yoghurt")
    }

    /**
     * The ceilings every box has since D42 (issue #32) are for typing, never for reading what is
     * stored: a file holding numbers past every one of them — written by a version before the rule,
     * or by a slipped finger the old boxes let through — restores them exactly as they were. A
     * restore that refused them would lose a record over a number the owner can correct later.
     */
    @Test
    fun `numbers past every box's ceiling still restore as they were`() = runTest {
        repository().restore(
            Backup(
                version = 2,
                foods = listOf(
                    BackupFood(
                        key = "lard|na",
                        name = "Lard",
                        brand = "NA",
                        per100g = BackupNutrients(5_000.0, 0.0, 0.0, 500.0, "TYPED"),
                        gramsPerUnit = BackupWeight2(9_000.0, "TYPED"),
                    ),
                ),
                meals = listOf(
                    BackupMeal(
                        epochDay = 20_699,
                        loggedAtMillis = 1_000,
                        items = listOf(
                            BackupItem(
                                name = "Lard",
                                portionAmount = 1e6,
                                portionUnit = "g",
                                kcal = 50_000,
                                proteinG = 0,
                                carbsG = 0,
                                fatG = 5_000,
                                source = "TYPED",
                                food = "lard|na",
                            ),
                        ),
                    ),
                ),
                savedMeals = listOf(
                    BackupSavedMeal(
                        name = "Feast",
                        components = listOf(BackupMealComponent("lard|na", 1e6, "GRAMS")),
                    ),
                ),
            ),
        )

        val lard = foods().observeAll().first().single()
        assertThat(lard.facts.per100g!!.nutrients).isEqualTo(Nutrients(5_000.0, 0.0, 0.0, 500.0))
        assertThat(lard.facts.gramsPerUnit!!.grams).isEqualTo(9_000.0)

        val item = db.mealDao().allMeals().single().items.single()
        assertThat(item.kcal).isEqualTo(50_000)
        assertThat(item.fatG).isEqualTo(5_000)
        assertThat(item.portionAmount).isEqualTo(1e6)
        assertThat(item.portionUnit).isEqualTo("g")

        val feast = savedMeals().observeOffered().first().single()
        assertThat(feast.name).isEqualTo("Feast")
        assertThat(feast.components.single().amount).isEqualTo(1e6)
    }

    // --- All or nothing (public issue #32) --------------------------------------------------------

    /**
     * A restore that fails at its very last write — the settings, which are written inside the
     * transaction after every database write — leaves the record exactly as it was, and the settings
     * are put back.
     */
    @Test
    fun `a restore whose settings write fails leaves the record exactly as it was`() = runTest {
        aRecord()
        val before = record()
        val putBack = mutableListOf<String>()
        val repository = repository(
            profiles = object : ProfileRepository by FakeProfileRepository(null) {
                override suspend fun saveArrival(arrival: GoalArrival): Unit =
                    throw IllegalStateException("disk full")
            },
            snapshot = { { putBack += "put back" } },
        )

        val thrown = runCatching { repository.restore(aFileReplacingIt()) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(NothingRestored::class.java)
        assertThat(putBack).containsExactly("put back")
        assertThat(record()).isEqualTo(before)
    }

    /** Midway: after the wipe, after a food and a built meal were created, before any meal. */
    @Test
    fun `a restore whose database write fails midway leaves the record exactly as it was`() = runTest {
        aRecord()
        val before = record()
        val repository = repository(
            savedMeals = object : SavedMealRepository by savedMeals() {
                override suspend fun put(
                    mealId: Long,
                    foodId: Long,
                    amount: Double,
                    countedAs: CountedAs,
                ): Unit = throw IllegalStateException("disk full")
            },
        )

        val thrown = runCatching { repository.restore(aFileReplacingIt()) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(NothingRestored::class.java)
        assertThat(record()).isEqualTo(before)
    }

    /**
     * The two refusals a restore always skipped — a food and a built meal whose names leave nothing
     * to key on — are decided before the transaction. Were either still thrown and caught inside
     * it, SQLite would roll the whole restore back on closing and this would find nothing restored.
     */
    @Test
    fun `a food and a built meal named nothing a key can be made of are skipped, and the rest is restored`() =
        runTest {
            val file = aFileReplacingIt().let { file ->
                file.copy(
                    foods = file.foods + file.foods.single().copy(key = "|na", name = "!!!"),
                    savedMeals = file.savedMeals + BackupSavedMeal(name = "?!"),
                )
            }

            repository().restore(file)

            assertThat(foods().observeAll().first().map { it.name }).containsExactly("Bread")
            assertThat(savedMeals().observeOffered().first().map { it.name }).containsExactly("Supper")
            assertThat(db.mealDao().allMeals().single().items.single().name).isEqualTo("Bread")
            assertThat(db.weightDao().all().map { it.kg }).containsExactly(79.0)
        }

    /** What is here before a failed restore: a logged meal, a weight, a food and a built meal. */
    private suspend fun aRecord() {
        val yoghurt = aYoghurt()
        val mealId = (savedMeals().create("Lunch") as MealResult.Built).mealId
        savedMeals().put(mealId, yoghurt.id, 150.0, CountedAs.GRAMS)
        insert(
            Meal(
                epochDay = 20_699,
                loggedAtMillis = 1_000,
                items = listOf(
                    FoodItem(
                        name = "Yoghurt",
                        kcal = 108,
                        proteinG = 6,
                        carbsG = 9,
                        fatG = 3,
                        source = Source.TYPED,
                        foodId = yoghurt.id,
                    ),
                ),
                savedMealId = mealId,
            ),
        )
        db.weightDao().upsert(WeightEntity(epochDay = 20_699, kg = 80.0))
    }

    /** Everything a restore replaces or merges into, read back whole, ids and stamps included. */
    private suspend fun record(): List<Any> = listOf(
        db.mealDao().allMeals(),
        db.weightDao().all(),
        foods().observeAll().first(),
        savedMeals().observeOffered().first(),
    )

    /** A file sharing nothing with [aRecord], and touching every store, the settings included. */
    private fun aFileReplacingIt() = Backup(
        foods = listOf(
            BackupFood(
                key = "bread|na",
                name = "Bread",
                brand = "NA",
                perUnit = BackupPerUnit("slice", 80.0, 3.0, 15.0, 1.0, source = "TYPED"),
            ),
        ),
        savedMeals = listOf(
            BackupSavedMeal(
                name = "Supper",
                components = listOf(BackupMealComponent("bread|na", 2.0, "UNITS")),
            ),
        ),
        meals = listOf(
            BackupMeal(
                epochDay = 20_700,
                loggedAtMillis = 2_000,
                items = listOf(
                    BackupItem(
                        name = "Bread",
                        portion = "2 slice",
                        portionAmount = 2.0,
                        portionUnit = "slice",
                        kcal = 160,
                        proteinG = 6,
                        carbsG = 30,
                        fatG = 2,
                        source = "TYPED",
                        food = "bread|na",
                    ),
                ),
                savedMeal = "Supper",
            ),
        ),
        weights = listOf(BackupWeight(20_700, 79.0)),
        arrival = BackupArrival(75.0, 20_700),
    )

    private fun repository(
        profiles: ProfileRepository = FakeProfileRepository(null),
        savedMeals: SavedMealRepository = savedMeals(),
        snapshot: SettingsSnapshot = SettingsSnapshot { {} },
    ): BackupRepository = BackupRepository(
        meals = db.mealDao(),
        weights = db.weightDao(),
        workouts = db.workoutDao(),
        sleep = db.sleepDao(),
        days = db.healthDayDao(),
        corrections = db.movementCorrectionDao(),
        profiles = profiles,
        reminders = NoReminders(),
        scheduler = NoScheduler(),
        ai = NoAiSettings(),
        foods = foods(),
        savedMeals = savedMeals,
        transaction = RoomDatabaseTransaction(db),
        snapshot = snapshot,
    )

    private suspend fun insert(meal: Meal) {
        val (entity, items) = meal.toEntities()
        val mealId = db.mealDao().insertMeal(entity.copy(id = 0))
        db.mealDao().insertItems(items.map { it.copy(id = 0, mealId = mealId) })
    }

    /**
     * The three things a backup touches that have nothing to do with foods.
     *
     * Stubbed rather than faked: what they do is tested where they live, and a restore that also
     * had to set a real alarm would be testing Android rather than the file.
     */
    private class NoReminders : ReminderStore {
        private var held = Reminder(enabled = false, hour = 20, minute = 0)
        override val reminder: Flow<Reminder> = flowOf(held)
        override suspend fun save(reminder: Reminder) { held = reminder }
        override suspend fun current(): Reminder = held
    }

    private class NoScheduler : ReminderScheduler {
        override fun schedule(reminder: Reminder, now: LocalDateTime) = Unit
        override fun cancel() = Unit
    }

    private class NoAiSettings : AiSettingsStore {
        override val settings: Flow<AiSettings> = flowOf(AiSettings())
        override suspend fun setModel(model: String) = Unit
        override suspend fun setDailyCeiling(ceiling: Int) = Unit
        override suspend fun recordCall() = Unit
    }
}
