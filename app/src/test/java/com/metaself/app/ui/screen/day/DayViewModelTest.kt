package com.metaself.app.ui.screen.day

import com.metaself.app.data.day.DeletedEntry
import com.metaself.app.data.day.DetachedRow
import com.metaself.app.data.day.InMemoryMealRepository
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.backup.DailyBackup
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.data.movement.StepSource
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.food.LoggedFoods
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.ToLog
import com.metaself.app.data.day.MealRepository
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentHour
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.streak.ConsistencyFigure
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.repeat.withAmount
import com.metaself.app.domain.window.DayMeasured
import com.metaself.app.domain.window.DayWindow
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.target.TargetRevision
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.food.MealWording
import com.metaself.app.ui.screen.repeat.LoggedMeal
import com.metaself.app.ui.day.DayTotalsWording
import com.metaself.app.ui.window.WindowWording
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with nothing logged, the whole target is left`() = runTest {
        val viewModel = viewModel(meals = emptyList())

        val ready = viewModel.state.first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.remaining.kcal).isEqualTo(2090)
        assertThat(ready.meals).isEmpty()
    }

    @Test
    fun `what is logged comes off what is left`() = runTest {
        val viewModel = viewModel(meals = listOf(aMeal(items = listOf(anItem(kcal = 600)))))

        val ready = viewModel.state.first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.remaining.kcal).isEqualTo(1490)
        assertThat(ready.meals).hasSize(1)
    }

    @Test
    fun `logging something adds it to today, not to some other day`() = runTest {
        val meals = FakeMealRepository()
        val viewModel = viewModel(mealRepository = meals)

        viewModel.log(anItem(name = "Hummus", kcal = 180))
        advanceUntilIdle()

        assertThat(meals.logged.single().epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(meals.logged.single().items.single().name).isEqualTo("Hummus")
    }

    @Test
    fun `deleting an item asks the store to delete it`() = runTest {
        val meals = FakeMealRepository()
        val viewModel = viewModel(mealRepository = meals)

        viewModel.deleteItem(anItem(id = 7))
        advanceUntilIdle()

        assertThat(meals.deleted).containsExactly(7L)
    }

    @Test
    fun `the app opens on today`() = runTest {
        val viewModel = viewModel()

        val ready = viewModel.state.first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(ready.isToday).isTrue()
    }

    @Test
    fun `choosing another day shows that day's meals`() = runTest {
        val yesterday = TEST_EPOCH_DAY - 1
        val meals = FakeMealRepository(
            listOf(
                aMeal(epochDay = TEST_EPOCH_DAY, items = listOf(anItem(name = "Today's lunch"))),
                aMeal(epochDay = yesterday, items = listOf(anItem(name = "Yesterday's dinner"))),
            ),
        )
        val viewModel = viewModel(mealRepository = meals)

        viewModel.showDay(yesterday)
        advanceUntilIdle()

        val ready = viewModel.state.first { it is DayUiState.Ready } as DayUiState.Ready
        assertThat(ready.epochDay).isEqualTo(yesterday)
        assertThat(ready.meals.single().items.single().name).isEqualTo("Yesterday's dinner")
        assertThat(ready.isToday).isFalse()
    }

    @Test
    fun `logging goes to the day being looked at, not to today`() = runTest {
        val meals = FakeMealRepository()
        val viewModel = viewModel(mealRepository = meals)

        viewModel.showDay(TEST_EPOCH_DAY - 3)
        advanceUntilIdle()
        viewModel.log(anItem(name = "A meal from three days ago"))
        advanceUntilIdle()

        assertThat(meals.logged.single().epochDay).isEqualTo(TEST_EPOCH_DAY - 3)
    }

    @Test
    fun `a future day can be logged to`() = runTest {
        val meals = FakeMealRepository()
        val viewModel = viewModel(mealRepository = meals)

        viewModel.showDay(TEST_EPOCH_DAY + 7)
        advanceUntilIdle()
        viewModel.log(anItem(name = "A booking"))
        advanceUntilIdle()

        assertThat(meals.logged.single().epochDay).isEqualTo(TEST_EPOCH_DAY + 7)
    }

    @Test
    fun `correcting an item asks the store to correct it`() = runTest {
        val meals = FakeMealRepository()
        val viewModel = viewModel(mealRepository = meals)

        val before = anItem(id = 7, name = "Hummus")
        viewModel.correctItem(before, before.copy(name = "Hummus, large"))
        advanceUntilIdle()

        assertThat(meals.updated.single().name).isEqualTo("Hummus, large")
    }

    @Test
    fun `with no profile stored there is nothing to be over or under`() = runTest {
        val viewModel = viewModel(profile = null)

        assertThat(viewModel.state.first { it !is DayUiState.Loading })
            .isInstanceOf(DayUiState.NeedsSetup::class.java)
    }

    /**
     * A packet printing half a gram of fat, with figures no whole-gram row can be worked back to
     * (issue #28). Unbranded, so it meets an unbranded food of the same name.
     */
    private val rice = Product(
        barcode = "2000000000011",
        name = "Rice cakes",
        brand = null,
        kcalPer100g = 387.4,
        proteinPer100g = 8.3,
        carbsPer100g = 81.6,
        fatPer100g = 0.5,
    )

    /**
     * The day keeps D38's whole grams, and the food keeps the label as printed: two numbers that
     * are each true of what they describe, rather than the food believing a rounded row (D4).
     */
    @Test
    fun `a scanned packet logged at 30 g is a whole-gram row, and its food keeps the label's figures`() =
        runTest {
            val meals = FakeMealRepository()
            val foods = FakeFoodRepository()
            val model = viewModel(mealRepository = meals, foods = foods)

            model.logScanned(rice.toFoodItem(30.0)!!, rice)
            advanceUntilIdle()

            val food = foods.current.single()
            val row = meals.logged.single().items.single()
            assertThat(row.fatG).isEqualTo(0)
            assertThat(row.source).isEqualTo(Source.LABEL)
            assertThat(row.foodId).isEqualTo(food.id)
            assertThat(food.facts.per100g!!.nutrients).isEqualTo(Nutrients(387.4, 8.3, 81.6, 0.5))
            assertThat(food.facts.per100g!!.provenance.source).isEqualTo(Source.LABEL)
            assertThat(food.facts.per100g!!.provenance.confidence).isNull()
            assertThat(food.barcode).isEqualTo(rice.barcode)
        }

    /** At exactly 100 g the row rounds up to 1 g, and the food still holds what the label says. */
    @Test
    fun `the same packet logged as 100 g is 1 g on the day and still half a gram on the food`() = runTest {
        val meals = FakeMealRepository()
        val foods = FakeFoodRepository()
        val model = viewModel(mealRepository = meals, foods = foods)

        model.logScanned(rice.toFoodItem(100.0)!!, rice)
        advanceUntilIdle()

        assertThat(meals.logged.single().items.single().fatG).isEqualTo(1)
        assertThat(foods.current.single().facts.per100g!!.nutrients.fatG).isEqualTo(0.5)
    }

    /** A REGRESSION GUARD for the everyday path: only a scan carries a label to hand over. */
    @Test
    fun `something typed and logged still teaches its food from the row`() = runTest {
        val meals = FakeMealRepository()
        val foods = FakeFoodRepository()
        val model = viewModel(mealRepository = meals, foods = foods)

        model.log(anItem(name = "Toast", portionAmount = 30.0, portionUnit = "g", fatG = 1))
        advanceUntilIdle()

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients.fatG).isWithin(1e-9).of(100.0 / 30.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
    }

    /**
     * Issue #7: a food saved with "Infinity" before 0.32.6 has that group cleared when the app
     * opens — its other group kept — and a row already logged from it keeps its figures (D42).
     */
    @Test
    fun `opening the app clears an impossible figure on a food, and no logged row moves`() = runTest {
        val broken = aFood(
            name = "Protein bar",
            facts = FoodFacts(per100g = aPer100g(kcal = Double.POSITIVE_INFINITY), perUnit = aPerUnit()),
        )
        val foods = FakeFoodRepository(listOf(broken))
        val logged = anItem(id = 7, name = "Protein bar", kcal = Int.MAX_VALUE).copy(foodId = 1)
        val meals = FakeMealRepository(listOf(aMeal(id = 1, items = listOf(logged))))

        viewModel(mealRepository = meals, foods = foods)
        advanceUntilIdle()

        val food = foods.current.single()
        assertThat(food.facts.per100g).isNull()
        assertThat(food.facts.perUnit).isEqualTo(aPerUnit())
        assertThat(meals.updated).isEmpty()
        assertThat(meals.deleted).isEmpty()
    }

    private fun viewModel(
        profile: Profile? = aProfile(),
        meals: List<Meal> = emptyList(),
        mealRepository: MealRepository = FakeMealRepository(meals),
        profiles: FakeProfileRepository = FakeProfileRepository(profile),
        weights: FakeWeightRepository = FakeWeightRepository(),
        savedMeals: FakeSavedMealRepository = FakeSavedMealRepository(),
        foods: FakeFoodRepository = FakeFoodRepository(),
        // Mid-afternoon on the test day, the same moment `currentHour` reports. Defaulted so that
        // every test written before stretches existed keeps the view model it always had.
        now: Now = Now { atHour(TEST_EPOCH_DAY, 15) },
        // The calendar day, overridable for the one test in which the night passes.
        today: Today = this.today,
        problems: ProblemLog = ProblemLog.NONE,
    ) = DayViewModel(
        profiles = profiles,
        meals = mealRepository,
        weights = weights,
        // Making a meal out of a day needs both: the foods the chosen rows point at, and somewhere
        // to put the meal that comes out of them.
        savedMeals = savedMeals,
        foods = foods,
        today = today,
        // A stretch is open or closed depending on how long ago its last input was, so the moment
        // is supplied rather than read from a clock inside the view model — the pattern `Today` and
        // `CurrentHour` already set.
        now = now,
        currentYear = CurrentYear { TEST_YEAR },
        // Mid-afternoon: after any sensible window has opened, so the yesterday check runs.
        currentHour = CurrentHour { 15 },
        // The daily copy has nowhere to write in a test and nothing here depends on it.
        automaticBackup = object : DailyBackup {
            override suspend fun runIfDue(today: LocalDate, nowMillis: Long) = null
        },
        // No Health Connect in a test, which the app treats exactly as it treats an ordinary day.
        steps = object : StepSource {
            override suspend fun access() = StepAccess.UNAVAILABLE
            override suspend fun history(from: LocalDate, to: LocalDate) = emptyList<DayMovement>()
        },
        // Attaching a row to its food is exercised where it lives; here it only has to happen.
        // The same foods the view model reads, so a row attached while logging or correcting is
        // attached to a food the rest of the test can see.
        loggedFoods = LoggedFoods(foods),
        problems = problems,
    )

    @Test
    fun `opening the app a week after the first weight revises the target`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        val weights = FakeWeightRepository(
            (0..7).map { WeightReading(epochDay = TEST_EPOCH_DAY - 7 + it, kg = 79.0) },
        )

        viewModel(profiles = profiles, weights = weights).state.first { it is DayUiState.Ready }
        advanceUntilIdle()

        assertThat(profiles.revisions).hasSize(1)
        assertThat(profiles.revisions.single().trendKg).isWithin(0.01).of(79.0)
    }

    @Test
    fun `one reading is not enough to move the target`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        val weights = FakeWeightRepository(listOf(WeightReading(TEST_EPOCH_DAY, kg = 74.0)))

        viewModel(profiles = profiles, weights = weights).state.first { it is DayUiState.Ready }
        advanceUntilIdle()

        assertThat(profiles.revisions).isEmpty()
    }

    @Test
    fun `the target shown follows the revision, not the setup weight`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 80.0),
            initialRevision = TargetRevision(
                TEST_EPOCH_DAY - 1,
                trendKg = 74.0,
                kcal = 0,
                previousKcal = null,
            ),
        )

        val ready = viewModel(profiles = profiles).state
            .first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.target.kcal).isEqualTo(
            CurrentTarget.of(aProfile(weightKg = 80.0), profiles.storedRevision, TEST_YEAR).kcal,
        )
    }

    @Test
    fun `a change the owner has not seen is offered as a notice`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(),
            initialRevision = TargetRevision(TEST_EPOCH_DAY, 79.2, 2050, 2090),
            initialSeen = false,
        )

        val ready = viewModel(profiles = profiles).state
            .first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.targetChangeNotice).isNotNull()
    }

    @Test
    fun `a change already seen is not offered again`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(),
            initialRevision = TargetRevision(TEST_EPOCH_DAY, 79.2, 2050, 2090),
            initialSeen = true,
        )

        val ready = viewModel(profiles = profiles).state
            .first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.targetChangeNotice).isNull()
    }

    @Test
    fun `dismissing the notice records that it was seen`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(),
            initialRevision = TargetRevision(TEST_EPOCH_DAY, 79.2, 2050, 2090),
            initialSeen = false,
        )
        val viewModel = viewModel(profiles = profiles)

        viewModel.dismissTargetChange()
        advanceUntilIdle()

        assertThat(profiles.seenMarked).isTrue()
    }

    private class FakeWeightRepository(
        initial: List<WeightReading> = emptyList(),
        /** Every read throws, for the once-per-open work that reads the weights. */
        failing: Boolean = false,
    ) : com.metaself.app.data.weight.WeightRepository {
        private val state = MutableStateFlow(initial)
        override val readings: Flow<List<WeightReading>> =
            if (failing) flow { throw IllegalStateException("disk full") } else state
        override suspend fun log(reading: WeightReading) {
            state.value = state.value.filterNot { it.epochDay == reading.epochDay } + reading
        }
        override suspend fun delete(epochDay: Long) {
            state.value = state.value.filterNot { it.epochDay == epochDay }
        }
    }

    
    /**
     * Reaching the goal weight, which the owner decided should celebrate for one day and then switch
     * the goal to holding by itself (D21).
     */
    @Test
    fun `reaching the goal records it and switches to holding`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 82.0, goal = Goal.lose(0.5, targetKg = 70.0)),
        )
        viewModel(profiles = profiles, weights = FakeWeightRepository(listOf(WeightReading(TEST_EPOCH_DAY, 69.5))))
        advanceUntilIdle()

        assertThat(profiles.arrivals.single().targetKg).isEqualTo(70.0)
        assertThat(profiles.arrivals.single().epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(profiles.storedProfile!!.goal).isEqualTo(Goal.hold())
    }

    /**
     * A milestone is announced once and never again. Once the goal is held there is no target to
     * arrive at, so a second open finds nothing to celebrate.
     */
    @Test
    fun `opening the app again after arriving celebrates nothing further`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 82.0, goal = Goal.lose(0.5, targetKg = 70.0)),
        )
        val weights = FakeWeightRepository(listOf(WeightReading(TEST_EPOCH_DAY, 69.5)))

        viewModel(profiles = profiles, weights = weights)
        advanceUntilIdle()
        viewModel(profiles = profiles, weights = weights)
        advanceUntilIdle()

        assertThat(profiles.arrivals).hasSize(1)
    }

    @Test
    fun `a weight still short of the goal celebrates nothing`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 82.0, goal = Goal.lose(0.5, targetKg = 70.0)),
        )
        viewModel(profiles = profiles, weights = FakeWeightRepository(listOf(WeightReading(TEST_EPOCH_DAY, 80.0))))
        advanceUntilIdle()

        assertThat(profiles.arrivals).isEmpty()
        assertThat(profiles.storedProfile!!.goal.targetKg).isEqualTo(70.0)
    }

    @Test
    fun `the celebration says what it reached and what the target has become`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 70.0, goal = Goal.hold()),
            initialArrival = GoalArrival(targetKg = 70.0, epochDay = TEST_EPOCH_DAY),
        )
        val model = viewModel(profiles = profiles)

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.goalReached).isNotNull()
        assertThat(ready.goalReached).contains("70 kg")
        assertThat(ready.goalReached).contains("${ready.target.kcal} kcal")
    }

    /** Gone the next morning. */
    @Test
    fun `yesterday's arrival is not celebrated today`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 70.0, goal = Goal.hold()),
            initialArrival = GoalArrival(targetKg = 70.0, epochDay = TEST_EPOCH_DAY - 1),
        )
        val model = viewModel(profiles = profiles)

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.goalReached).isNull()
    }

    @Test
    fun `the day screen carries the run counted from the record`() = runTest {
        val meals = (0..4).map { aMeal(epochDay = TEST_EPOCH_DAY - it) }
        val model = viewModel(meals = meals)

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready

        assertThat(ready.streak.currentDays).isEqualTo(5)
        assertThat(ready.streak.lifetimeDays).isEqualTo(5)
    }

    /**
     * Decision D13, end to end: a stored counter loses a run to any unlogged day, and the resolution
     * was a record that can be completed rather than a counter with a forgiveness rule.
     */
    @Test
    fun `filling in a missed day repairs the run with no other action`() = runTest {
        val withHole = listOf(0L, 1L, 3L, 4L).map { aMeal(epochDay = TEST_EPOCH_DAY - it) }
        val store = FakeMealRepository(withHole)
        val model = viewModel(mealRepository = store)

        assertThat(
            (model.state.first { it is DayUiState.Ready } as DayUiState.Ready).streak.currentDays,
        ).isEqualTo(2)

        store.log(aMeal(epochDay = TEST_EPOCH_DAY - 2))
        advanceUntilIdle()

        assertThat(model.state.value.let { it as DayUiState.Ready }.streak.currentDays).isEqualTo(5)
    }

    // --- the one consistency figure (D52) ------------------------------------------------------

    /** Thirty days in a row, ending today: 30 is a milestone and not a multiple of seven. */
    @Test
    fun `a run reaching thirty today is a milestone on the day`() = runTest {
        val model = watched(viewModel(meals = (0L..29L).map { aMeal(epochDay = TEST_EPOCH_DAY - it) }))

        assertThat(ready(model).consistency)
            .isEqualTo(ConsistencyFigure.Run(days = 30, milestone = true))
    }

    /**
     * Seven days ending today: the weekly congratulation fires, so the figure stays plain and the
     * milestone is said once — and it stays plain once the congratulation is dismissed.
     */
    @Test
    fun `on the day the weekly congratulation fires, the figure stays plain`() = runTest {
        val model = watched(viewModel(meals = (0L..6L).map { aMeal(epochDay = TEST_EPOCH_DAY - it) }))

        assertThat(ready(model).encouragement).isEqualTo("A week of logging, unbroken.")
        assertThat(ready(model).consistency)
            .isEqualTo(ConsistencyFigure.Run(days = 7, milestone = false))

        model.dismissEncouragement()
        advanceUntilIdle()

        assertThat(ready(model).encouragement).isNull()
        assertThat(ready(model).consistency)
            .isEqualTo(ConsistencyFigure.Run(days = 7, milestone = false))
    }

    /** The morning after, before the first logging: the run stands, the milestone was yesterday's. */
    @Test
    fun `a milestone run that ended yesterday is plain`() = runTest {
        val model = watched(viewModel(meals = (1L..30L).map { aMeal(epochDay = TEST_EPOCH_DAY - it) }))

        assertThat(ready(model).consistency)
            .isEqualTo(ConsistencyFigure.Run(days = 30, milestone = false))
    }

    /** A past day's page carries the same figure, but the milestone belongs to today's page. */
    @Test
    fun `a past day's page shows the milestone plainly`() = runTest {
        val model = watched(viewModel(meals = (0L..29L).map { aMeal(epochDay = TEST_EPOCH_DAY - it) }))

        model.showDay(TEST_EPOCH_DAY - 3)
        advanceUntilIdle()

        assertThat(ready(model).consistency)
            .isEqualTo(ConsistencyFigure.Run(days = 30, milestone = false))
    }

    /** A gap two days ago leaves a run of two, so the month speaks instead. */
    @Test
    fun `a short run gives way to the thirty-day count`() = runTest {
        val days = listOf(0L, 1L, 3L, 4L, 5L, 40L)
        val model = watched(viewModel(meals = days.map { aMeal(epochDay = TEST_EPOCH_DAY - it) }))

        assertThat(ready(model).consistency).isEqualTo(ConsistencyFigure.Recent(days = 5))
    }

    @Test
    fun `passing the first kilogram records it and says so today`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 82.0, goal = Goal.lose(0.5, targetKg = 70.0)),
        )
        val weights = FakeWeightRepository(
            listOf(
                WeightReading(TEST_EPOCH_DAY - 20, 82.0),
                WeightReading(TEST_EPOCH_DAY, 80.5),
            ),
        )

        val model = viewModel(profiles = profiles, weights = weights)
        advanceUntilIdle()

        assertThat(profiles.milestones.first().keys).contains(Milestone.firstKg)
        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready
        assertThat(ready.milestoneReached).contains("first kilogram")
    }

    /**
     * A milestone — the first kilogram, say — is announced once and never again. A second app open
     * must find nothing new to say, and a weight that drifts back up and down again must stay
     * silent.
     */
    @Test
    fun `a milestone already announced is never announced again`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 82.0, goal = Goal.lose(0.5, targetKg = 70.0)),
        )
        val readings = listOf(
            WeightReading(TEST_EPOCH_DAY - 20, 82.0),
            WeightReading(TEST_EPOCH_DAY, 80.5),
        )

        viewModel(profiles = profiles, weights = FakeWeightRepository(readings))
        advanceUntilIdle()
        viewModel(profiles = profiles, weights = FakeWeightRepository(readings))
        advanceUntilIdle()

        assertThat(profiles.milestoneWrites).isEqualTo(1)
    }

    @Test
    fun `yesterday's milestone is not shown today`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 82.0, goal = Goal.lose(0.5, targetKg = 70.0)),
            initialMilestones = mapOf(Milestone.firstKg to TEST_EPOCH_DAY - 1),
        )
        val model = viewModel(
            profiles = profiles,
            weights = FakeWeightRepository(
                listOf(
                    WeightReading(TEST_EPOCH_DAY - 20, 82.0),
                    WeightReading(TEST_EPOCH_DAY, 80.5),
                ),
            ),
        )
        advanceUntilIdle()

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready
        assertThat(ready.milestoneReached).isNull()
    }

    /** D22: nothing is ever said about going the wrong way. */
    @Test
    fun `a weight going the wrong way records nothing`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(weightKg = 80.0, goal = Goal.lose(0.5, targetKg = 70.0)),
        )
        viewModel(
            profiles = profiles,
            weights = FakeWeightRepository(
                listOf(
                    WeightReading(TEST_EPOCH_DAY - 20, 80.0),
                    WeightReading(TEST_EPOCH_DAY, 82.0),
                ),
            ),
        )
        advanceUntilIdle()

        assertThat(profiles.milestoneWrites).isEqualTo(0)
    }

    /**
     * The owner's one firm requirement about the eating window: it applies from a chosen day
     * forwards and never backwards. A rule set today must not re-score yesterday.
     */
    @Test
    fun `an eating window set today leaves yesterday alone`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        // A late dinner yesterday, logged as it was eaten.
        val lateYesterday = aMeal(
            epochDay = TEST_EPOCH_DAY - 1,
            loggedAtMillis = LocalDate.ofEpochDay(TEST_EPOCH_DAY - 1)
                .atTime(23, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        profiles.addWindowRule(WindowRule.Fixed(EatingWindow(6, 20, fromEpochDay = TEST_EPOCH_DAY)))

        val model = viewModel(profiles = profiles, meals = listOf(lateYesterday))
        model.showDay(TEST_EPOCH_DAY - 1)
        advanceUntilIdle()

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready
        assertThat(ready.verdict).isNull()
    }

    @Test
    fun `a late meal today is noticed`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        val lateToday = aMeal(
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
                .atTime(22, 30)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        profiles.addWindowRule(WindowRule.Fixed(EatingWindow(6, 20, fromEpochDay = TEST_EPOCH_DAY)))

        val model = viewModel(profiles = profiles, meals = listOf(lateToday))
        advanceUntilIdle()

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready
        // The fixed kind is a COUNT verdict, and reading it as one is a cast rather than a rename:
        // "meals outside" is not on the shared verdict type and never will be, because those words
        // do not describe a span.
        val verdict = ready.verdict as DayWindow
        assertThat(verdict.mealsOutside).isEqualTo(1)
        assertThat(verdict.kept).isFalse()
    }

    /**
     * The same requirement for the measured kind, at the level the owner would actually see it.
     *
     * Everything needed to score yesterday is already in the record; only the decision not to
     * prevents it, which is exactly why this is asserted rather than assumed.
     */
    @Test
    fun `a ratio set today leaves yesterday alone`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        val earlyYesterday = aMeal(
            epochDay = TEST_EPOCH_DAY - 1,
            loggedAtMillis = atHour(TEST_EPOCH_DAY - 1, 9),
        )
        val lateYesterday = aMeal(
            epochDay = TEST_EPOCH_DAY - 1,
            loggedAtMillis = atHour(TEST_EPOCH_DAY - 1, 23),
        )
        profiles.addWindowRule(
            WindowRule.Measured(MeasuredWindow(fastingHours = 16), fromEpochDay = TEST_EPOCH_DAY),
        )

        val model = viewModel(profiles = profiles, meals = listOf(earlyYesterday, lateYesterday))
        model.showDay(TEST_EPOCH_DAY - 1)
        advanceUntilIdle()

        val ready = model.state.first { it is DayUiState.Ready } as DayUiState.Ready
        assertThat(ready.verdict).isNull()
    }

    // --- the measured window judges stretches, not days (design §3) --------------------------

    /**
     * Fourteen hours fasting, ten eating, in force for a fortnight.
     *
     * Far enough back that nothing below is gated by the rule's start day; the gate itself is
     * proved in `WindowRulesTest` and the two tests above.
     */
    private suspend fun aRatioInForce(
        profiles: FakeProfileRepository,
        fastingHours: Int = 14,
    ) {
        profiles.addWindowRule(
            WindowRule.Measured(
                window = MeasuredWindow(fastingHours = fastingHours),
                fromEpochDay = TEST_EPOCH_DAY - 13,
            ),
        )
    }

    /** One day's eating, from [fromHour] to [toHour]: two inputs, so it can be judged. */
    private fun aStretchOn(epochDay: Long, fromHour: Int = 10, toHour: Int = 18): List<Meal> =
        listOf(
            aMeal(epochDay = epochDay, loggedAtMillis = atHour(epochDay, fromHour)),
            aMeal(epochDay = epochDay, loggedAtMillis = atHour(epochDay, toHour)),
        )

    /**
     * The day shows the stretches that STARTED on it (design §3).
     *
     * The old verdict was a span over the calendar day. This one is a list, because a day can hold
     * more than one stretch and — see the next test — it can hold none of its own at all.
     */
    @Test
    fun `the day shows the stretches that began on it`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 9)),
                    aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 13)),
                ),
            ),
        )

        val measured = ready(model).verdict as DayMeasured
        assertThat(measured.stretches).hasSize(1)
        assertThat(measured.stretches.single().stretch.startedOnEpochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(measured.stretches.single().stretch.inputs).isEqualTo(2)
    }

    /**
     * A stretch belongs to the day it began on, and never to two (design §3).
     *
     * Dinner at 22:00 and a last thing at 00:30 are one stretch of two and a half hours. Giving it
     * to both days would let one late dinner break two of them; so today — which holds an input but
     * no stretch of its OWN — gets no verdict, and yesterday gets the whole of it.
     *
     * This is also the test that forces the day screen to read past the day it is showing: the
     * input that opens the stretch is on the previous date.
     */
    @Test
    fun `a stretch begun yesterday and ended today puts no verdict on today`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        val yesterday = TEST_EPOCH_DAY - 1

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(epochDay = yesterday, loggedAtMillis = atHour(yesterday, 22)),
                    aMeal(
                        epochDay = TEST_EPOCH_DAY,
                        loggedAtMillis = atTime(TEST_EPOCH_DAY, 0, 30),
                    ),
                ),
            ),
        )

        assertThat((ready(model).verdict as DayMeasured).stretches).isEmpty()

        model.showDay(yesterday)
        advanceUntilIdle()

        val begun = (ready(model).verdict as DayMeasured).stretches.single()
        assertThat(begun.stretch.startedOnEpochDay).isEqualTo(yesterday)
        assertThat(begun.stretch.inputs).isEqualTo(2)
    }

    /**
     * A ratio in force lends the ring nothing, on today and on any day paged back to.
     *
     * DELIBERATELY CHANGED. The stretch rule briefly reported open-or-shut for a ratio
     * as well — a stretch is open until the fast completes, which is a state of the same shape the
     * fixed hours have. The consequence was drawn on the wrong day: paging back to a day the FIXED
     * hours had governed put a ring beside that day's tally whose fill came from whether eating is
     * going on right now, which is a different thing from the one that day was judged by, and
     * sitting where it sits it reads as belonging to that day (design §4, corrected 2026-09-17).
     *
     * So the ring is the fixed hours' alone. The state is null while a ratio governs, here with
     * eating open two hours ago — the answer the ring would have drawn is emphatically available,
     * and is deliberately not reported. Nothing on screen is lost by it: a measured day draws its
     * own mark, and a fixed day paged back to still draws its tally.
     */
    @Test
    fun `a ratio in force gives the ring no state, whichever day is on screen`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 13)),
                ),
            ),
        )

        // The stretch IS open: today's verdict holds it, and it has not closed.
        val stretches = (ready(model).verdict as DayMeasured).stretches
        assertThat(stretches.single().closed).isFalse()
        assertThat(ready(model).windowOpenNow).isNull()

        model.showDay(TEST_EPOCH_DAY - 1)
        advanceUntilIdle()

        assertThat(ready(model).windowOpenNow).isNull()
    }

    /**
     * The ring the fixed hours own still answers, which is the half that must not have been lost.
     *
     * The clock reads mid-afternoon in every test here. Hours of 06:00 to 20:00 contain it and the
     * ring is filled; hours of 16:00 to 22:00 do not and it is drawn empty. Withholding the state
     * under a ratio (above) must not have become withholding it altogether.
     */
    @Test
    fun `the fixed hours in force still say whether the window is open now`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        profiles.addWindowRule(
            WindowRule.Fixed(EatingWindow(6, 20, fromEpochDay = TEST_EPOCH_DAY - 10)),
        )

        assertThat(ready(watched(viewModel(profiles = profiles))).windowOpenNow).isTrue()

        val shut = FakeProfileRepository(aProfile())
        theHoursInForce(shut, fromEpochDay = TEST_EPOCH_DAY - 10)

        assertThat(ready(watched(viewModel(profiles = shut))).windowOpenNow).isFalse()
    }

    /**
     * The tally on the day counts stretches, not days (design §3.1).
     *
     * Chosen so the two answers cannot agree. Eating at 18:00 and again at 01:00 is ONE stretch of
     * seven hours, kept on a 14/10 — but two calendar days of one input each, and a day of one
     * input is not judged at all. Days say nothing happened; stretches say one was judged and kept.
     */
    @Test
    fun `the tally on the day counts stretches, not days`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        val evening = TEST_EPOCH_DAY - 4

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(epochDay = evening, loggedAtMillis = atHour(evening, 18)),
                    aMeal(epochDay = evening + 1, loggedAtMillis = atHour(evening + 1, 1)),
                ),
            ),
        )

        assertThat(ready(model).windowJudged).isEqualTo(1)
        assertThat(ready(model).windowKept).isEqualTo(1)
    }

    /**
     * A meal with no trustworthy hour is still admitted to, now from the stretch walk's own count.
     *
     * It rides on the day's verdict for the reason it always did: the screen prints it beside the
     * window's line, and the two numbers have to have come from one reading of the same meals. The
     * meal itself is dropped before any stretch is worked out (design §2.3), so it neither opens a
     * stretch nor stretches a real one.
     */
    @Test
    fun `a meal written down on another day is admitted on its own day, and left out of the stretches`() =
        runTest {
            val profiles = FakeProfileRepository(aProfile())
            aRatioInForce(profiles)
            val filedOn = TEST_EPOCH_DAY - 2

            val model = watched(
                viewModel(
                    profiles = profiles,
                    meals = listOf(
                        aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 9)),
                        aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 13)),
                        // Typed today, but filed against a day two days ago: the hour is the hour it
                        // was TYPED, which is not when it was eaten.
                        aMeal(epochDay = filedOn, loggedAtMillis = atHour(TEST_EPOCH_DAY, 14)),
                    ),
                ),
            )

            // Today's own meals are both timed, and today says nothing about the one two days back.
            // It used to: the count was the walk's, and the walk reads the days either side — so
            // today admitted a meal whose "--:--" is two pages away, and since D33 told him to tap
            // a time that is not on the page (found by review, 2026-09-18).
            val today = ready(model).verdict as DayMeasured
            assertThat(today.mealsUntimed).isEqualTo(0)
            assertThat(today.stretches.single().stretch.inputs).isEqualTo(2)

            model.showDay(filedOn)
            advanceUntilIdle()
            assertThat((ready(model).verdict as DayMeasured).mealsUntimed).isEqualTo(1)
        }

    /**
     * A stretch that runs past the last day the screen read is STILL OPEN, not a measured span.
     *
     * The screen reads a small bounded range — two days back, two days forward, never past today.
     * A stretch longer than that has its last input CUT OFF by where the read stopped, so the walk
     * sees an input that looks final, decides the fast has long since completed, and would state a
     * span that is an artefact of the range rather than a measurement of the eating. D4 forbids
     * exactly that, and design §3 and §5 say an open stretch is not judged.
     *
     * Reachable, not hypothetical: 16/8, breakfast at 08:00 and dinner at 20:00 every day. Every
     * gap is twelve hours, under the sixteen-hour fast, so the whole month is ONE stretch that
     * never closes. Open the day it began on and the honest answer is "still open".
     */
    @Test
    fun `a stretch that outruns the days read is still open, and states no span`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        profiles.addWindowRule(
            WindowRule.Measured(
                window = MeasuredWindow(fastingHours = 16),
                fromEpochDay = TEST_EPOCH_DAY - 40,
            ),
        )
        val began = TEST_EPOCH_DAY - 30

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = aMonthOfNeverFasting(began),
            ),
        )

        model.showDay(began)
        advanceUntilIdle()

        val measured = ready(model).verdict as DayMeasured
        val stretch = measured.stretches.single()

        assertThat(stretch.stretch.startedOnEpochDay).isEqualTo(began)
        assertThat(stretch.closed).isFalse()
        assertThat(stretch.judged).isFalse()
        assertThat(stretch.kept).isFalse()
        // The two sentences the screen would print, asked the way the screen asks them.
        assertThat(WindowWording.span(stretch, measured.window)).isNull()
        assertThat(
            WindowWording.stillOpen(
                stretch,
                measured.window,
                isToday = false,
            ),
        )
            .isEqualTo("Still open — nothing is judged until you have fasted 16 hours.")
    }


    /**
     * The silence a review found, and the one thing now said into it (design §3.0a).
     *
     * A ratio whose fast is never actually reached — 16/8 with breakfast at 08:00 and dinner at
     * 20:00, every gap twelve hours — is ONE stretch that never closes. So no day has a stretch of
     * its own, nothing is ever judged, the tally is 0 of 0, and the app said nothing at all to
     * exactly the person failing to keep the ratio he set himself. That is the mirror image of the
     * problem the whole stretch rule exists to fix.
     *
     * TODAY now says how long it has been, about the stretch that is open RIGHT NOW and whichever
     * day that stretch began on. Thirty days and seven hours by mid-afternoon: 727h against a ratio
     * allowing eight. Nothing is judged by it — the stretch is still open, so it is in neither half
     * of the tally and neither kept nor broken.
     *
     * The month is not decoration. The stretch began thirty days before the day the sentence is
     * said on, so a view model that read only the days around the one on screen would state a
     * duration that was an artefact of where its read stopped, which is the one thing D4 forbids.
     */
    @Test
    fun `today says how long a stretch that never closes has been running`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        profiles.addWindowRule(
            WindowRule.Measured(
                window = MeasuredWindow(fastingHours = 16),
                fromEpochDay = TEST_EPOCH_DAY - 40,
            ),
        )
        val began = TEST_EPOCH_DAY - 30

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = aMonthOfNeverFasting(began),
            ),
        )
        advanceUntilIdle()

        // From 08:00 thirty days ago to this morning's 08:00, every gap twelve hours: thirty days of
        // eating, said in hours. It first read "727h", which was 08:00 thirty days ago to 15:00 NOW —
        // the seven hours since breakfast counted as eating (2026-09-18) — and the fixture also held
        // a 20:00 meal today, five hours after the test's own clock.
        assertThat(ready(model).isToday).isTrue()
        // Now the over-run is said with the time he can act on (D32): a sixteen-hour fast from this
        // morning's 08:00 breakfast completes at midnight.
        assertThat(ready(model).ratioNow).isEqualTo(
            "You've eaten for 720h 0m — next meal from 00:00 tomorrow if you're keeping your fast.",
        )
        // Judged by nothing: no day of the month has a closed stretch, so both halves of the tally
        // are still zero and no day is kept or broken.
        assertThat(ready(model).windowKept).isEqualTo(0)
        assertThat(ready(model).windowJudged).isEqualTo(0)
        assertThat((ready(model).verdict as DayMeasured).judged).isFalse()
    }

    /**
     * And it is said on today only, however the owner pages about.
     *
     * The day the stretch BEGAN keeps the sentence it already had — still open, nothing judged
     * until the fast completes — and no span is stated anywhere, because no span has been measured.
     * The live line is a fact about this moment and would be a lie on a day thirty days gone.
     */
    @Test
    fun `the day the stretch began still says only that it is open`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        profiles.addWindowRule(
            WindowRule.Measured(
                window = MeasuredWindow(fastingHours = 16),
                fromEpochDay = TEST_EPOCH_DAY - 40,
            ),
        )
        val began = TEST_EPOCH_DAY - 30

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = aMonthOfNeverFasting(began),
            ),
        )

        model.showDay(began)
        advanceUntilIdle()

        val shown = ready(model)
        val measured = shown.verdict as DayMeasured
        val stretch = measured.stretches.single()

        assertThat(shown.ratioNow).isNull()
        assertThat(WindowWording.span(stretch, measured.window)).isNull()
        assertThat(
            WindowWording.stillOpen(
                stretch,
                measured.window,
                isToday = false,
            ),
        )
            .isEqualTo("Still open — nothing is judged until you have fasted 16 hours.")
    }

    /**
     * Eating well inside the ratio, today says when the last meal should be (D32).
     *
     * Eating from 10:00 on a 14/10, looked at at 15:00: five hours in, the window closes at 20:00,
     * and that — as a condition, since he may already be done — is the one thing said.
     */
    @Test
    fun `a stretch still inside its eating hours says when the last meal should be`() =
        runTest {
            val profiles = FakeProfileRepository(aProfile())
            aRatioInForce(profiles)

            val model = watched(
                viewModel(
                    profiles = profiles,
                    meals = listOf(
                        aMeal(
                            epochDay = TEST_EPOCH_DAY,
                            loggedAtMillis = atHour(TEST_EPOCH_DAY, 10),
                        ),
                        aMeal(
                            epochDay = TEST_EPOCH_DAY,
                            loggedAtMillis = atHour(TEST_EPOCH_DAY, 14),
                        ),
                    ),
                ),
            )
            advanceUntilIdle()

            assertThat(ready(model).ratioNow)
                .isEqualTo("If you're keeping your window, last meal by 20:00.")
        }

    /**
     * A morning after a kept stretch, end to end: today says when eating may resume, not that the
     * hours were run over.
     *
     * A 14/10, with invented times. Yesterday's eating ran 08:00 to 17:00 — nine hours, inside the
     * ten — with nothing logged since. At 05:00 the stretch is still open, correctly: fourteen hours
     * from 17:00 is 07:00. The defect this pins: the live line once counted from the first input to
     * NOW — here 21h — with the whole night's fast counted as eating.
     *
     * Today began no stretch, and under D32 says the time that can be acted on: fourteen hours from
     * 17:00 is 07:00. Yesterday, the day the stretch began, carries its own sentence: still open.
     */
    @Test
    fun `a stretch that stopped in time tells him when he may eat the next morning`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        val yesterday = TEST_EPOCH_DAY - 1

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(epochDay = yesterday, loggedAtMillis = atTime(yesterday, 8, 0)),
                    aMeal(epochDay = yesterday, loggedAtMillis = atTime(yesterday, 12, 0)),
                    aMeal(epochDay = yesterday, loggedAtMillis = atTime(yesterday, 17, 0)),
                ),
                now = Now { atTime(TEST_EPOCH_DAY, 5, 0) },
            ),
        )
        advanceUntilIdle()

        // Today began no stretch, and says the one thing that is true and useful at 05:00: when the
        // fast allows the next meal.
        val today = ready(model)
        assertThat(today.isToday).isTrue()
        assertThat(today.ratioNow)
            .isEqualTo("Good morning. If you're keeping your fast, next meal from 07:00.")
        assertThat((today.verdict as DayMeasured).stretches).isEmpty()

        model.showDay(yesterday)
        advanceUntilIdle()

        val shown = ready(model)
        val measured = shown.verdict as DayMeasured
        val stretch = measured.stretches.single()
        assertThat(stretch.closed).isFalse()
        assertThat(shown.ratioNow).isNull()
        assertThat(WindowWording.span(stretch, measured.window)).isNull()
        assertThat(
            WindowWording.stillOpen(
                stretch,
                measured.window,
                isToday = false,
            ),
        ).isEqualTo("Still open — nothing is judged until you have fasted 14 hours.")
    }

    // --- D33: when a meal was eaten, set on the day ------------------------------------------

    /**
     * A meal's time can be put where it was eaten, and the day moves it.
     *
     * Logged at 11:00, eaten at 08:00: the ordinary case, and the one every time the ratio
     * tells depends on. The meal stays on its day; only its time changes.
     */
    @Test
    fun `a meal's time can be set to when it was eaten`() = runTest {
        val model = watched(
            viewModel(
                meals = listOf(
                    aMeal(id = 1, epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 11)),
                ),
            ),
        )
        val meal = ready(model).meals.single()

        model.setEatenAt(meal, hour = 8, minute = 0)
        advanceUntilIdle()

        val moved = ready(model).meals.single()
        assertThat(moved.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(moved.loggedAtMillis).isEqualTo(atHour(TEST_EPOCH_DAY, 8))
        assertThat(DayTotalsWording.eatenAt(moved, java.time.ZoneId.systemDefault())).isEqualTo("08:00")
    }

    /**
     * Something missed, written down later onto an earlier day, counts once its time is set.
     *
     * The case the requirement names. Typed today but filed two days ago, it carries
     * the moment of typing — another date — so the window leaves it out as untimed. Given the time
     * he ate it, it joins that day's eating.
     */
    @Test
    fun `a meal written down later counts in the window once its time is set`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        val earlier = TEST_EPOCH_DAY - 2

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(id = 1, epochDay = earlier, loggedAtMillis = atHour(earlier, 9)),
                    aMeal(id = 2, epochDay = earlier, loggedAtMillis = atHour(earlier, 13)),
                    aMeal(id = 3, epochDay = earlier, loggedAtMillis = atHour(TEST_EPOCH_DAY, 14)),
                ),
            ),
        )
        model.showDay(earlier)
        advanceUntilIdle()
        val before = ready(model).verdict as DayMeasured
        assertThat(before.mealsUntimed).isEqualTo(1)
        assertThat(before.stretches.single().stretch.inputs).isEqualTo(2)

        model.setEatenAt(ready(model).meals.single { it.id == 3L }, hour = 17, minute = 30)
        advanceUntilIdle()

        val after = ready(model).verdict as DayMeasured
        assertThat(after.mealsUntimed).isEqualTo(0)
        assertThat(after.stretches.single().stretch.inputs).isEqualTo(3)
        assertThat(after.stretches.single().stretch.lastAtMillis)
            .isEqualTo(atTime(earlier, 17, 30))
    }

    /**
     * A time that has not happened yet is refused, and nothing moves.
     *
     * The clock reads 15:00. A meal put at 16:00 would hold open a fast that has closed, and every
     * time the ratio says would follow it.
     */
    @Test
    fun `a time that has not happened yet is refused`() = runTest {
        val model = watched(
            viewModel(
                meals = listOf(
                    aMeal(id = 1, epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 11)),
                ),
            ),
        )
        val meal = ready(model).meals.single()

        model.setEatenAt(meal, hour = 16, minute = 0)
        advanceUntilIdle()

        assertThat(ready(model).refusal)
            .isEqualTo("16:00 has not happened yet. A meal can only be put at a time already past.")
        assertThat(ready(model).meals.single().loggedAtMillis).isEqualTo(atHour(TEST_EPOCH_DAY, 11))
    }

    /**
     * A morning after the app was left alive overnight opens on the NEW today.
     *
     * Found by review, 2026-09-18. The view model belongs to the activity, which Android may keep
     * overnight, and "today" was fixed when it was built: the next morning the page headed "Today"
     * was yesterday, so there was no "Good morning" and breakfast was filed onto the day before.
     * Coming to the front on a new date moves the day screen's today with it.
     */
    @Test
    fun `coming back on a new date moves today with it`() = runTest {
        var date = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
        var clock = atHour(TEST_EPOCH_DAY, 22)
        val model = watched(viewModel(today = Today { date }, now = Now { clock }))
        assertThat(model.calendarToday.value).isEqualTo(TEST_EPOCH_DAY)

        date = date.plusDays(1)
        clock = atHour(TEST_EPOCH_DAY + 1, 7)
        model.lookedAt()
        advanceUntilIdle()

        assertThat(model.calendarToday.value).isEqualTo(TEST_EPOCH_DAY + 1)
    }

    /**
     * The tally catches up when he comes back, not only when something is logged.
     *
     * A stretch whose fast completes while the app sits in the background is judged at that moment,
     * and nothing in the record changes to say so. It was counted only when a meal next changed, so
     * the morning showed "Your 14 hours were up" beside a tally one stretch behind it (review,
     * 2026-09-18). One moment now serves the sentence, the tally and the day's verdicts.
     */
    @Test
    fun `the tally counts a fast that completed while he was away`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        var clock = atHour(TEST_EPOCH_DAY, 22)
        var date = LocalDate.ofEpochDay(TEST_EPOCH_DAY)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = listOf(
                    aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 9)),
                    aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 17)),
                ),
                now = Now { clock },
                today = Today { date },
            ),
        )
        advanceUntilIdle()
        assertThat(ready(model).windowJudged).isEqualTo(0)

        // 17:00 plus fourteen hours is 07:00: the fast is done by 08:00 the next morning, and
        // nothing was logged. The date moves with the clock, as it does on the phone — a read that
        // stopped at yesterday could not see the fast end, and would rightly leave it open.
        clock = atHour(TEST_EPOCH_DAY + 1, 8)
        date = date.plusDays(1)
        model.lookedAt()
        advanceUntilIdle()

        assertThat(ready(model).windowJudged).isEqualTo(1)
        assertThat(ready(model).windowKept).isEqualTo(1)
    }

    /**
     * The follower of the open stretch is a stream, and a stream that threw has stopped: the
     * sentence and the tally would stay as they were for as long as the app was open. Coming back to
     * the screen starts it again.
     *
     * The read that throws is armed only once the app has opened, and is the tally's first day — two
     * days before the ratio began — so what it stops is the follower, woken by a change to the
     * record, and nothing else.
     */
    @Test
    fun `a stretch follower that threw starts again when he comes back`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        var clock = atHour(TEST_EPOCH_DAY, 22)
        var date = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
        val inner = FakeMealRepository(
            listOf(aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 9))),
        )
        var brokenDay: Long? = null
        val onceBroken = object : MealRepository by inner {
            override fun observeDay(epochDay: Long): Flow<List<Meal>> =
                if (epochDay == brokenDay) {
                    brokenDay = null
                    flow { throw IllegalStateException("disk full") }
                } else {
                    inner.observeDay(epochDay)
                }
        }
        val problems = RecordingProblemLog()
        val model = watched(
            viewModel(
                profiles = profiles,
                mealRepository = onceBroken,
                now = Now { clock },
                today = Today { date },
                problems = problems,
            ),
        )
        advanceUntilIdle()

        brokenDay = TEST_EPOCH_DAY - 15
        inner.log(aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 17)))
        advanceUntilIdle()
        assertThat(problems.recorded.map { it.kind }).containsExactly("refused")

        // As in the tally test above: the fast is done by morning, and only a live follower can
        // say so.
        clock = atHour(TEST_EPOCH_DAY + 1, 8)
        date = date.plusDays(1)
        model.lookedAt()
        advanceUntilIdle()

        assertThat(ready(model).windowJudged).isEqualTo(1)
        assertThat(ready(model).windowKept).isEqualTo(1)
    }

    /**
     * The tally is said only on days the ratio governed — never on a day before it began.
     *
     * The defect this pins: the streak of stretches appeared on dates before the ratio even
     * started. The count is of stretches since the ratio was set, so on a day before that it is a
     * count of something that had not started. Paging back past the rule's first day shows none.
     */
    @Test
    fun `no tally is shown on a day before the ratio began`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        val ruleBegan = TEST_EPOCH_DAY - 13
        val meals = (ruleBegan..TEST_EPOCH_DAY - 1).flatMap { day ->
            listOf(
                aMeal(epochDay = day, loggedAtMillis = atHour(day, 9)),
                aMeal(epochDay = day, loggedAtMillis = atHour(day, 17)),
            )
        }

        val model = watched(viewModel(profiles = profiles, meals = meals))
        advanceUntilIdle()
        assertThat(ready(model).ratioTally).startsWith("Kept ")

        model.showDay(ruleBegan)
        advanceUntilIdle()
        assertThat(ready(model).ratioTally).isNotNull()

        model.showDay(ruleBegan - 1)
        advanceUntilIdle()
        assertThat(ready(model).ratioTally).isNull()
        assertThat(ready(model).windowJudged).isEqualTo(0)
    }

    // --- issue #22: correcting a row keeps the food it is --------------------------------------

    /** A corrected figure changes the row, never which food it is. */
    @Test
    fun `correcting a figure keeps the row's food`() = runTest {
        val repository = FakeMealRepository()
        val model = watched(viewModel(mealRepository = repository))
        val before = anItem(id = 5, name = "Cucumber", kcal = 15).copy(foodId = 7)

        model.correctItem(before, before.copy(kcal = 18))
        advanceUntilIdle()

        assertThat(repository.updated.last().kcal).isEqualTo(18)
        assertThat(repository.updated.last().foodId).isEqualTo(7)
    }

    /**
     * A new name is a claim that it was a different food, so the row goes to that food.
     *
     * Exactly as logging it under that name would: found when it exists, made when it does not.
     */
    @Test
    fun `renaming a row moves it to the food with the new name`() = runTest {
        val repository = FakeMealRepository()
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber"), aFood(name = "Feta")))
        val feta = foods.current.single { it.name == "Feta" }
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val before = anItem(id = 5, name = "Cucumber").copy(foodId = foods.current.first().id)

        model.correctItem(before, before.copy(name = "Feta"))
        advanceUntilIdle()

        assertThat(repository.updated.last().foodId).isEqualTo(feta.id)
    }

    /**
     * A row attached to nothing, corrected under the same name, stays attached to nothing.
     *
     * Its food may have been deleted on purpose — deleting a food detaches its rows by design — and
     * correcting a figure is no reason to bring the food back into the list.
     */
    @Test
    fun `correcting a detached row does not bring a deleted food back`() = runTest {
        val repository = FakeMealRepository()
        val foods = FakeFoodRepository()
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val before = anItem(id = 5, name = "Halva", kcal = 200)

        model.correctItem(before, before.copy(kcal = 210))
        advanceUntilIdle()

        assertThat(repository.updated.last().foodId).isNull()
        assertThat(foods.current).isEmpty()
    }

    // --- issue #29: renaming a scanned row teaches no figure under the label's name -------------

    /** The packet's own figures per 100 g, as a scan leaves them on its food. */
    private val riceLabel = PerHundredGrams(
        Nutrients(387.4, 8.3, 81.6, 0.5),
        Provenance(Source.LABEL, null, setAtMillis = 0),
    )

    /**
     * Rice cakes as a scan left them — the food holding the label, the row of 30 g on the day
     * attached to it — plus whatever else [others] puts in the list. The row is D38's: whole grams,
     * fat 0, LABEL.
     */
    private fun scannedRiceCakes(vararg others: Food): Pair<FakeFoodRepository, FoodItem> {
        val foods = FakeFoodRepository(
            listOf(aFood(name = "Rice cakes", facts = FoodFacts(per100g = riceLabel))) + others,
        )
        val riceFood = foods.current.single { it.name == "Rice cakes" }
        return foods to rice.toFoodItem(30.0)!!.copy(id = 5, foodId = riceFood.id)
    }

    /**
     * *Correct this item*, new name: the row goes to that food (#22) with nothing but its own
     * rounded numbers, so the per-100 g the new food learns is worked back from 0 g of fat. It is
     * filed as copied from a past meal, never as the packet's (D4, D43) — and the row itself is
     * untouched, still the label's on the day.
     */
    @Test
    fun `renaming a scanned row makes a food whose figure is copied, not the label's`() = runTest {
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes()
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.copy(name = "Puffed rice"))
        advanceUntilIdle()

        val puffed = foods.current.single { it.name == "Puffed rice" }
        val row = repository.updated.last()
        assertThat(row.foodId).isEqualTo(puffed.id)
        assertThat(row.source).isEqualTo(Source.LABEL)
        assertThat(row.fatG).isEqualTo(0)

        val per100g = puffed.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(per100g.provenance.rank).isLessThan(Provenance.rankOf(Source.TYPED))
        assertThat(per100g.nutrients.fatG).isWithin(1e-9).of(0.0)

        // The food the row came from keeps the packet's figures exactly.
        assertThat(foods.current.single { it.name == "Rice cakes" }.facts.per100g).isEqualTo(riceLabel)
    }

    /** Before D43 the worked-back 0.0 arrived as LABEL, outranked his 0.5, and replaced it. */
    @Test
    fun `renaming a scanned row onto a food he typed leaves his figures alone`() = runTest {
        val typed = PerHundredGrams(
            Nutrients(380.0, 8.0, 80.0, 0.5),
            Provenance(Source.TYPED, null, setAtMillis = 0),
        )
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes(aFood(name = "Puffed rice", facts = FoodFacts(per100g = typed)))
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.copy(name = "Puffed rice"))
        advanceUntilIdle()

        val puffed = foods.current.single { it.name == "Puffed rice" }
        assertThat(repository.updated.last().foodId).isEqualTo(puffed.id)
        assertThat(puffed.facts.per100g).isEqualTo(typed)
    }

    /** A blank is filled, as copied; what one of it is worth — his number — is not touched. */
    @Test
    fun `renaming a scanned row onto a food with no per-100 g fills the blank`() = runTest {
        val perUnit = aPerUnit("cake", 35.0)
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes(aFood(name = "Puffed rice", facts = FoodFacts(perUnit = perUnit)))
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.copy(name = "Puffed rice"))
        advanceUntilIdle()

        val puffed = foods.current.single { it.name == "Puffed rice" }
        assertThat(repository.updated.last().foodId).isEqualTo(puffed.id)
        val per100g = puffed.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(116 * 100 / 30.0)
        assertThat(puffed.facts.perUnit).isEqualTo(perUnit)
        assertThat(puffed.facts.gramsPerUnit).isNull()
    }

    /**
     * #22's guarantee survives: the renamed row still points at a food that can be weighed, so it
     * can be gathered into a meal. The meal repository here records a correction rather than
     * performing it, so the corrected row is put on the day by hand for the second screen.
     */
    @Test
    fun `a renamed scanned row can still join a meal`() = runTest {
        val corrections = FakeMealRepository()
        val (foods, before) = scannedRiceCakes()
        val renaming = watched(viewModel(mealRepository = corrections, foods = foods))
        renaming.correctItem(before, before.copy(name = "Puffed rice"))
        advanceUntilIdle()
        val renamed = corrections.updated.last()
        val puffed = foods.current.single { it.name == "Puffed rice" }
        assertThat(renamed.foodId).isEqualTo(puffed.id)

        val mealRepository = FakeMealRepository(listOf(aMeal(id = 9, items = listOf(renamed))))
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val model = watched(
            viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(5)
        model.makeMealFromChosen("Snack")
        advanceUntilIdle()

        assertThat(ready(model).refusal).isNull()
        val built = savedMeals.current.single()
        assertThat(built.components).hasSize(1)
        val component = built.components.single()
        assertThat(component.food.id).isEqualTo(puffed.id)
        assertThat(component.countedAs).isEqualTo(CountedAs.GRAMS)
        assertThat(component.amount).isEqualTo(30.0)
        assertThat(mealRepository.gathered.single().second).containsExactly(5L)
    }

    // --- issue #35: a figure he changes in Correct this item is his, not the packet's (D44) ------

    /**
     * The issue's own case, through the one place a correction becomes a stored row.
     *
     * Everything else about the row is the correction's business and must survive untouched — in
     * particular the food it is, which corrections used to drop silently (issue #22).
     */
    @Test
    fun `correcting the calories of a scanned row stores it as his number`() = runTest {
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes()
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.copy(kcal = 140))
        advanceUntilIdle()

        val row = repository.updated.last()
        assertThat(row.source).isEqualTo(Source.TYPED)
        assertThat(row.confidence).isNull()
        assertThat(row.kcal).isEqualTo(140)
        assertThat(row.foodId).isEqualTo(before.foodId)
    }

    /**
     * The amount is not a figure: the editor rescales 30 g to 60 g by itself, and a packet's reading
     * of twice as much is still the packet's reading.
     */
    @Test
    fun `correcting only the amount of a scanned row leaves it the label's`() = runTest {
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes()
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.withAmount(60.0))
        advanceUntilIdle()

        val row = repository.updated.last()
        assertThat(row.source).isEqualTo(Source.LABEL)
        assertThat(row.kcal).isEqualTo(232)
    }

    /**
     * The interaction with D43 (#29), and the reason the correction has to be applied BEFORE the row
     * reaches the food list.
     *
     * D43 demotes a figure worked back from a label row to *copied from a past meal*, because the
     * row is rounded to whole grams and so is not what the packet printed. Once he has corrected a
     * figure, nobody is claiming the packet printed it — he stated it — so the food learns a TYPED
     * number, ranked as anything he types is. The food the row came from is not touched by any of
     * this and keeps the packet's own figures.
     */
    @Test
    fun `correcting a figure and renaming teaches the new food his own number`() = runTest {
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes()
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.copy(name = "Puffed rice", kcal = 140))
        advanceUntilIdle()

        val puffed = foods.current.single { it.name == "Puffed rice" }
        val row = repository.updated.last()
        assertThat(row.source).isEqualTo(Source.TYPED)
        assertThat(row.foodId).isEqualTo(puffed.id)

        val per100g = puffed.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(per100g.provenance.rank).isEqualTo(Provenance.rankOf(Source.TYPED))
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(140 * 100 / 30.0)

        assertThat(foods.current.single { it.name == "Rice cakes" }.facts.per100g).isEqualTo(riceLabel)
    }

    /**
     * His newer number replaces his older one, as one he typed from scratch always has.
     *
     * The ranking's tie is resolved in favour of the newer figure, so a corrected row landing on a
     * food that already holds a typed per-100 g replaces it — where the same rename with no figure
     * touched leaves it alone (the neighbouring test above), because a copied figure ranks below a
     * typed one. A real label figure still outranks both and is still safe.
     */
    @Test
    fun `correcting a figure and renaming onto a food he typed replaces his older figure`() = runTest {
        val typed = PerHundredGrams(
            Nutrients(380.0, 8.0, 80.0, 0.5),
            Provenance(Source.TYPED, null, setAtMillis = 0),
        )
        val repository = FakeMealRepository()
        val (foods, before) = scannedRiceCakes(
            aFood(name = "Puffed rice", facts = FoodFacts(per100g = typed)),
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.correctItem(before, before.copy(name = "Puffed rice", kcal = 140))
        advanceUntilIdle()

        val puffed = foods.current.single { it.name == "Puffed rice" }
        assertThat(repository.updated.last().foodId).isEqualTo(puffed.id)
        val per100g = puffed.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(140 * 100 / 30.0)
    }

    /**
     * **D4 regression, end to end — passes before this change and must keep passing.** Correcting a
     * guess by hand does not make it a measurement, and it keeps the confidence it was guessed with.
     */
    @Test
    fun `correcting an estimate through the day leaves it an estimate`() = runTest {
        val repository = FakeMealRepository()
        val model = watched(viewModel(mealRepository = repository))
        val before = anItem(id = 5, source = Source.AI_ESTIMATE, confidence = Confidence.LOW)

        model.correctItem(before, before.copy(kcal = 640))
        advanceUntilIdle()

        val row = repository.updated.last()
        assertThat(row.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(row.confidence).isEqualTo(Confidence.LOW)
        assertThat(row.kcal).isEqualTo(640)
    }

    // --- the weekly compliment, counted in stretches (design §3.2) ----------------------------

    /**
     * Seven closed judged stretches, every one of them kept.
     *
     * The unit has moved and the spirit has not: a whole run of success or nothing at all. The
     * stretches sit on the days eight to two back, so yesterday holds none — which keeps the
     * compliment about the RUN rather than about yesterday, an occasion of its own that would
     * otherwise take the day's one slot before this one was reached.
     */
    @Test
    fun `the weekly compliment fires on seven closed judged stretches, all kept`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = (2L..8L).flatMap { aStretchOn(TEST_EPOCH_DAY - it) },
            ),
        )

        assertThat(ready(model).encouragement).isEqualTo("Seven days, seven inside your window.")
    }

    /** Six is not seven, and a partial run has never been worth saying anything about. */
    @Test
    fun `six kept stretches say nothing`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = (3L..8L).flatMap { aStretchOn(TEST_EPOCH_DAY - it) },
            ),
        )

        assertThat(ready(model).encouragement).isNull()
    }

    /**
     * One broken stretch in the seven and it stays silent.
     *
     * The broken one runs 08:00 to 19:00 — eleven hours against a ratio allowing ten. Its hours are
     * chosen so it is still a stretch of its OWN: fourteen hours exactly after the previous
     * evening's last input, and fifteen before the next morning's first.
     */
    @Test
    fun `one broken stretch in the seven silences the compliment`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = (2L..8L).flatMap { back ->
                    if (back == 5L) {
                        aStretchOn(TEST_EPOCH_DAY - back, fromHour = 8, toHour = 19)
                    } else {
                        aStretchOn(TEST_EPOCH_DAY - back)
                    }
                },
            ),
        )

        assertThat(ready(model).encouragement).isNull()
    }

    /**
     * One day's eating well inside a 16:00 to 22:00 window: two meals, so the day is judged.
     *
     * The hours start at 16:00 while the test clock reads 15:00, which is deliberate. Today's
     * window has therefore not opened yet, so the YESTERDAY compliment — which comes first and
     * would otherwise take the day's one slot — is not in play, and these tests are about the week.
     */
    private fun aDayInsideTheHours(epochDay: Long): List<Meal> = listOf(
        aMeal(epochDay = epochDay, loggedAtMillis = atHour(epochDay, 17)),
        aMeal(epochDay = epochDay, loggedAtMillis = atHour(epochDay, 21)),
    )

    /** Fixed hours of 16:00 to 22:00, beginning on a chosen day. */
    private suspend fun theHoursInForce(profiles: FakeProfileRepository, fromEpochDay: Long) {
        profiles.addWindowRule(
            WindowRule.Fixed(EatingWindow(16, 22, fromEpochDay = fromEpochDay)),
        )
    }

    /**
     * A week the owner switched kinds in earns no compliment, because seven judged days cannot
     * exist in it.
     *
     * The run is counted in the unit the rule in force counts in, and the fixed hours count DAYS.
     * Counting seven days back regardless of when those hours began pulled in the days a RATIO had
     * governed and scored each of them from its own meals alone, with the rule that says where a
     * read was cut switched off. Here the ratio days are exactly that trap: eating at 20:00 and
     * 22:00 reads as a kept two-hour stretch, and the record has it running on to noon the next day
     * — sixteen hours, against a ratio that allows ten. Seven flattering days, and a compliment he
     * had not earned.
     *
     * The consequence of clamping is accepted rather than worked around: in the week of a switch
     * the compliment is silent, because there are not seven days the hours in force actually
     * governed. Silence is the honest answer there.
     *
     * Today is logged as well so the logging run is eight days rather than seven — a run of exactly
     * seven is an occasion of its own and would answer this test for the wrong reason.
     */
    @Test
    fun `a week the owner switched kinds in earns no compliment`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        theHoursInForce(profiles, fromEpochDay = TEST_EPOCH_DAY - 3)

        val ratioDays = listOf(
            aMeal(epochDay = TEST_EPOCH_DAY - 7, loggedAtMillis = atHour(TEST_EPOCH_DAY - 7, 20)),
            aMeal(epochDay = TEST_EPOCH_DAY - 7, loggedAtMillis = atHour(TEST_EPOCH_DAY - 7, 22)),
            aMeal(epochDay = TEST_EPOCH_DAY - 6, loggedAtMillis = atHour(TEST_EPOCH_DAY - 6, 8)),
            aMeal(epochDay = TEST_EPOCH_DAY - 6, loggedAtMillis = atHour(TEST_EPOCH_DAY - 6, 12)),
        ) + aStretchOn(TEST_EPOCH_DAY - 5) + aStretchOn(TEST_EPOCH_DAY - 4)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = ratioDays +
                    (1L..3L).flatMap { aDayInsideTheHours(TEST_EPOCH_DAY - it) } +
                    aDayInsideTheHours(TEST_EPOCH_DAY),
            ),
        )

        assertThat(ready(model).encouragement).isNull()
    }

    /**
     * The other half of the clamp: a run the hours in force really did govern still earns it.
     *
     * The silence above is a consequence of counting only days the rule was actually in force for,
     * and it must not have become a silence about everything. The hours here began three weeks ago,
     * so every one of the seven days ending yesterday is theirs.
     */
    @Test
    fun `seven days under hours that governed them all still earns the compliment`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        theHoursInForce(profiles, fromEpochDay = TEST_EPOCH_DAY - 20)

        val model = watched(
            viewModel(
                profiles = profiles,
                meals = (1L..7L).flatMap { aDayInsideTheHours(TEST_EPOCH_DAY - it) } +
                    aDayInsideTheHours(TEST_EPOCH_DAY),
            ),
        )

        assertThat(ready(model).encouragement).isEqualTo("Seven days, seven inside your window.")
    }

    /**
     * Breakfast at 08:00 and dinner at 20:00 every day from [began], and today's breakfast — never a
     * gap long enough to be a fast on a 16/8, so one stretch that never closes.
     *
     * Today stops at breakfast because the test's clock reads 15:00: a 20:00 meal today would be
     * five hours in the future, and the stretch would then be measured to a meal not yet eaten.
     */
    private fun aMonthOfNeverFasting(began: Long): List<Meal> =
        (began until TEST_EPOCH_DAY).flatMap { day ->
            listOf(
                aMeal(epochDay = day, loggedAtMillis = atHour(day, 8)),
                aMeal(epochDay = day, loggedAtMillis = atHour(day, 20)),
            )
        } + aMeal(epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 8))

    private fun atHour(epochDay: Long, hour: Int): Long = atTime(epochDay, hour, 0)

    private fun atTime(epochDay: Long, hour: Int, minute: Int): Long = LocalDate.ofEpochDay(epochDay)
        .atTime(hour, minute)
        .atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    // --- issue #25: Undo puts back the entry that was deleted, as it was ------------------------
    //
    // Against the PERFORMING stand-in, because what undo puts back is only visible on a day that
    // actually changes. Every meal and row seeded carries a distinct, non-zero id, because the
    // assertions name ids.

    /** The performing stand-in, reading through the same foods and saved-meal titles as the test. */
    private fun performing(
        meals: List<Meal>,
        foods: FakeFoodRepository = FakeFoodRepository(),
        titles: MutableStateFlow<Map<Long, String>> = MutableStateFlow(emptyMap()),
    ) = InMemoryMealRepository(meals, foods = foods, mealTitles = titles)

    @Test
    fun `an undone deletion comes back at the time it was eaten`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = TEST_EPOCH_DAY,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(anItem(id = 10, name = "Eggs")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        assertThat(ready(model).meals).isEmpty()
        model.undoDelete()
        advanceUntilIdle()

        val back = ready(model).meals.single()
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.loggedAtMillis).isEqualTo(atHour(TEST_EPOCH_DAY, 9))
        assertThat(back.items.single().id).isEqualTo(10L)
    }

    @Test
    fun `an undone deletion goes back into the meal it came from`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single { it.id == 11L })
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        val meal = ready(model).meals.single()
        assertThat(meal.id).isEqualTo(1L)
        assertThat(meal.items.map { it.id }).containsExactly(10L, 11L).inOrder()
    }

    @Test
    fun `an undone deletion that had emptied its meal brings the meal back whole`() = runTest {
        val foods = FakeFoodRepository()
        val titles = MutableStateFlow(mapOf(3L to "Greek salad"))
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 12),
                    note = "written down later",
                    savedMealId = 3,
                    savedMealAdjusted = true,
                    items = listOf(anItem(id = 10, name = "Greek salad", kcal = 320)),
                ),
            ),
            foods,
            titles,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val before = ready(model).meals.single()

        model.deleteItem(before.items.single())
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        val back = ready(model).meals.single()
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.loggedAtMillis).isEqualTo(atHour(TEST_EPOCH_DAY, 12))
        assertThat(back.note).isEqualTo("written down later")
        assertThat(back.savedMealId).isEqualTo(3L)
        assertThat(back.savedMealName).isEqualTo("Greek salad")
        assertThat(back.savedMealAdjusted).isTrue()
        assertThat(back).isEqualTo(before)
    }

    /**
     * The case the issue was found by: undo stamped the row with the moment of undoing, which on a
     * past day is another date, so the window left it out as untimed.
     */
    @Test
    fun `an undone deletion on a past day stays timed`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        aRatioInForce(profiles)
        val past = TEST_EPOCH_DAY - 2
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = past,
                    loggedAtMillis = atHour(past, 9),
                    items = listOf(anItem(id = 10, name = "Eggs")),
                ),
                aMeal(
                    id = 2,
                    epochDay = past,
                    loggedAtMillis = atHour(past, 13),
                    items = listOf(anItem(id = 11, name = "Hummus")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(profiles = profiles, mealRepository = repository, foods = foods))
        model.showDay(past)
        advanceUntilIdle()
        val before = ready(model).verdict as DayMeasured
        assertThat(before.mealsUntimed).isEqualTo(0)
        assertThat(before.stretches.single().stretch.inputs).isEqualTo(2)

        model.deleteItem(ready(model).meals.single { it.id == 2L }.items.single())
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        val after = ready(model).verdict as DayMeasured
        assertThat(after.mealsUntimed).isEqualTo(0)
        assertThat(after.stretches.single().stretch.inputs).isEqualTo(2)
    }

    @Test
    fun `an undone deletion goes back to the day it was deleted from`() = runTest {
        val threeDaysAgo = TEST_EPOCH_DAY - 3
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = threeDaysAgo,
                    loggedAtMillis = atHour(threeDaysAgo, 13),
                    items = listOf(anItem(id = 10, name = "Hummus")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.showDay(threeDaysAgo)
        advanceUntilIdle()
        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        model.showDay(TEST_EPOCH_DAY)
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        // Deleted from three days ago, so that is where undoing it puts it back — even though the
        // owner has since moved to another day.
        assertThat(ready(model).meals).isEmpty()
        val back = repository.observeDay(threeDaysAgo).first().single()
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.loggedAtMillis).isEqualTo(atHour(threeDaysAgo, 13))
        assertThat(back.items.single().id).isEqualTo(10L)
    }

    /**
     * Restoring is not logging: a detached row stays detached, and no food is made for it.
     *
     * The discriminating half of the plan's test 21 — under the old undo, which re-logged through
     * the food list, this row found or made a food called Hummus.
     */
    @Test
    fun `undoing a deletion does not log the food again`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 12),
                    items = listOf(
                        anItem(id = 10, name = "Hummus", portionAmount = 100.0, portionUnit = "g", kcal = 180),
                    ),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        val back = ready(model).meals.single().items.single()
        assertThat(back.id).isEqualTo(10L)
        assertThat(back.foodId).isNull()
        assertThat(foods.foodIdsNamed("Hummus")).isEmpty()
        assertThat(foods.current).isEmpty()
    }

    /**
     * Restoring offers the row's figures to nothing: the food it points at is exactly as it was.
     *
     * A REGRESSION GUARD, not a discriminator: the old undo passed this too, because the food list
     * already leaves an attached row alone. It is here so a later change cannot start doing so.
     */
    @Test
    fun `undoing the deletion of a row with a food leaves that food as it was`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Hummus")))
        val hummus = foods.current.single()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 12),
                    items = listOf(
                        // Figures far from what the food knows, so an offer would show.
                        anItem(id = 10, name = "Hummus", portionAmount = 100.0, portionUnit = "g", kcal = 600)
                            .copy(foodId = hummus.id),
                    ),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val foodsBefore = foods.current

        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        assertThat(foods.current).isEqualTo(foodsBefore)
        assertThat(ready(model).meals.single().items.single().foodId).isEqualTo(hummus.id)
    }

    @Test
    fun `an item put back cannot be put back twice`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(aMeal(id = 1, loggedAtMillis = atHour(TEST_EPOCH_DAY, 12), items = listOf(anItem(id = 10)))),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        model.undoDelete()
        model.undoDelete()
        advanceUntilIdle()

        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).containsExactly(10L)
    }

    /**
     * Tidying up two rows and then changing his mind used to cost the first one for good: a single
     * receipt, overwritten by the second delete. Both come back now, most recent first.
     */
    @Test
    fun `two rows deleted can both be put back, the later one first`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single { it.id == 10L })
        advanceUntilIdle()
        model.deleteItem(ready(model).meals.single().items.single { it.id == 11L })
        advanceUntilIdle()
        assertThat(ready(model).meals).isEmpty()

        model.undoDelete()
        advanceUntilIdle()
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).containsExactly(11L)

        model.undoDelete()
        advanceUntilIdle()
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id })
            .containsExactly(10L, 11L)
    }

    /**
     * Everything ticked goes in one act (#50), and every row of it is still recoverable one at a
     * time — because each one went through the same delete a row deleted on its own goes through.
     * A single "delete these" in the store would have been one receipt for several rows and a
     * second thing for Undo to know how to reverse.
     */
    @Test
    fun `deleting what is chosen takes every ticked row, each still recoverable`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(
                        anItem(id = 10, name = "Eggs"),
                        anItem(id = 11, name = "Tomato"),
                        anItem(id = 12, name = "Coffee"),
                    ),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.beginChoosing(10)
        model.toggleChosen(11)
        model.deleteChosen()
        advanceUntilIdle()

        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).containsExactly(12L)
        // The choice is spent: the bar must not go on offering a meal made of rows that have gone.
        assertThat(ready(model).chosen).isEmpty()
        assertThat(model.canUndo.value).isTrue()

        model.undoDelete()
        advanceUntilIdle()
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).containsExactly(11L, 12L)

        model.undoDelete()
        advanceUntilIdle()
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id })
            .containsExactly(10L, 11L, 12L)
    }

    /** Nothing ticked, nothing deleted — and nothing offered to put back either. */
    @Test
    fun `deleting what is chosen with nothing chosen does nothing`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(anItem(id = 10), anItem(id = 11)),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteChosen()
        advanceUntilIdle()

        assertThat(ready(model).meals.flatMap { it.items }).hasSize(2)
        assertThat(model.canUndo.value).isFalse()
    }

    /** What the Undo line reads from: something to put back, or nothing. */
    @Test
    fun `there is nothing to undo until something is deleted, and nothing again once it is all back`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(anItem(id = 10), anItem(id = 11)),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        assertThat(model.canUndo.value).isFalse()

        model.deleteItem(ready(model).meals.single().items.single { it.id == 10L })
        advanceUntilIdle()
        model.deleteItem(ready(model).meals.single().items.single { it.id == 11L })
        advanceUntilIdle()
        assertThat(model.canUndo.value).isTrue()

        model.undoDelete()
        advanceUntilIdle()
        assertThat(model.canUndo.value).isTrue()

        model.undoDelete()
        advanceUntilIdle()
        assertThat(model.canUndo.value).isFalse()
    }

    /** Undo waits for its own delete's receipt rather than acting on whatever was there before. */
    @Test
    fun `undo pressed before the deletion finished puts back that row`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single { it.id == 11L })
        // No advanceUntilIdle() here: the delete has not run yet when Undo is pressed.
        model.undoDelete()
        advanceUntilIdle()

        val meal = ready(model).meals.single()
        assertThat(meal.id).isEqualTo(1L)
        assertThat(meal.items.map { it.id }).containsExactly(10L, 11L).inOrder()
    }

    /**
     * The other half of the plan's test 23: a second delete's Undo, pressed before that delete ran,
     * puts back the SECOND row — not the first, whose snackbar was ignored.
     */
    @Test
    fun `undo pressed before a second deletion finished puts back the second row, not the first`() =
        runTest {
            val foods = FakeFoodRepository()
            val repository = performing(
                listOf(
                    aMeal(
                        id = 1,
                        loggedAtMillis = atHour(TEST_EPOCH_DAY, 9),
                        items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                    ),
                ),
                foods,
            )
            val model = watched(viewModel(mealRepository = repository, foods = foods))
            val (eggs, tomato) = ready(model).meals.single().items

            model.deleteItem(eggs)
            advanceUntilIdle()
            model.deleteItem(tomato)
            model.undoDelete()
            advanceUntilIdle()

            val meal = ready(model).meals.single()
            assertThat(meal.id).isEqualTo(1L)
            assertThat(meal.items.map { it.id }).containsExactly(11L)
        }

    /**
     * A delete cancelled before it hands back its receipt — the screen going away mid-delete — still
     * lets a waiting Undo finish, with nothing to put back. Without the `finally` in `deleteItem`
     * the Undo would wait for ever: it is counted here as a child of the view model's scope that
     * never finishes.
     */
    @Test
    fun `undo after a cancelled deletion finishes and puts nothing back`() = runTest {
        val foods = FakeFoodRepository()
        val restored = mutableListOf<DeletedEntry>()
        val performing = performing(
            listOf(aMeal(id = 1, loggedAtMillis = atHour(TEST_EPOCH_DAY, 12), items = listOf(anItem(id = 10)))),
            foods,
        )
        val repository = object : MealRepository by performing {
            override suspend fun deleteItem(itemId: Long): DeletedEntry? =
                throw CancellationException("the screen went away mid-delete")

            override suspend fun restore(entry: DeletedEntry) {
                restored += entry
            }
        }
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val item = ready(model).meals.single().items.single()
        advanceUntilIdle()
        val running = model.viewModelScope.coroutineContext.job.children.count()

        model.deleteItem(item)
        model.undoDelete()
        advanceUntilIdle()

        assertThat(model.viewModelScope.coroutineContext.job.children.count()).isEqualTo(running)
        assertThat(restored).isEmpty()
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).containsExactly(10L)
    }

    @Test
    fun `a row whose food was deleted meanwhile comes back detached`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Hummus")))
        val foodId = foods.current.single().id
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 12),
                    items = listOf(
                        anItem(id = 10, name = "Hummus", portionAmount = 100.0, portionUnit = "g", kcal = 180)
                            .copy(foodId = foodId),
                    ),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val before = ready(model).meals.single().items.single()

        model.deleteItem(before)
        advanceUntilIdle()
        foods.delete(foodId)
        model.undoDelete()
        advanceUntilIdle()

        val back = ready(model).meals.single().items.single()
        assertThat(back.foodId).isNull()
        assertThat(back).isEqualTo(before.copy(foodId = null, currentName = null))
    }

    @Test
    fun `a meal whose saved meal was deleted meanwhile comes back without its title`() = runTest {
        val foods = FakeFoodRepository()
        val titles = MutableStateFlow(mapOf(3L to "Greek salad"))
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 12),
                    note = "written down later",
                    savedMealId = 3,
                    savedMealAdjusted = true,
                    items = listOf(anItem(id = 10, name = "Greek salad")),
                ),
            ),
            foods,
            titles,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))

        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        // The stand-in's view of the saved_meals table: the meal is gone from it.
        titles.value = emptyMap()
        model.undoDelete()
        advanceUntilIdle()

        val back = ready(model).meals.single()
        assertThat(back.id).isEqualTo(1L)
        assertThat(back.savedMealId).isNull()
        assertThat(back.savedMealName).isNull()
        assertThat(back.savedMealAdjusted).isTrue()
        assertThat(back.loggedAtMillis).isEqualTo(atHour(TEST_EPOCH_DAY, 12))
        assertThat(back.note).isEqualTo("written down later")
    }

    /** Yesterday, so that 17:30 is a time already past and the re-time is not refused. */
    @Test
    fun `a row put back into a meal re-timed meanwhile does not move it back`() = runTest {
        val yesterday = TEST_EPOCH_DAY - 1
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = yesterday,
                    loggedAtMillis = atHour(yesterday, 12),
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        model.showDay(yesterday)
        advanceUntilIdle()

        model.deleteItem(ready(model).meals.single().items.single { it.id == 11L })
        advanceUntilIdle()
        model.setEatenAt(ready(model).meals.single(), hour = 17, minute = 30)
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        val meal = ready(model).meals.single()
        assertThat(meal.id).isEqualTo(1L)
        assertThat(meal.loggedAtMillis).isEqualTo(atTime(yesterday, 17, 30))
        assertThat(meal.items.map { it.id }).containsExactly(10L, 11L).inOrder()
    }

    /** D4, at the level the screen sees: a guess put back is still a guess, and the same row. */
    @Test
    fun `a restored row keeps where its numbers came from`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(
                aMeal(
                    id = 1,
                    loggedAtMillis = atHour(TEST_EPOCH_DAY, 12),
                    items = listOf(
                        anItem(id = 10, name = "Coffee with milk", kcal = 60, source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM),
                    ),
                ),
            ),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val before = ready(model).meals.single().items.single()

        model.deleteItem(before)
        advanceUntilIdle()
        model.undoDelete()
        advanceUntilIdle()

        val back = ready(model).meals.single().items.single()
        assertThat(back.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(back.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(back).isEqualTo(before)
    }

    @Test
    fun `there is nothing to undo until something is deleted`() = runTest {
        val foods = FakeFoodRepository()
        val repository = performing(
            listOf(aMeal(id = 1, loggedAtMillis = atHour(TEST_EPOCH_DAY, 12), items = listOf(anItem(id = 10)))),
            foods,
        )
        val model = watched(viewModel(mealRepository = repository, foods = foods))
        val before = ready(model).meals

        model.undoDelete()
        advanceUntilIdle()

        assertThat(ready(model).meals).isEqualTo(before)
    }

    // --- Making a meal out of a day (design §3.5) ---------------------------------------------------

    /**
     * Holding rather than tapping, for the same reason the foods list uses: a tap on a row already
     * means something — it opens what is under a meal — and choosing that began on a tap would turn
     * every look at the day into the start of a meal.
     */
    @Test
    fun `holding a row on the day starts choosing and chooses that row`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree())))

        model.beginChoosing(1)
        advanceUntilIdle()

        assertThat(ready(model).choosing).isTrue()
        assertThat(ready(model).chosen).containsExactly(1L)
    }

    @Test
    fun `tapping another row while choosing adds it, and tapping it again takes it out`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree())))

        model.beginChoosing(1)
        model.toggleChosen(2)
        advanceUntilIdle()
        assertThat(ready(model).chosen).containsExactly(1L, 2L)

        model.toggleChosen(2)
        advanceUntilIdle()
        assertThat(ready(model).chosen).containsExactly(1L)
    }

    /** One tap for the whole day, which is what "save this day as a meal" amounts to (design §4). */
    @Test
    fun `All chooses everything on the day, across the meals it was logged in`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree(), anotherMeal())))

        model.chooseAll()
        advanceUntilIdle()

        assertThat(ready(model).chosen).containsExactly(1L, 2L, 3L)
    }

    /**
     * Ticking a meal he built takes every row of it at once (D50, #50).
     *
     * Its parts are behind the row on the record screen, so its kicker is the only thing on screen
     * that stands for them — and ticking a label that stands for five rows has to mean the rows.
     */
    @Test
    fun `ticking a meal he built takes all of its rows`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree(), anotherMeal())))

        model.chooseMeal(aDayOfThree())
        advanceUntilIdle()

        assertThat(ready(model).chosen).containsExactly(1L, 2L)
    }

    /** Ticked whole, it unticks whole: the second tap on the kicker empties what the first filled. */
    @Test
    fun `ticking a chosen meal again takes all of its rows out`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree(), anotherMeal())))

        model.chooseMeal(aDayOfThree())
        model.chooseMeal(aDayOfThree())
        advanceUntilIdle()

        assertThat(ready(model).chosen).isEmpty()
    }

    /**
     * **Partly in counts as not in, and this is the case that makes it matter.** Toggling each of
     * its rows in turn would INVERT a half-chosen meal rather than choose it, so a kicker tapped on
     * a meal with one row already ticked would silently drop exactly the row he had picked.
     */
    @Test
    fun `ticking a half-chosen meal completes it rather than inverting it`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree(), anotherMeal())))

        model.beginChoosing(1)
        model.chooseMeal(aDayOfThree())
        advanceUntilIdle()

        assertThat(ready(model).chosen).containsExactly(1L, 2L)
    }

    /** Otherwise the day would sit in a mode with nothing in it, and every tap would still add. */
    @Test
    fun `taking the last row out ends choosing`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree())))

        model.beginChoosing(1)
        model.toggleChosen(1)
        advanceUntilIdle()

        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).choosing).isFalse()
    }

    /**
     * Naming the chosen rows is the whole act: a meal he built, its parts taken from what was logged,
     * and those rows gathered under it so the day shows them as one titled row.
     *
     * The two rows are chosen in the opposite order to the one they were logged in, on purpose: the
     * meal's parts are in the DAY's order, not in the order he happened to tap.
     */
    @Test
    fun `naming what is chosen builds the meal from those rows and gathers them under it`() = runTest {
        val foods = FakeFoodRepository(
            listOf(
                aFood(name = "Cucumber"),
                aFood(name = "Olive oil", facts = FoodFacts(perUnit = aPerUnit("spoon"))),
            ),
        )
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val mealRepository = FakeMealRepository(listOf(aSaladWorthOfRows()))
        val model = watched(
            viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(2)
        model.toggleChosen(1)
        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        val built = savedMeals.current.single()
        assertThat(built.name).isEqualTo("Vegetable salad")
        assertThat(built.components.map { it.food.name })
            .containsExactly("Cucumber", "Olive oil").inOrder()
        assertThat(built.components.map { it.countedAs })
            .containsExactly(CountedAs.GRAMS, CountedAs.UNITS).inOrder()
        assertThat(built.components.map { it.amount }).containsExactly(100.0, 1.0).inOrder()

        val (epochDay, itemIds, savedMealId) = mealRepository.gathered.single()
        assertThat(epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(itemIds).containsExactly(1L, 2L)
        assertThat(savedMealId).isEqualTo(built.id)

        // Done with, so the day goes back to being a day.
        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).refusal).isNull()
    }

    /**
     * Half a meal is worse than none: a row that cannot become a component stops the whole thing.
     *
     * The refusal names the row, so it is a next step — drop that one and try again — rather than a
     * failure. What he chose stays chosen for exactly that reason.
     */
    @Test
    fun `a row that cannot go into a meal stops the whole thing and is said out loud`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber")))
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val mealRepository = FakeMealRepository(
            listOf(
                aMeal(
                    id = 5,
                    items = listOf(
                        anItem(id = 1, name = "Cucumber", portionAmount = 100.0, portionUnit = "g")
                            .copy(foodId = 1),
                        // Logged before foods existed, so it points at nothing.
                        anItem(id = 2, name = "Coffee with milk"),
                    ),
                ),
            ),
        )
        val model = watched(
            viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(1)
        model.toggleChosen(2)
        model.makeMealFromChosen("Breakfast")
        advanceUntilIdle()

        assertThat(savedMeals.current).isEmpty()
        assertThat(mealRepository.gathered).isEmpty()
        assertThat(ready(model).refusal).isNotNull()
        assertThat(ready(model).refusal).contains("Coffee with milk")
        assertThat(ready(model).chosen).containsExactly(1L, 2L)
    }

    /**
     * A name he already used is refused too, and nothing is gathered.
     *
     * Not a detail: without this the meal would never be created and the rows would be pointed at a
     * meal that does not exist.
     *
     * Choosing survives the refusal, as it does for every other refusal on this path. The screen
     * leans on that: the sheet stays open exactly while there is still a choice to name.
     */
    @Test
    fun `a name another meal already has is refused, and no row is moved`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber")))
        val savedMeals = FakeSavedMealRepository(listOf(SavedMeal(name = "Vegetable salad")))
            .knowsAbout(*foods.current.toTypedArray())
        val mealRepository = FakeMealRepository(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(id = 1, name = "Cucumber", portionAmount = 100.0, portionUnit = "g")
                            .copy(foodId = 1),
                    ),
                ),
            ),
        )
        val model = watched(
            viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(1)
        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        assertThat(mealRepository.gathered).isEmpty()
        assertThat(savedMeals.current.single().components).isEmpty()
        assertThat(ready(model).refusal).contains("Vegetable salad")
        // What he chose stays chosen, which is what lets the naming sheet stay open with the name
        // still in it: correcting a taken name is one edit, not a fresh start.
        assertThat(ready(model).chosen).containsExactly(1L)
    }

    /**
     * A row that has gone from the day since he ticked it stops the whole thing too.
     *
     * The choice is a set of ids and the rows are re-read when the name is confirmed, so a row
     * deleted in between simply is not there any more. Left unnoticed that makes a meal quietly
     * smaller than the day it came from and quietly smaller than what he ticked — the same half a
     * meal every other refusal on this path exists to prevent, arrived at by a different route.
     */
    @Test
    fun `a row that vanished between ticking it and naming the meal stops the whole thing`() =
        runTest {
            val foods = FakeFoodRepository(
                listOf(
                    aFood(name = "Cucumber"),
                    aFood(name = "Olive oil", facts = FoodFacts(perUnit = aPerUnit("spoon"))),
                ),
            )
            val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
            val mealRepository = FakeMealRepository(listOf(aSaladWorthOfRows()))
            val model = watched(
                viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
            )

            model.beginChoosing(1)
            model.toggleChosen(2)
            advanceUntilIdle()

            // Deleted from somewhere else while the naming sheet was open.
            mealRepository.rowVanishes(2)
            model.makeMealFromChosen("Vegetable salad")
            advanceUntilIdle()

            assertThat(savedMeals.current).isEmpty()
            assertThat(mealRepository.gathered).isEmpty()
            assertThat(ready(model).refusal).isNotNull()
            // What he chose stays chosen, so choosing again is the next step and not a fresh start.
            assertThat(ready(model).chosen).containsExactly(1L, 2L)
        }

    /**
     * The same coffee at breakfast and again in the afternoon goes in ONCE, worth both cups.
     *
     * A meal stores its parts keyed on the food: putting one in twice changes the amount rather than
     * making a second row. So two components of one coffee left the meal holding one cup — half of
     * what the sheet had just totalled up — and nothing said so until he logged the meal and it came
     * out half size.
     */
    @Test
    fun `the same food logged twice in a day goes into the meal once, worth both rows`() = runTest {
        val foods = FakeFoodRepository(
            listOf(
                aFood(
                    name = "Coffee with milk",
                    facts = FoodFacts(perUnit = aPerUnit("cup", kcal = 60.0)),
                ),
            ),
        )
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val mealRepository = FakeMealRepository(listOf(twoCoffeesOnOneDay()))
        val model = watched(
            viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(1)
        model.toggleChosen(2)
        model.makeMealFromChosen("Two coffees")
        advanceUntilIdle()

        val component = savedMeals.current.single().components.single()
        assertThat(component.food.name).isEqualTo("Coffee with milk")
        assertThat(component.countedAs).isEqualTo(CountedAs.UNITS)
        assertThat(component.amount).isEqualTo(2.0)

        // Both rows still go under the meal: merging is about what the meal HOLDS, not about which
        // rows the day gathers.
        assertThat(mealRepository.gathered.single().second).containsExactly(1L, 2L)
    }

    /**
     * When EVERY row he ticked has gone, he is told, in the same words as when one has.
     *
     * The all-gone case used to take a silent path of its own — the empty check sat in front of the
     * count comparison — so the naming sheet, which now closes only when the choosing ends, sat open
     * over a button that did nothing at all.
     */
    @Test
    fun `a choice whose rows have all gone is refused in the same words as one gone`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber")))
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val mealRepository = FakeMealRepository(
            listOf(
                aMeal(
                    id = 5,
                    items = listOf(
                        anItem(id = 1, name = "Cucumber", portionAmount = 100.0, portionUnit = "g")
                            .copy(foodId = 1),
                        anItem(id = 2, name = "Cucumber", portionAmount = 50.0, portionUnit = "g")
                            .copy(foodId = 1),
                        anItem(id = 3, name = "Coffee with milk"),
                    ),
                ),
            ),
        )
        val model = watched(
            viewModel(mealRepository = mealRepository, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(1)
        model.toggleChosen(2)
        advanceUntilIdle()

        // Both of them deleted from somewhere else while the naming sheet was open.
        mealRepository.rowVanishes(1)
        mealRepository.rowVanishes(2)
        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        assertThat(savedMeals.current).isEmpty()
        assertThat(mealRepository.gathered).isEmpty()
        assertThat(ready(model).refusal).isEqualTo(MealWording.chosenRowGone)
        assertThat(ready(model).chosen).containsExactly(1L, 2L)
    }

    /**
     * Choosing belongs to the day it started on.
     *
     * The ids are rows on one date; carrying them across a swipe would offer to make a meal out of
     * rows that are no longer on screen.
     */
    @Test
    fun `changing day clears what was chosen`() = runTest {
        val model = watched(viewModel(meals = listOf(aDayOfThree())))

        model.beginChoosing(1)
        advanceUntilIdle()
        model.showDay(TEST_EPOCH_DAY - 1)
        advanceUntilIdle()

        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).choosing).isFalse()
    }

    // --- Keeping what was just described as a meal (D46, issue #24) -----------------------------

    /**
     * The offer on the accept screen logs the rows and leaves exactly those rows chosen.
     *
     * **And nothing before they exist.** The write happens after the tap, so between the two there
     * is a frame in which the offer has been taken and nothing has been logged; a sheet opened in it
     * lists nothing and totals 0 kcal in front of him. So the choice appears when the rows do, and
     * that is asserted before the coroutine is let run as well as after.
     */
    @Test
    fun `logging what was described leaves exactly those rows chosen`() = runTest {
        val foods = twoFoods()
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(
                mealRepository = meals,
                savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray()),
                foods = foods,
            ),
        )

        model.logMealAndChoose(aDescribedSalad())

        // The tap's own frame: nothing written, so nothing to name.
        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).choosing).isFalse()

        advanceUntilIdle()

        val shown = ready(model)
        val onTheDay = shown.meals.flatMap { it.items }
        assertThat(onTheDay.map { it.name }).containsExactly("Cucumber", "Olive oil")
        assertThat(shown.chosen).containsExactlyElementsIn(onTheDay.map { it.id })
        // Saving is never silent, exactly as it is not when the offer is declined.
        assertThat(shown.justLogged).isNotNull()
    }

    /**
     * Naming what was just described is the day's own act, reached from the accept screen.
     *
     * One saved meal, one component per food, and those rows and no others gathered under it.
     */
    @Test
    fun `naming what was just described makes one meal of exactly those rows`() = runTest {
        val foods = twoFoods()
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()
        val chosen = ready(model).chosen.sorted()

        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        val built = savedMeals.current.single()
        assertThat(built.name).isEqualTo("Vegetable salad")
        assertThat(built.components.map { it.food.name })
            .containsExactly("Cucumber", "Olive oil").inOrder()
        assertThat(built.components.map { it.countedAs })
            .containsExactly(CountedAs.GRAMS, CountedAs.UNITS).inOrder()
        assertThat(built.components.map { it.amount }).containsExactly(100.0, 1.0).inOrder()

        val (epochDay, itemIds, savedMealId) = meals.gathered.single()
        assertThat(epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(itemIds.sorted()).isEqualTo(chosen)
        assertThat(savedMealId).isEqualTo(built.id)

        // Done with, so the day goes back to being a day and the sheet has nothing to name.
        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).refusal).isNull()
    }

    /**
     * The whole point of issue #24: one way of making a meal, reached from two places.
     *
     * The same two rows, once ticked on the day by hand and once kept the moment they were
     * described, are asserted AGAINST EACH OTHER — the name made, the parts it holds, and the
     * gathering that moved the rows — rather than each against what this test thinks is right.
     */
    @Test
    fun `the meal made this way is made by the same path as one made by hand`() = runTest {
        val byHandFoods = twoFoods()
        val byHandMeals = FakeMealRepository(listOf(aSaladWorthOfRows()))
        val byHandMade = FakeSavedMealRepository().knowsAbout(*byHandFoods.current.toTypedArray())
        val byHand = watched(
            viewModel(mealRepository = byHandMeals, savedMeals = byHandMade, foods = byHandFoods),
        )
        byHand.beginChoosing(1)
        byHand.toggleChosen(2)
        byHand.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        val describedFoods = twoFoods()
        val describedMeals = FakeMealRepository()
        val describedMade =
            FakeSavedMealRepository().knowsAbout(*describedFoods.current.toTypedArray())
        val described = watched(
            viewModel(
                mealRepository = describedMeals,
                savedMeals = describedMade,
                foods = describedFoods,
            ),
        )
        described.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()
        described.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        assertThat(describedMade.current.map { it.name }).isEqualTo(byHandMade.current.map { it.name })
        assertThat(partsOf(describedMade)).isEqualTo(partsOf(byHandMade))
        assertThat(describedMeals.gathered).isEqualTo(byHandMeals.gathered)
    }

    /**
     * *Describe a meal* is on the day and is reachable with rows already ticked.
     *
     * The choice is ASSIGNED from the ids the write handed back, never added to — otherwise a meal
     * made from a description would quietly swallow a row he ticked an hour ago.
     */
    @Test
    fun `rows chosen on the day before describing are not swallowed`() = runTest {
        val foods = twoFoods()
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val meals = FakeMealRepository(
            listOf(aMeal(id = 9, items = listOf(anItem(id = 7, name = "Coffee with milk", kcal = 60)))),
        )
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(7)
        advanceUntilIdle()

        model.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()

        val chosen = ready(model).chosen
        assertThat(chosen).hasSize(2)
        assertThat(chosen).doesNotContain(7L)

        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        assertThat(meals.gathered.single().second.sorted()).isEqualTo(chosen.sorted())
        // The coffee is still on the day, in the meal it was logged in.
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).contains(7L)
    }

    /**
     * Naming, in the window between taking the offer and the rows landing, can only name nothing.
     *
     * This is the trap the test above walks past by advancing the clock over it. The accept screen
     * opens the naming sheet the moment the day has rows chosen, and the write is two database
     * round trips away; a choice left standing through that window is a sheet over the rows he
     * ticked an hour ago, and Confirm pressed inside it gathers THOSE into the named meal.
     *
     * So the window is held open here — the store's write waits, as a real one does — and the whole
     * of it is looked at: everything else settles, the screen catches up, and a name is confirmed
     * while the rows are still on their way.
     */
    @Test
    fun `a name confirmed before the rows land cannot gather rows chosen earlier`() = runTest {
        val foods = twoFoods()
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val meals = WriteHeldOpen(
            FakeMealRepository(
                listOf(aMeal(id = 9, items = listOf(anItem(id = 7, name = "Coffee with milk", kcal = 60)))),
            ),
        )
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.beginChoosing(7)
        advanceUntilIdle()
        assertThat(ready(model).chosen).containsExactly(7L)

        model.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()

        // Inside the window, with the screen fully caught up: nothing is chosen, so the sheet has
        // nothing to open over and the Chosen bar has gone from the day.
        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).choosing).isFalse()

        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        // Naming an empty choice makes nothing, and above all it does not gather the coffee.
        assertThat(meals.store.gathered).isEmpty()
        assertThat(savedMeals.current).isEmpty()

        meals.letItLand()
        advanceUntilIdle()

        // And when the rows do land they are the described ones, and only them.
        val chosen = ready(model).chosen
        assertThat(chosen).hasSize(2)
        assertThat(chosen).doesNotContain(7L)
        assertThat(ready(model).meals.flatMap { it.items }.map { it.id }).contains(7L)
    }

    /**
     * The sentence in the sheet belongs to what he is doing now, not to what he did before.
     *
     * The day's refusal is one field written by more than meal naming — putting a meal at a time
     * that has not happened yet writes into it too — and it stands until something clears it. The
     * accept screen shows it inside the naming sheet, so a refusal left over from the day would
     * arrive under a brand-new name, having been on screen for minutes. The write is held open
     * again, because "minutes" is exactly the window a refusal cleared only afterwards survives.
     */
    @Test
    fun `a refusal left standing on the day is gone before the naming sheet can show it`() = runTest {
        val foods = twoFoods()
        val meals = WriteHeldOpen(
            FakeMealRepository(
                listOf(
                    aMeal(id = 9, epochDay = TEST_EPOCH_DAY, loggedAtMillis = atHour(TEST_EPOCH_DAY, 11)),
                ),
            ),
        )
        val model = watched(
            viewModel(
                mealRepository = meals,
                savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray()),
                foods = foods,
            ),
        )

        // The clock reads 15:00, so 16:00 is refused and the sentence stays up.
        model.setEatenAt(ready(model).meals.single { it.id == 9L }, hour = 16, minute = 0)
        advanceUntilIdle()
        assertThat(ready(model).refusal).isNotNull()

        model.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()

        // Inside the window: the old sentence has gone with the old choice, not two database round
        // trips later, so the sheet cannot open over it.
        assertThat(ready(model).refusal).isNull()

        meals.letItLand()
        advanceUntilIdle()
        assertThat(ready(model).refusal).isNull()
    }

    /**
     * A milky coffee as the model returns it: the milk comes back in millilitres, and the day cannot read it.
     *
     * The items are written first and on their own, so the refusal costs him nothing — both rows are
     * on the day, and the sentence names the row and says what stood in the way. The choice stands,
     * because dropping a row and trying again is the next step.
     *
     * The rows arrive already attached to their foods, which is what holds the case still: a milk
     * the record has only ever weighed counts in grams, and 120 ml of it is a volume this app has no
     * factor for (#19). Attaching them here would teach a brand-new Milk to count in millilitres,
     * and the row would join.
     */
    @Test
    fun `a row that cannot join leaves the items logged and makes no meal`() = runTest {
        val foods = FakeFoodRepository(
            listOf(
                aFood(name = "Milk"),
                aFood(name = "Espresso", facts = FoodFacts(perUnit = aPerUnit("cup", kcal = 2.0))),
            ),
        )
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.logMealAndChoose(
            listOf(
                describedRow("Milk", kcal = 60, amount = 120.0, unit = "ml").copy(foodId = 1),
                describedRow("Espresso", kcal = 2, amount = 1.0, unit = "cup").copy(foodId = 2),
            ).map(::ToLog),
        )
        advanceUntilIdle()

        model.makeMealFromChosen("Cappuccino")
        advanceUntilIdle()

        // Everything he described is on the day, which is what makes the refusal harmless.
        assertThat(ready(model).meals.flatMap { it.items }.map { it.name })
            .containsExactly("Milk", "Espresso")
        assertThat(savedMeals.current).isEmpty()
        assertThat(meals.gathered).isEmpty()

        val refusal = ready(model).refusal
        assertThat(refusal).isNotNull()
        assertThat(refusal).contains("Milk")
        assertThat(refusal).contains("nothing here turns ml into grams")
        // Still chosen, so the sheet stays open and correcting the day is one step.
        assertThat(ready(model).chosen).hasSize(2)
    }

    /**
     * A row naming a unit and never saying how much is refused in the day's own words.
     *
     * Kept off the describe route today by D34 — a reply with an amount missing is refused at the
     * door (`EstimateResponse`, `EstimateResult.AmountMissing`) — so this pins the view model rather
     * than that route. It is the case issue #23 is about, and the one the day would meet first if
     * that door ever opened.
     */
    @Test
    fun `a row that never said how much is refused, and the items stay logged`() = runTest {
        val foods = FakeFoodRepository(
            listOf(aFood(name = "Cappuccino", facts = FoodFacts(perUnit = aPerUnit("cup", kcal = 60.0)))),
        )
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.logMealAndChoose(
            listOf(
                describedRow("Cappuccino", kcal = 60, amount = 0.0, unit = "cup", portion = "amount not stated")
                    .copy(foodId = 1),
            ).map(::ToLog),
        )
        advanceUntilIdle()

        model.makeMealFromChosen("Morning coffee")
        advanceUntilIdle()

        assertThat(ready(model).meals.flatMap { it.items }).hasSize(1)
        assertThat(savedMeals.current).isEmpty()
        assertThat(meals.gathered).isEmpty()
        val refusal = ready(model).refusal
        assertThat(refusal).contains("Cappuccino")
        assertThat(refusal).contains("nothing said how much")
    }

    /**
     * A name another meal holds is refused by the sheet the day already uses, and nothing is made.
     *
     * The rows were logged when the offer was taken, so typing a free name afterwards makes the meal
     * out of the SAME rows: the day holds two, not four. That is the whole reason the write and the
     * meal are two separate acts.
     */
    @Test
    fun `a name another meal holds is refused, and retyping does not log the items again`() = runTest {
        val foods = twoFoods()
        val savedMeals = FakeSavedMealRepository(listOf(SavedMeal(name = "Breakfast")))
            .knowsAbout(*foods.current.toTypedArray())
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()
        val chosen = ready(model).chosen

        model.makeMealFromChosen("Breakfast")
        advanceUntilIdle()

        assertThat(ready(model).refusal).isEqualTo(MealWording.nameTaken("Breakfast"))
        assertThat(savedMeals.current.map { it.name }).containsExactly("Breakfast")
        assertThat(savedMeals.current.single().components).isEmpty()
        assertThat(meals.gathered).isEmpty()
        // What he typed is still being named: the choice is what keeps the sheet open.
        assertThat(ready(model).chosen).isEqualTo(chosen)

        model.makeMealFromChosen("Second breakfast")
        advanceUntilIdle()

        assertThat(savedMeals.current.map { it.name })
            .containsExactly("Breakfast", "Second breakfast")
        assertThat(meals.gathered.single().second.sorted()).isEqualTo(chosen.sorted())
        // Logged once, when the offer was taken — and not again by either attempt at a name.
        assertThat(meals.logged).hasSize(1)
        assertThat(ready(model).meals.flatMap { it.items }).hasSize(2)
    }

    /**
     * A described item's food learns the worth it was described with, not a figure worked back
     * from the rounded row (D53 §3). Invented: 717.4 kcal per 100 g, 7 g logged as 50 kcal, which
     * works back to 714.29.
     */
    @Test
    fun `a described meal teaches each food what it was handed`() = runTest {
        val foods = FakeFoodRepository()
        val model = watched(viewModel(mealRepository = FakeMealRepository(), foods = foods))
        val worth = Nutrients(717.4, 0.9, 0.1, 81.1)
        val row = describedRow("Butter", kcal = 50, amount = 7.0, unit = "g", proteinG = 0, carbsG = 0, fatG = 6)
        val taught = FoodFacts(
            per100g = PerHundredGrams(worth, Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 0)),
        )

        model.logMeal(listOf(ToLog(row, taught = taught)))
        advanceUntilIdle()

        assertThat(foods.current.single().facts.per100g!!.nutrients).isEqualTo(worth)
        assertThat(ready(model).meals.single().items.single().kcal).isEqualTo(50)
    }

    /** Today's behaviour, pinned: declining the offer logs the items and starts nothing. */
    @Test
    fun `accepting without the offer logs the items and chooses nothing`() = runTest {
        val foods = twoFoods()
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(
                mealRepository = meals,
                savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray()),
                foods = foods,
            ),
        )

        model.logMeal(aDescribedSalad())
        advanceUntilIdle()

        assertThat(ready(model).meals.flatMap { it.items }).hasSize(2)
        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).choosing).isFalse()
        assertThat(meals.gathered).isEmpty()
    }

    /**
     * An empty answer is not a meal, and must not leave a sheet standing over nothing.
     *
     * `logMeal` refuses one outright, and so does this: a sheet listing no rows would have a Confirm
     * that silently does nothing, because naming an empty choice makes nothing.
     */
    @Test
    fun `an empty answer logs nothing and leaves nothing to name`() = runTest {
        val meals = FakeMealRepository()
        val model = watched(viewModel(mealRepository = meals))

        model.logMealAndChoose(emptyList())
        advanceUntilIdle()

        assertThat(meals.logged).isEmpty()
        assertThat(ready(model).chosen).isEmpty()
        assertThat(ready(model).choosing).isFalse()
        assertThat(ready(model).justLogged).isNull()
    }

    /**
     * Gathering is a labelling act: not one stored figure moves (D4).
     *
     * The rows on the day after the meal is made carry the calories, the macros, the portion, the
     * source and the confidence they were logged with — an estimate is still an estimate afterwards.
     * What a REAL gather does to stored rows is `RoomMealRepositoryTest`'s, against a database.
     */
    @Test
    fun `not one stored figure moves when the rows are gathered`() = runTest {
        val foods = twoFoods()
        val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
        val meals = FakeMealRepository()
        val model = watched(
            viewModel(mealRepository = meals, savedMeals = savedMeals, foods = foods),
        )

        model.logMealAndChoose(aDescribedSalad())
        advanceUntilIdle()
        model.makeMealFromChosen("Vegetable salad")
        advanceUntilIdle()

        val cucumber = ready(model).meals.flatMap { it.items }.single { it.name == "Cucumber" }
        assertThat(cucumber.kcal).isEqualTo(16)
        assertThat(cucumber.proteinG).isEqualTo(1)
        assertThat(cucumber.carbsG).isEqualTo(3)
        assertThat(cucumber.fatG).isEqualTo(0)
        assertThat(cucumber.portionAmount).isEqualTo(100.0)
        assertThat(cucumber.portionUnit).isEqualTo("g")
        assertThat(cucumber.portion).isEqualTo("100 g")
        assertThat(cucumber.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(cucumber.confidence).isEqualTo(Confidence.MEDIUM)
    }

    /** The two foods the described salad lands on, seeded so the meal's parts can be asserted. */
    private fun twoFoods() = FakeFoodRepository(
        listOf(
            aFood(name = "Cucumber"),
            aFood(name = "Olive oil", facts = FoodFacts(perUnit = aPerUnit("spoon"))),
        ),
    )

    /** A meal's parts as three plain facts, for comparing one route's answer with another's. */
    private fun partsOf(meals: FakeSavedMealRepository) = meals.current.single().components
        .map { Triple(it.food.name, it.countedAs, it.amount) }

    /**
     * A row as the accept screen hands it over: the model's own figures, attached to nothing yet.
     *
     * An estimate with a confidence, because that is what the describe path produces and what must
     * still be on the row after it has been gathered under a name (D4).
     */
    private fun describedRow(
        name: String,
        kcal: Int,
        amount: Double,
        unit: String,
        portion: String = "${if (amount % 1.0 == 0.0) amount.toInt() else amount} $unit",
        proteinG: Int = 1,
        carbsG: Int = 3,
        fatG: Int = 0,
    ): FoodItem = anItem(
        name = name,
        portion = portion,
        portionAmount = amount,
        portionUnit = unit,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        source = Source.AI_ESTIMATE,
        confidence = Confidence.MEDIUM,
    )

    /** The same two rows [aSaladWorthOfRows] holds, as the model would have just answered them. */
    private fun aDescribedSalad(): List<ToLog> = listOf(
        describedRow("Cucumber", kcal = 16, amount = 100.0, unit = "g"),
        describedRow("Olive oil", kcal = 119, amount = 1.0, unit = "spoon", proteinG = 0, carbsG = 0, fatG = 13),
    ).map(::ToLog)

    /** Two things logged separately on the same day, so "All" has to reach across both. */
    private fun aDayOfThree(): Meal = aMeal(
        id = 5,
        items = listOf(
            anItem(id = 1, name = "Eggs", kcal = 155),
            anItem(id = 2, name = "Tomato", kcal = 27),
        ),
    )

    private fun anotherMeal(): Meal = aMeal(
        id = 6,
        items = listOf(anItem(id = 3, name = "Coffee with milk", kcal = 60)),
    )

    /** One cup of coffee, in the unit the food is counted in. */
    private fun coffee(id: Long): FoodItem = anItem(
        id = id,
        name = "Coffee with milk",
        portionAmount = 1.0,
        portionUnit = "cup",
        kcal = 60,
    ).copy(foodId = 1)

    /** One food, logged twice on one day, in the unit it is counted in. */
    private fun twoCoffeesOnOneDay(): Meal = aMeal(
        id = 5,
        items = listOf(
            coffee(id = 1),
            coffee(id = 2),
        ),
    )

    /** One row weighed in grams, one counted in spoons, both attached to a food. */
    private fun aSaladWorthOfRows(): Meal = aMeal(
        id = 5,
        items = listOf(
            anItem(id = 1, name = "Cucumber", portionAmount = 100.0, portionUnit = "g", kcal = 16)
                .copy(foodId = 1),
            anItem(id = 2, name = "Olive oil", portionAmount = 1.0, portionUnit = "spoon", kcal = 119)
                .copy(foodId = 2),
        ),
    )

    // --- issue #13 (D45): the day says so when logging changed a food's own figures -------------

    /**
     * The silent cases are not re-asserted here. `ReplacedFactsTest` proves them over the function
     * that decides, and `LoggedFoodsTest` proves that attaching reports only what it really saw.
     * These cases are about the DAY's own behaviour: that the sentence arrives, stays, goes when
     * dismissed, survives a swipe, and does not crowd out the notice that was already there.
     */

    /** A food knowing what 100 g of it are worth, as he typed them. */
    private fun typedPer100g(kcal: Double) = PerHundredGrams(
        Nutrients(kcal, 1.0, 3.0, 0.1),
        Provenance(Source.TYPED, null, setAtMillis = 0),
    )

    private fun cucumberKnowing(kcal: Double) =
        aFood("Cucumber", FoodFacts(per100g = typedPer100g(kcal)))

    /** One row weighed out at 100 g, so what it implies per 100 g is what it says. */
    private fun typedRow(name: String, kcal: Int) = anItem(
        name = name, portionAmount = 100.0, portionUnit = "g",
        kcal = kcal, proteinG = 1, carbsG = 3, fatG = 0,
    )

    @Test
    fun `logging over a food's own figure says which food, what it held and what it holds`() = runTest {
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val model = watched(viewModel(foods = foods))

        model.log(typedRow("Cucumber", 18))
        advanceUntilIdle()

        // The log is never blocked and which figure wins never changes.
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(18.0)
        val notice = ready(model).foodRetaughtNotice
        assertThat(notice).contains("Cucumber")
        assertThat(notice).contains("18")
        assertThat(notice).contains("15")
    }

    /** Once read it is gone, and it does not reappear when the state next emits. */
    @Test
    fun `dismissing the food notice clears it and it does not come back`() = runTest {
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val model = watched(viewModel(foods = foods))
        model.log(typedRow("Cucumber", 18))
        advanceUntilIdle()
        assertThat(ready(model).foodRetaughtNotice).isNotNull()

        model.dismissFoodRetaught()
        advanceUntilIdle()
        assertThat(ready(model).foodRetaughtNotice).isNull()

        // Something else changes, so the state emits again: the sentence must not return with it.
        model.dismissJustLogged()
        advanceUntilIdle()
        assertThat(ready(model).foodRetaughtNotice).isNull()
    }

    /** One notice per action: the next thing he logs replaces it, including replaces it with nothing. */
    @Test
    fun `a later logging action with nothing to say clears the earlier notice`() = runTest {
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val model = watched(viewModel(foods = foods))
        model.log(typedRow("Cucumber", 18))
        advanceUntilIdle()
        assertThat(ready(model).foodRetaughtNotice).isNotNull()

        model.log(typedRow("Kohlrabi", 27))
        advanceUntilIdle()

        assertThat(ready(model).foodRetaughtNotice).isNull()
    }

    /**
     * A food is not a property of a day, so swiping to yesterday must not take the sentence away
     * before it has been read — unlike everything `showDay` does clear, which belongs to the day.
     */
    @Test
    fun `moving to another day keeps the food notice`() = runTest {
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val model = watched(viewModel(foods = foods))
        model.log(typedRow("Cucumber", 18))
        advanceUntilIdle()
        val said = ready(model).foodRetaughtNotice
        assertThat(said).isNotNull()

        model.showDay(TEST_EPOCH_DAY - 1)
        advanceUntilIdle()

        assertThat(ready(model).foodRetaughtNotice).isEqualTo(said)
    }

    /**
     * Teaching a food from outside logging says nothing — the shape a backup restore has, which
     * replays history rather than recording something he has just done.
     *
     * A stand-in for the restore rather than a test of it, and it can only be that: `BackupRepository`
     * calls `findOrCreate` and drops what it returns, and there is no channel at all from a
     * repository to this notice — only a view-model that has just acted can set one. Restore's
     * silence is therefore structural, and what this pins is the same structure from the one place
     * a test can reach it.
     */
    @Test
    fun `a food taught outside logging produces no notice`() = runTest {
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val model = watched(viewModel(foods = foods))

        foods.findOrCreate("Cucumber", facts = FoodFacts(per100g = typedPer100g(18.0)))
        advanceUntilIdle()

        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(18.0)
        assertThat(ready(model).foodRetaughtNotice).isNull()
    }

    /**
     * *Correct this item* renaming a row onto another food teaches that food exactly as logging
     * does — it is D44's own route — and returns him to the screen that can tell him. Slightly
     * wider than the issue's literal words, and deliberate.
     */
    @Test
    fun `a correction that renames a row onto another food says so too`() = runTest {
        val foods = FakeFoodRepository(
            listOf(cucumberKnowing(15.0), aFood("Feta", FoodFacts(per100g = typedPer100g(264.0)))),
        )
        val model = watched(viewModel(mealRepository = FakeMealRepository(), foods = foods))
        val before = typedRow("Cucumber", 15).copy(id = 5, foodId = 1)

        model.correctItem(before, before.copy(name = "Feta", kcal = 280))
        advanceUntilIdle()

        val notice = ready(model).foodRetaughtNotice
        assertThat(notice).contains("Feta")
        assertThat(notice).contains("280")
        assertThat(notice).contains("264")
    }

    /**
     * A correction that keeps the row's name teaches no food, so it is not one of the actions that
     * speaks and has nothing to replace: the last sentence stays until he dismisses it.
     *
     * The path this pins is the ordinary one — he logs a described meal, reads that a food's figure
     * moved, then taps Edit on another row to fix the grams the model guessed wrong. Clearing the
     * notice there would wipe it unread, which is the silence issue #13 exists to close coming back
     * through the ordinary door.
     */
    @Test
    fun `correcting a row without renaming it leaves the food notice standing`() = runTest {
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val meals = FakeMealRepository()
        val model = watched(viewModel(mealRepository = meals, foods = foods))
        model.log(typedRow("Cucumber", 18))
        advanceUntilIdle()
        val said = ready(model).foodRetaughtNotice
        assertThat(said).isNotNull()

        val row = typedRow("Tahini", 90).copy(id = 5, foodId = 7)
        model.correctItem(row, row.withAmount(60.0))
        advanceUntilIdle()

        // The correction itself still lands, and still on the food it was.
        assertThat(meals.updated.last().foodId).isEqualTo(7)
        assertThat(ready(model).foodRetaughtNotice).isEqualTo(said)
    }

    /**
     * The two notices are about different things — the weekly revision moved his target; what he
     * just logged moved a food — so neither may suppress the other.
     */
    @Test
    fun `the target's notice and the food's notice can be on screen together`() = runTest {
        val profiles = FakeProfileRepository(
            aProfile(),
            initialRevision = TargetRevision(TEST_EPOCH_DAY, 79.2, 2050, 2090),
            initialSeen = false,
        )
        val foods = FakeFoodRepository(listOf(cucumberKnowing(15.0)))
        val model = watched(viewModel(profiles = profiles, foods = foods))

        model.log(typedRow("Cucumber", 18))
        advanceUntilIdle()

        val shown = ready(model)
        assertThat(shown.targetChangeNotice).isNotNull()
        assertThat(shown.foodRetaughtNotice).isNotNull()
    }

    // --- When an action throws -----------------------------------------------------------------------
    //
    // An exception that got past the guard would fail each of these on its own: `runTest` reports a
    // coroutine's uncaught exception when it ends.

    @Test
    fun `a log that throws says it may have partly happened, in place of the Logged line`() =
        runTest {
            val inner = FakeMealRepository()
            val problems = RecordingProblemLog()
            val model = watched(
                viewModel(mealRepository = Failing(inner).apply { writes = true }, problems = problems),
            )

            model.log(anItem(name = "Hummus", kcal = 180))
            advanceUntilIdle()

            val shown = ready(model)
            assertThat(shown.failed).isEqualTo(ActionRefused.MAYBE_PARTIAL)
            assertThat(shown.justLogged).isNull()
            assertThat(inner.logged).isEmpty()
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
            assertThat(problems.recorded.single().detail).contains("disk full")
        }

    @Test
    fun `every way of logging says so when it throws`() = runTest {
        val failing = Failing(FakeMealRepository()).apply { writes = true }
        val problems = RecordingProblemLog()
        val model = watched(viewModel(mealRepository = failing, problems = problems))

        model.logMeal(listOf(anItem(name = "Eggs"), anItem(name = "Toast")).map(::ToLog))
        model.logMealAndChoose(listOf(anItem(name = "Eggs"), anItem(name = "Toast")).map(::ToLog))
        model.logSavedMeal(LoggedMeal(items = listOf(anItem(name = "Eggs")), savedMealId = 1, adjusted = false))
        model.logScanned(anItem(name = "Crackers"), aPacket())
        advanceUntilIdle()

        assertThat(ready(model).failed).isEqualTo(ActionRefused.MAYBE_PARTIAL)
        assertThat(ready(model).chosen).isEmpty()
        assertThat(problems.recorded.map { it.kind }).containsExactly("refused", "refused", "refused", "refused")
    }

    /**
     * The accept screen starts over only once the rows are on the day. Started over on the tap, a
     * failed write left him on an empty describe screen, his answer gone and nothing written.
     */
    @Test
    fun `keeping what was described as a meal reports it was logged only once it was`() = runTest {
        val failing = Failing(FakeMealRepository()).apply { writes = true }
        val model = watched(viewModel(mealRepository = failing, problems = RecordingProblemLog()))
        var loggedTimes = 0

        model.logMealAndChoose(aDescribedSalad()) { loggedTimes++ }
        advanceUntilIdle()

        assertThat(loggedTimes).isEqualTo(0)
        assertThat(ready(model).failed).isEqualTo(ActionRefused.MAYBE_PARTIAL)

        failing.writes = false
        model.logMealAndChoose(aDescribedSalad()) { loggedTimes++ }
        // Not on the tap: only once the write has answered.
        assertThat(loggedTimes).isEqualTo(0)
        advanceUntilIdle()

        assertThat(loggedTimes).isEqualTo(1)
        assertThat(ready(model).chosen).isNotEmpty()
    }

    /**
     * Nothing was changed, because the meal, its parts and the gathering are one transaction in the
     * real store (`RoomSavedMealRepositoryTest` shows it rolls back). What he chose stays chosen, and
     * the sheet with it, so he can try again.
     */
    @Test
    fun `making a meal that throws part-way says nothing was changed and keeps the choice`() =
        runTest {
            val foods = FakeFoodRepository(
                listOf(
                    aFood(name = "Cucumber"),
                    aFood(name = "Olive oil", facts = FoodFacts(perUnit = aPerUnit("spoon"))),
                ),
            )
            val savedMeals = FakeSavedMealRepository().knowsAbout(*foods.current.toTypedArray())
            val failing = Failing(FakeMealRepository(listOf(aSaladWorthOfRows())))
            val problems = RecordingProblemLog()
            val model = watched(
                viewModel(
                    mealRepository = failing,
                    savedMeals = savedMeals,
                    foods = foods,
                    problems = problems,
                ),
            )

            model.beginChoosing(1)
            model.toggleChosen(2)
            failing.writes = true
            model.makeMealFromChosen("Vegetable salad")
            advanceUntilIdle()

            val shown = ready(model)
            assertThat(shown.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
            assertThat(shown.refusal).isNull()
            assertThat(shown.chosen).containsExactly(1L, 2L)
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
        }

    /** No Undo for a row that is still there. */
    @Test
    fun `a delete that throws offers nothing to undo`() = runTest {
        val failing = Failing(FakeMealRepository(listOf(aDayOfThree())))
        val problems = RecordingProblemLog()
        val model = watched(viewModel(mealRepository = failing, problems = problems))

        failing.writes = true
        model.deleteItem(ready(model).meals.single().items.first())
        advanceUntilIdle()

        assertThat(ready(model).failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(model.canUndo.value).isFalse()
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
    }

    /** Several rows are several deletes, so one failing among them may leave the others gone. */
    @Test
    fun `deleting what is chosen, with one row refusing, says it may have partly happened`() =
        runTest {
            val inner = FakeMealRepository(listOf(aDayOfThree()))
            val failing = Failing(inner).apply { deleteRefusedFor = setOf(2L) }
            val problems = RecordingProblemLog()
            val model = watched(viewModel(mealRepository = failing, problems = problems))

            model.beginChoosing(1)
            model.toggleChosen(2)
            model.deleteChosen()
            advanceUntilIdle()

            assertThat(ready(model).failed).isEqualTo(ActionRefused.MAYBE_PARTIAL)
            assertThat(inner.deleted).containsExactly(1L)
            // The row that went can still come back; the one that stayed offers nothing.
            assertThat(model.canUndo.value).isTrue()
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
        }

    @Test
    fun `an undo that throws keeps the receipt, so Undo is still there`() = runTest {
        val foods = FakeFoodRepository()
        val failing = Failing(
            performing(listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Eggs")))), foods),
        )
        val model = watched(
            viewModel(mealRepository = failing, foods = foods, problems = RecordingProblemLog()),
        )

        model.deleteItem(ready(model).meals.single().items.single())
        advanceUntilIdle()
        failing.writes = true
        model.undoDelete()
        advanceUntilIdle()

        assertThat(ready(model).failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(model.canUndo.value).isTrue()

        // And it works once the store does.
        failing.writes = false
        model.undoDelete()
        advanceUntilIdle()
        assertThat(ready(model).meals.single().items.single().id).isEqualTo(10L)
        assertThat(ready(model).failed).isNull()
    }

    @Test
    fun `a time that throws says nothing was changed`() = runTest {
        val failing = Failing(FakeMealRepository(listOf(aDayOfThree())))
        val problems = RecordingProblemLog()
        val model = watched(viewModel(mealRepository = failing, problems = problems))

        failing.writes = true
        model.setEatenAt(ready(model).meals.single(), hour = 9, minute = 0)
        advanceUntilIdle()

        assertThat(ready(model).failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
    }

    /**
     * Keeping the name is one write to the row. A new name first finds or makes its food, in a
     * transaction of its own, so a failure after that may have left the food behind.
     */
    @Test
    fun `a correction that throws says nothing changed, or may have partly happened on a rename`() =
        runTest {
            val failing = Failing(FakeMealRepository(listOf(aDayOfThree()))).apply { writes = true }
            val problems = RecordingProblemLog()
            val model = watched(viewModel(mealRepository = failing, problems = problems))
            val eggs = ready(model).meals.single().items.first()

            model.correctItem(eggs, eggs.copy(kcal = 140))
            advanceUntilIdle()
            assertThat(ready(model).failed).isEqualTo(ActionRefused.NOTHING_CHANGED)

            model.correctItem(eggs, eggs.copy(name = "Omelette"))
            advanceUntilIdle()
            assertThat(ready(model).failed).isEqualTo(ActionRefused.MAYBE_PARTIAL)
            assertThat(problems.recorded.map { it.kind }).containsExactly("refused", "refused")
        }

    @Test
    fun `choosing everything when the day cannot be read says it could not be opened`() = runTest {
        val failing = Failing(FakeMealRepository(listOf(aDayOfThree())))
        val problems = RecordingProblemLog()
        val model = watched(viewModel(mealRepository = failing, problems = problems))

        failing.nextRead = true
        model.chooseAll()
        advanceUntilIdle()

        assertThat(ready(model).failed).isEqualTo(ActionRefused.COULD_NOT_OPEN)
        assertThat(ready(model).chosen).isEmpty()
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
    }

    @Test
    fun `a failure goes when he dismisses it, and when the next action starts`() = runTest {
        val failing = Failing(FakeMealRepository(listOf(aDayOfThree())))
        val model = watched(viewModel(mealRepository = failing, problems = RecordingProblemLog()))
        val meal = ready(model).meals.single()

        failing.writes = true
        model.setEatenAt(meal, hour = 9, minute = 0)
        advanceUntilIdle()
        model.dismissRefusal()
        advanceUntilIdle()
        assertThat(ready(model).failed).isNull()

        model.setEatenAt(meal, hour = 9, minute = 0)
        advanceUntilIdle()
        failing.writes = false
        model.setEatenAt(meal, hour = 10, minute = 0)
        advanceUntilIdle()
        assertThat(ready(model).failed).isNull()
    }

    /** A refusal and a failure share one slot: the latest of the two is the one on screen. */
    @Test
    fun `a failure takes a refusal's place`() = runTest {
        val failing = Failing(FakeMealRepository(listOf(aDayOfThree())))
        val model = watched(viewModel(mealRepository = failing, problems = RecordingProblemLog()))
        val meal = ready(model).meals.single()

        model.setEatenAt(meal, hour = 23, minute = 0)
        advanceUntilIdle()
        assertThat(ready(model).refusal).isNotNull()

        failing.writes = true
        model.setEatenAt(meal, hour = 9, minute = 0)
        advanceUntilIdle()
        assertThat(ready(model).refusal).isNull()
        assertThat(ready(model).failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
    }

    /**
     * The once-per-open work — the weekly revision, milestones, the day's encouragement — is written
     * down when it throws, and the day says nothing: he did not do anything to be told about.
     */
    @Test
    fun `once-per-open work that throws is written down and the day still opens`() = runTest {
        val problems = RecordingProblemLog()
        val model = watched(
            viewModel(weights = FakeWeightRepository(failing = true), problems = problems),
        )

        assertThat(ready(model).failed).isNull()
        assertThat(problems.recorded).isNotEmpty()
        assertThat(problems.recorded.map { it.kind }.distinct()).containsExactly("refused")
    }

    private fun aPacket() = Product(
        barcode = "1234567890123",
        name = "Crackers",
        brand = null,
        kcalPer100g = 400.0,
        proteinPer100g = 10.0,
        carbsPer100g = 70.0,
        fatPer100g = 10.0,
        servingSizeG = 30.0,
    )

    /**
     * A meal store that throws where a test says to, and is the store it wraps everywhere else.
     *
     * [writes] makes every write throw; [deleteRefusedFor] only deletes of those rows; [nextRead] the
     * next read of a day, once — once, because the day's own state reads the same days, and a read
     * that kept failing would fail the screen rather than the action under test.
     */
    private class Failing(private val inner: MealRepository) : MealRepository by inner {
        var writes = false
        var deleteRefusedFor: Set<Long> = emptySet()
        var nextRead = false

        private fun refuse(): Nothing = throw IllegalStateException("disk full")

        override fun observeDay(epochDay: Long): Flow<List<Meal>> =
            inner.observeDay(epochDay).map { meals ->
                if (nextRead) {
                    nextRead = false
                    refuse()
                }
                meals
            }

        override suspend fun log(meal: Meal): List<Long> =
            if (writes) refuse() else inner.log(meal)

        override suspend fun updateItem(item: FoodItem) =
            if (writes) refuse() else inner.updateItem(item)

        override suspend fun setEatenAt(mealId: Long, atMillis: Long) =
            if (writes) refuse() else inner.setEatenAt(mealId, atMillis)

        override suspend fun deleteItem(itemId: Long): DeletedEntry? =
            if (writes || itemId in deleteRefusedFor) refuse() else inner.deleteItem(itemId)

        override suspend fun restore(entry: DeletedEntry) =
            if (writes) refuse() else inner.restore(entry)

        override suspend fun gatherIntoSavedMeal(epochDay: Long, itemIds: List<Long>, savedMealId: Long) =
            if (writes) refuse() else inner.gatherIntoSavedMeal(epochDay, itemIds, savedMealId)
    }

    private fun ready(model: DayViewModel): DayUiState.Ready =
        model.state.value as DayUiState.Ready

    /**
     * The state only flows while something is collecting it, and every assertion above reads the
     * choosing out of it — so a test that never collected would be asserting on `Loading`.
     */
    private fun TestScope.watched(model: DayViewModel): DayViewModel {
        backgroundScope.launch { model.state.collect { } }
        advanceUntilIdle()
        return model
    }

    /**
     * The ordinary stand-in with its write held open, so the window a write takes can be looked at.
     *
     * A real write is two database round trips, and the screen keeps drawing throughout. Every
     * other test lets that window pass in one `advanceUntilIdle`, which is exactly how a defect
     * that lives inside it stays invisible: what is on screen while the rows are on their way can
     * only be asserted if the rows can be made to wait.
     */
    private class WriteHeldOpen(val store: FakeMealRepository) : MealRepository by store {
        private val landed = CompletableDeferred<Unit>()

        override suspend fun log(meal: Meal): List<Long> {
            landed.await()
            return store.log(meal)
        }

        /** Let the rows arrive. */
        fun letItLand() {
            landed.complete(Unit)
        }
    }

    private class FakeMealRepository(initial: List<Meal> = emptyList()) : MealRepository {
        private val state = MutableStateFlow(initial)
        val logged = mutableListOf<Meal>()

        /**
         * The row-id counter, standing in for `sqlite_sequence`, starting above every seeded id.
         *
         * A row written with no id gets one, because the choice a description leaves behind is a set
         * of ids (D46, issue #24) and rows all sharing id 0 could not be told apart. A row seeded
         * with an id keeps it, so every assertion written before this existed still names the row it
         * always named, and [logged] still records the meal exactly as it was passed.
         */
        private var lastItemId: Long = initial.flatMap { it.items }.maxOfOrNull { it.id } ?: 0L
        val updated = mutableListOf<FoodItem>()
        val deleted = mutableListOf<Long>()

        /** Each gathering, as the day it was on, the rows moved, and the meal they went under. */
        val gathered = mutableListOf<Triple<Long, List<Long>, Long>>()

        override fun observeDay(epochDay: Long): Flow<List<Meal>> =
            state.map { meals -> meals.filter { it.epochDay == epochDay } }

        override suspend fun kcalByDaySince(fromEpochDay: Long): Map<Long, Int> =
            state.value
                .filter { it.epochDay >= fromEpochDay }
                .groupBy { it.epochDay }
                .mapValues { (_, meals) -> meals.sumOf { meal -> meal.items.sumOf { it.kcal } } }

        override fun observeLoggedDays(): Flow<Set<Long>> =
            state.map { meals -> meals.map { it.epochDay }.toSet() }

        override suspend fun log(meal: Meal): List<Long> {
            logged += meal
            val written = meal.copy(
                items = meal.items.map { item ->
                    if (item.id != 0L) {
                        item.also { lastItemId = maxOf(lastItemId, it.id) }
                    } else {
                        item.copy(id = ++lastItemId)
                    }
                },
            )
            state.value = state.value + written
            return written.items.map { it.id }
        }

        override suspend fun rowsWithNoFood(): List<DetachedRow> =
            state.value.flatMap { it.items }.filter { it.foodId == null }.map { DetachedRow(it.id, it.name) }

        override suspend fun attachRow(itemId: Long, foodId: Long) {
            state.value = state.value.map { meal ->
                meal.copy(items = meal.items.map { if (it.id == itemId && it.foodId == null) it.copy(foodId = foodId) else it })
            }
        }

        override suspend fun updateItem(item: FoodItem) {
            updated += item
        }

        /** Performed rather than recorded: the day has to be seen to move for a time to be tested. */
        override suspend fun setEatenAt(mealId: Long, atMillis: Long) {
            state.value = state.value.map {
                if (it.id == mealId) it.copy(loggedAtMillis = atMillis) else it
            }
        }

        /**
         * Recorded, and hands back nothing. What undo puts back is tested against the PERFORMING
         * stand-in, `InMemoryMealRepository`, where the day can be seen to change (issue #25).
         */
        override suspend fun deleteItem(itemId: Long): DeletedEntry? {
            deleted += itemId
            return null
        }

        /** Every restore asked for, recorded; see [deleteItem] for where undo is really tested. */
        val restored = mutableListOf<DeletedEntry>()

        override suspend fun restore(entry: DeletedEntry) {
            restored += entry
        }

        /** A row taken off the day from somewhere else, while the day screen still holds its id. */
        fun rowVanishes(itemId: Long) {
            state.value = state.value.map { meal ->
                meal.copy(items = meal.items.filterNot { it.id == itemId })
            }
        }

        /**
         * Recorded rather than performed: what this does to the stored rows is the one thing a fake
         * cannot prove, and `RoomMealRepositoryTest` proves it against a real database in CI.
         */
        override suspend fun gatherIntoSavedMeal(
            epochDay: Long,
            itemIds: List<Long>,
            savedMealId: Long,
        ) {
            gathered += Triple(epochDay, itemIds, savedMealId)
        }
    }
}
