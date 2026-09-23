package com.metaself.app.ui.screen.mealbuilder

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.FoundOrCreated
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.CannotCount
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/**
 * Building a meal out of foods chosen in the food list.
 *
 * Pure but for the view model's own scope, so JUnit 5 — `org.junit.jupiter.api.Test`, never
 * `org.junit.Test`. The two annotations look identical at the call site and the wrong one produces
 * a test that silently never runs.
 *
 * **The thing under test is that nothing is guessed for him.** A component cannot exist without an
 * amount — `MealComponent` requires one greater than zero — and nothing may fill one in, because a
 * default typed into a box the owner then saves is indistinguishable from his own number (D4). So
 * the foods he chose arrive as rows waiting for an amount, and each becomes a real component only
 * at the moment its amount is one the food can actually be costed at.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealBuilderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Knows what 100 g of it are worth, so it can be weighed. */
    private val cucumber = aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)

    /** Knows only what one spoon of it is worth, so it can be counted and not weighed. */
    private val oil = aFood("Olive oil", FoodFacts(perUnit = aPerUnit("spoon", 119.0))).copy(id = 2)

    /** A food with a real brand, as a scanned packet gives one. Third in any list that holds it. */
    private val milk = aFood("Milk").copy(id = 3, brand = "Dairyco")

    @Test
    fun `foods chosen in the list arrive waiting for an amount`() = runTest(dispatcher) {
        val meals = withSalad()
        val viewModel = opened(carrying(mealId = 1, foods = "1,2"), meals)

        assertThat(viewModel.state.value.pending.map { it.food.name })
            .containsExactly("Cucumber", "Olive oil").inOrder()
        // Waiting, not in it: nothing is in the meal until an amount says how much.
        assertThat(meals.current.single().components).isEmpty()
    }

    /** The bare route still opens an ordinary builder with nothing waiting in it. */
    @Test
    fun `a builder opened with nothing chosen has nothing waiting`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withSalad())

        assertThat(viewModel.state.value.pending).isEmpty()
    }

    /**
     * What the route carries is the list he chose, not the list still outstanding — and Android is
     * entitled to kill this process and rebuild the screen from that same route at any time.
     *
     * The defect this guards against: a food already put into the meal came back as an empty waiting
     * row after that rebuild. Confirming it would have written over an amount he had already entered,
     * in a builder that then offered no way to change a part's amount. So what the meal
     * already holds is never offered again, here for the same reason it is never offered in the
     * search results.
     */
    @Test
    fun `a chosen food the meal already holds is not waiting again`() = runTest(dispatcher) {
        val meals = withSaladHolding(
            MealComponent(id = 900, food = cucumber, amount = 100.0, countedAs = CountedAs.GRAMS),
        )
        val viewModel = opened(carrying(mealId = 1, foods = "1,2"), meals)

        assertThat(viewModel.state.value.pending.map { it.food.name }).containsExactly("Olive oil")
        assertThat(meals.current.single().components.single().amount).isEqualTo(100.0)
    }

    /**
     * D4, at the one place it is easiest to break: a number typed into the box for him is a number
     * he cannot tell from his own the moment he saves it.
     */
    @Test
    fun `nothing is filled in for him`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1, foods = "1,2"), withSalad())

        assertThat(viewModel.state.value.pending.map { it.amount }).containsExactly("", "")
    }

    /**
     * Each food arrives counted the way it can be counted, which is a fact about the food rather
     * than a guess about the amount.
     */
    @Test
    fun `each waiting food is counted the only way it can be`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1, foods = "1,2"), withSalad())

        assertThat(viewModel.state.value.pending.map { it.countedAs })
            .containsExactly(CountedAs.GRAMS, CountedAs.UNITS).inOrder()
    }

    /**
     * The defect this guards against: the field is typed into one character at a time, so a row that
     * went in the instant what was typed could be costed went in at "1" — one gram of cucumber — and
     * took its own field off the screen, leaving the "00" with nowhere to land and no way back,
     * because a part's amount could not then be changed once it was in.
     *
     * So the row stays put while he types, showing what the amount so far comes to, and joins the
     * meal only when he says so.
     */
    @Test
    fun `typing an amount a digit at a time puts nothing in until he says so`() =
        runTest(dispatcher) {
            val meals = withSalad()
            val viewModel = opened(carrying(mealId = 1, foods = "1"), meals)

            viewModel.setPendingAmount(1, "1")
            advanceUntilIdle()
            viewModel.setPendingAmount(1, "10")
            advanceUntilIdle()
            viewModel.setPendingAmount(1, "100")
            advanceUntilIdle()

            assertThat(meals.current.single().components).isEmpty()
            val waiting = viewModel.state.value.pending.single()
            assertThat(waiting.amount).isEqualTo("100")
            // The running total is on screen the whole time he is typing, which is what the waiting
            // row is for.
            assertThat(waiting.preview?.kcal).isEqualTo(16)

            viewModel.confirmPending(1)
            advanceUntilIdle()

            val components = meals.current.single().components
            assertThat(components).hasSize(1)
            assertThat(components.single().amount).isEqualTo(100.0)
            assertThat(viewModel.state.value.pending).isEmpty()
        }

    @Test
    fun `an amount typed against a pending food puts it into the meal`() = runTest(dispatcher) {
        val meals = withSalad()
        val viewModel = opened(carrying(mealId = 1, foods = "1,2"), meals)

        viewModel.setPendingAmount(1, "100")
        viewModel.confirmPending(1)
        advanceUntilIdle()

        val component = meals.current.single().components.single()
        assertThat(component.food.name).isEqualTo("Cucumber")
        assertThat(component.amount).isEqualTo(100.0)
        assertThat(component.countedAs).isEqualTo(CountedAs.GRAMS)
        // It stops waiting the moment it is in, and the one still waiting is untouched.
        assertThat(viewModel.state.value.pending.map { it.food.name }).containsExactly("Olive oil")
        assertThat(viewModel.state.value.meal!!.components).hasSize(1)
    }

    /**
     * Shown with its reason rather than quietly refused, and the number he typed is kept where he
     * typed it — the same rule the rest of the builder follows.
     */
    @Test
    fun `an amount that cannot be costed leaves it pending and says why`() = runTest(dispatcher) {
        val meals = withSalad()
        val viewModel = opened(carrying(mealId = 1, foods = "2"), meals)

        viewModel.countPendingAs(2, CountedAs.GRAMS)
        viewModel.setPendingAmount(2, "50")
        advanceUntilIdle()

        viewModel.confirmPending(2)
        advanceUntilIdle()

        assertThat(meals.current.single().components).isEmpty()
        val waiting = viewModel.state.value.pending.single()
        assertThat(waiting.amount).isEqualTo("50")
        assertThat(waiting.cannotWeigh)
            .isEqualTo(CannotCount.NothingKnowsWhatOneWeighs("spoon"))
    }

    /** Neither does a half-typed number: nothing is put in until the amount is a real one. */
    @Test
    fun `a half typed amount puts nothing in the meal`() = runTest(dispatcher) {
        val meals = withSalad()
        val viewModel = opened(carrying(mealId = 1, foods = "1"), meals)

        viewModel.setPendingAmount(1, "0")
        advanceUntilIdle()

        assertThat(meals.current.single().components).isEmpty()
        assertThat(viewModel.state.value.pending).hasSize(1)
    }

    @Test
    fun `a pending food can be dropped without touching the meal`() = runTest(dispatcher) {
        val meals = withSalad()
        val viewModel = opened(carrying(mealId = 1, foods = "1,2"), meals)

        viewModel.setPendingAmount(1, "100")
        viewModel.confirmPending(1)
        advanceUntilIdle()
        viewModel.dropPending(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.pending).isEmpty()
        assertThat(meals.current.single().components.map { it.food.name })
            .containsExactly("Cucumber")
    }

    /**
     * Naming is still the one gate, because a meal is only something he built AND named — and the
     * foods he chose have to survive it, or choosing them would have been wasted on the way in.
     */
    @Test
    fun `naming the meal comes first, and pending foods survive it`() = runTest(dispatcher) {
        val meals = FakeSavedMealRepository().knowsAbout(cucumber, oil)
        val viewModel = opened(carrying(mealId = 0, foods = "1,2"), meals)

        assertThat(viewModel.state.value.needsAName).isTrue()
        assertThat(viewModel.state.value.pending).hasSize(2)

        viewModel.setName("Vegetable salad")
        viewModel.name()
        advanceUntilIdle()

        assertThat(viewModel.state.value.meal?.name).isEqualTo("Vegetable salad")
        assertThat(viewModel.state.value.pending.map { it.food.name })
            .containsExactly("Cucumber", "Olive oil").inOrder()
        assertThat(viewModel.state.value.pending.map { it.amount }).containsExactly("", "")
    }

    /**
     * The act the builder's question guards (D36). Nothing covered it before: the screen's question
     * and the nav host's delete-and-go-back both lean on this doing what it says.
     */
    @Test
    fun `deleting the meal removes it`() = runTest(dispatcher) {
        val meals = withSalad()
        val viewModel = opened(carrying(mealId = 1), meals)
        assertThat(viewModel.state.value.meal?.name).isEqualTo("Vegetable salad")

        viewModel.delete()
        advanceUntilIdle()

        assertThat(meals.observeOffered().first()).isEmpty()
        assertThat(viewModel.state.value.meal).isNull()
    }

    // --- Searching what is already here (D41, issue #14) ------------------------------------------

    /**
     * The agent walk: Cucumber already on screen in the salad, `Cuc` typed, and the builder said
     * nothing matched. It matched — the food is left out because a meal holds a food once — so
     * the search names it rather than denying it is there.
     */
    @Test
    fun `a search that finds only what is in the meal names it, and does not say nothing matches`() =
        runTest(dispatcher) {
            val viewModel = opened(carrying(mealId = 1), withCucumberIn())

            viewModel.search("Cuc")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.candidates).isEmpty()
            assertThat(state.alreadyIn.map { it.name }).containsExactly("Cucumber")
            assertThat(state.alreadyWaiting).isEmpty()
            assertThat(state.searchedAndFoundNothing).isFalse()
        }

    /** Naming it is not offering it: amounts of a food already in are issue #17, not this. */
    @Test
    fun `a food already in the meal is still not offered a second time`() = runTest(dispatcher) {
        val meals = withCucumberIn()
        val viewModel = opened(carrying(mealId = 1), meals)

        viewModel.search("Cuc")
        advanceUntilIdle()
        assertThat(viewModel.state.value.candidates.map { it.id }).doesNotContain(1L)

        viewModel.beginAdding(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.adding).isNull()
        val inMeal = meals.current.single().components.single()
        assertThat(inMeal.food.name).isEqualTo("Cucumber")
        assertThat(inMeal.amount).isEqualTo(100.0)
    }

    @Test
    fun `a search that finds nothing at all still says so`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withCucumberIn())

        viewModel.search("Tahini")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.candidates).isEmpty()
        assertThat(state.alreadyIn).isEmpty()
        assertThat(state.alreadyWaiting).isEmpty()
        assertThat(state.searchedAndFoundNothing).isTrue()
    }

    /**
     * An empty search matches everything, so without a guard the sentence would list the whole meal
     * before he had typed a letter.
     */
    @Test
    fun `nothing is named as already in before he types`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withCucumberIn())

        listOf("", "   ").forEach { query ->
            viewModel.search(query)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.alreadyIn).isEmpty()
            assertThat(state.alreadyWaiting).isEmpty()
            assertThat(state.candidates.map { it.name }).containsExactly("Olive oil")
        }
    }

    /**
     * Every food the search found is accounted for on screen — as a row to tap or a name in the
     * sentence. Offering Cucumber pickle and silently dropping Cucumber would be the same lie, only
     * smaller.
     */
    @Test
    fun `new foods are offered and the one already in is named, together`() = runTest(dispatcher) {
        val pickle = aFood("Cucumber pickle").copy(id = 3)
        val viewModel = opened(
            carrying(mealId = 1),
            withCucumberIn(),
            FakeFoodRepository(listOf(cucumber, oil, pickle)),
        )

        viewModel.search("cucumber")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.candidates.map { it.name }).containsExactly("Cucumber pickle")
        assertThat(state.alreadyIn.map { it.name }).containsExactly("Cucumber")
        assertThat(state.searchedAndFoundNothing).isFalse()
    }

    /**
     * A hidden food is kept out of every picker but stays in the meals that hold it, on screen. So
     * what is already in is searched over the meal itself, not over the foods on offer — or the
     * builder would deny the very row it is drawing.
     */
    @Test
    fun `a food in the meal that has since been hidden is still named`() = runTest(dispatcher) {
        val viewModel = opened(
            carrying(mealId = 1),
            withCucumberIn(),
            FakeFoodRepository(listOf(cucumber.copy(hidden = true), oil)),
        )

        viewModel.search("Cuc")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.alreadyIn.map { it.name }).containsExactly("Cucumber")
        assertThat(state.searchedAndFoundNothing).isFalse()
    }

    /**
     * Chosen in the list and waiting for an amount is not in the meal — D37 made the heading say so
     * — so it is named as what it is, never as "in this meal".
     */
    @Test
    fun `a food waiting for an amount is named as waiting, not as in the meal`() =
        runTest(dispatcher) {
            val viewModel = opened(carrying(mealId = 1, foods = "2"), withSalad())

            viewModel.search("oil")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.candidates).isEmpty()
            assertThat(state.alreadyWaiting.map { it.name }).containsExactly("Olive oil")
            assertThat(state.alreadyIn).isEmpty()
            assertThat(state.searchedAndFoundNothing).isFalse()
        }

    /** The builder uses the same search as My foods, so the brand finds a food here too. */
    @Test
    fun `the builder finds a food by its brand`() = runTest(dispatcher) {
        val offered = opened(
            carrying(mealId = 1),
            withSalad(),
            FakeFoodRepository(listOf(cucumber, oil, milk)),
        )

        offered.search("dairyco")
        advanceUntilIdle()

        assertThat(offered.state.value.candidates.map { it.name }).containsExactly("Milk")

        // With the milk in the meal instead, the same search names it as already there.
        val holding = opened(
            carrying(mealId = 1),
            withSaladHolding(
                MealComponent(id = 900, food = milk, amount = 100.0, countedAs = CountedAs.GRAMS),
            ),
            FakeFoodRepository(listOf(cucumber, oil, milk)),
        )

        holding.search("dairyco")
        advanceUntilIdle()

        assertThat(holding.state.value.candidates).isEmpty()
        assertThat(holding.state.value.alreadyIn.map { it.name }).containsExactly("Milk")
        assertThat(holding.state.value.searchedAndFoundNothing).isFalse()
    }

    /**
     * Brand and name typed together find the food here too (D41, issue #33) — offered when it is not
     * in the meal, and named as already there when it is, never *Nothing matches*.
     */
    @Test
    fun `brand and name typed together offer the food, or name it when it is already in the meal`() =
        runTest(dispatcher) {
            val offered = opened(
                carrying(mealId = 1),
                withSalad(),
                FakeFoodRepository(listOf(cucumber, oil, milk)),
            )

            offered.search("milk dairyco")
            advanceUntilIdle()

            assertThat(offered.state.value.candidates.map { it.name }).containsExactly("Milk")

            val holding = opened(
                carrying(mealId = 1),
                withSaladHolding(
                    MealComponent(id = 900, food = milk, amount = 100.0, countedAs = CountedAs.GRAMS),
                ),
                FakeFoodRepository(listOf(cucumber, oil, milk)),
            )

            holding.search("Dairyco milk")
            advanceUntilIdle()

            assertThat(holding.state.value.candidates).isEmpty()
            assertThat(holding.state.value.alreadyIn.map { it.name }).containsExactly("Milk")
            assertThat(holding.state.value.searchedAndFoundNothing).isFalse()
        }

    /** The waiting foods are searched by the same matcher, so they are found word by word too. */
    @Test
    fun `brand and name typed together name a food waiting for an amount`() = runTest(dispatcher) {
        val viewModel = opened(
            carrying(mealId = 1, foods = "3"),
            withSalad(),
            FakeFoodRepository(listOf(cucumber, oil, milk)),
        )
        advanceUntilIdle()
        assertThat(viewModel.state.value.pending.map { it.food.name }).containsExactly("Milk")

        viewModel.search("milk dairyco")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.candidates).isEmpty()
        assertThat(state.alreadyWaiting.map { it.name }).containsExactly("Milk")
        assertThat(state.alreadyIn).isEmpty()
        assertThat(state.searchedAndFoundNothing).isFalse()
    }

    /**
     * One search can find a food in the meal and another waiting for an amount at once. Both are
     * named, each as what it is, and neither lets the screen say nothing matched.
     */
    @Test
    fun `a food in the meal and one waiting, found by one search, are both named`() =
        runTest(dispatcher) {
            val pickle = aFood("Cucumber pickle").copy(id = 3)
            val viewModel = opened(
                carrying(mealId = 1, foods = "3"),
                withCucumberIn(),
                FakeFoodRepository(listOf(cucumber, oil, pickle)),
            )
            advanceUntilIdle()
            assertThat(viewModel.state.value.pending.map { it.food.name })
                .containsExactly("Cucumber pickle")

            viewModel.search("cucumber")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.candidates).isEmpty()
            assertThat(state.alreadyIn.map { it.name }).containsExactly("Cucumber")
            assertThat(state.alreadyWaiting.map { it.name }).containsExactly("Cucumber pickle")
            assertThat(state.searchedAndFoundNothing).isFalse()
        }

    /**
     * An infinite amount used to be put in: the meal's own check wants an amount greater than
     * zero, and infinity is. Past its ceiling now (D42, issue #32), it is no amount this food can be
     * costed at, so the food goes on waiting with what he typed still in its box — the refusal is
     * the row's to show.
     */
    @Test
    fun `a waiting food with an unbelievable amount is not put in`() = runTest(dispatcher) {
        listOf("Infinity", "6000").forEach { typed ->
            val meals = withSalad()
            val viewModel = opened(carrying(mealId = 1, foods = "1"), meals)

            viewModel.setPendingAmount(1, typed)
            advanceUntilIdle()
            viewModel.confirmPending(1)
            advanceUntilIdle()

            assertThat(meals.current.single().components).isEmpty()
            val waiting = viewModel.state.value.pending.single()
            assertThat(waiting.food.name).isEqualTo("Cucumber")
            assertThat(waiting.amount).isEqualTo(typed)
            assertThat(waiting.amountTooMuch).isTrue()
        }
    }

    // --- issue #13 (D45): the other door that types figures into an existing food ---------------

    /** What he types into *make a food*: everything the form needs and nothing it does not. */
    private fun form(name: String, kcal: String) = FoodForm(
        name = name,
        kcalPer100g = kcal,
        proteinPer100g = "1",
        carbsPer100g = "5",
        fatPer100g = "0",
    )

    /**
     * *Make a food* reaches the same one door logging does, so it can replace a figure the food
     * already held in exactly the same way — and this panel is the only surface there is to say so.
     * No *Got it*: the line goes when the panel does.
     */
    @Test
    fun `making a food whose name is already his says what its figures now are`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(cucumber, oil))
        val viewModel = opened(carrying(mealId = 1), withSalad(), foods)

        viewModel.createFood(form("Cucumber", kcal = "25"))
        advanceUntilIdle()

        assertThat(foods.current.single { it.name == "Cucumber" }.facts.per100g!!.nutrients.kcal)
            .isEqualTo(25.0)
        val adding = viewModel.state.value.adding!!
        assertThat(adding.food.name).isEqualTo("Cucumber")
        assertThat(adding.retaughtNotice).contains("Cucumber")
        assertThat(adding.retaughtNotice)
            .contains("because you have just typed different numbers for it.")
    }

    /** A name belonging to nothing makes a food. Nothing was replaced, so nothing is said. */
    @Test
    fun `making a food under a new name says nothing`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(cucumber, oil))
        val viewModel = opened(carrying(mealId = 1), withSalad(), foods)

        viewModel.createFood(form("Kohlrabi", kcal = "27"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.adding!!.food.name).isEqualTo("Kohlrabi")
        assertThat(viewModel.state.value.adding!!.retaughtNotice).isNull()
    }

    /** Picking a food out of the list teaches it nothing, so that panel has nothing to say. */
    @Test
    fun `picking an existing food out of the list says nothing`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withSalad())

        viewModel.search("Cuc")
        advanceUntilIdle()
        viewModel.beginAdding(1)
        advanceUntilIdle()

        val adding = viewModel.state.value.adding!!
        assertThat(adding.food.name).isEqualTo("Cucumber")
        assertThat(adding.retaughtNotice).isNull()
    }

    /** The walk's salad: 100 g of Cucumber in it, and nothing else. */
    private fun withCucumberIn() = withSaladHolding(
        MealComponent(id = 900, food = cucumber, amount = 100.0, countedAs = CountedAs.GRAMS),
    )

    // --- When an action throws -----------------------------------------------------------------------
    //
    // An exception that got past the guard would fail each of these on its own: `runTest` reports a
    // coroutine's uncaught exception when it ends.

    @Test
    fun `naming that throws says nothing was changed and still asks for a name`() = runTest(dispatcher) {
        val meals = Failing(FakeSavedMealRepository().knowsAbout(cucumber, oil))
        val problems = RecordingProblemLog()
        val viewModel = opened(carrying(mealId = 0), meals, problems = problems)

        meals.writes = true
        viewModel.setName("Vegetable salad")
        viewModel.name()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.needsAName).isTrue()
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
        assertThat(problems.recorded.single().detail).contains("disk full")
    }

    /** The panel stays, amount and all, so putting it in is one tap again. */
    @Test
    fun `putting a food in that throws keeps what he typed`() = runTest(dispatcher) {
        val meals = Failing(withSalad())
        val problems = RecordingProblemLog()
        val viewModel = opened(carrying(mealId = 1), meals, problems = problems)

        viewModel.beginAdding(cucumber.id)
        viewModel.setAmount("100")
        meals.writes = true
        viewModel.confirmAdding()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.adding?.amount).isEqualTo("100")
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
    }

    /** The write happened, so "nothing was changed" would be untrue; what failed is opening it. */
    @Test
    fun `a write that lands but cannot be read back says it could not be opened`() =
        runTest(dispatcher) {
            val meals = Failing(withSalad())
            val viewModel = opened(carrying(mealId = 1), meals, problems = RecordingProblemLog())

            viewModel.beginAdding(cucumber.id)
            viewModel.setAmount("100")
            meals.reads = true
            viewModel.confirmAdding()
            advanceUntilIdle()

            assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.COULD_NOT_OPEN)
            meals.reads = false
            assertThat(meals.byId(1)!!.components.map { it.food.id }).containsExactly(cucumber.id)
        }

    @Test
    fun `a meal that cannot be read says it could not be opened`() = runTest(dispatcher) {
        val meals = Failing(withSalad()).apply { reads = true }
        val problems = RecordingProblemLog()
        val viewModel = opened(carrying(mealId = 1), meals, problems = problems)

        assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.COULD_NOT_OPEN)
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
    }

    @Test
    fun `renaming, removing and reordering that throw say nothing was changed`() =
        runTest(dispatcher) {
            val meals = Failing(
                withSaladHolding(
                    MealComponent(id = 7, food = cucumber, amount = 100.0, countedAs = CountedAs.GRAMS, position = 0),
                    MealComponent(id = 8, food = oil, amount = 1.0, countedAs = CountedAs.UNITS, position = 1),
                ),
            )
            val problems = RecordingProblemLog()
            val viewModel = opened(carrying(mealId = 1), meals, problems = problems)
            meals.writes = true

            viewModel.rename("Green salad")
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
            viewModel.remove(7)
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
            viewModel.move(7, by = 1)
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)

            assertThat(problems.recorded.map { it.kind }).containsExactly("refused", "refused", "refused")
            assertThat(viewModel.state.value.meal?.components?.map { it.id }).containsExactly(7L, 8L).inOrder()
        }

    /** The screen leaves only once the meal has gone, so a failure is still there to be read. */
    @Test
    fun `a delete that throws stays on the screen and says so`() = runTest(dispatcher) {
        val meals = Failing(withSalad())
        val viewModel = opened(carrying(mealId = 1), meals, problems = RecordingProblemLog())
        var left = false

        meals.writes = true
        viewModel.delete { left = true }
        advanceUntilIdle()

        assertThat(left).isFalse()
        assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.meal?.name).isEqualTo("Vegetable salad")

        meals.writes = false
        viewModel.delete { left = true }
        advanceUntilIdle()
        assertThat(left).isTrue()
    }

    @Test
    fun `making a food that throws says nothing was changed and keeps the form open`() =
        runTest(dispatcher) {
            val foods = object : FoodRepository by FakeFoodRepository(listOf(cucumber, oil)) {
                override suspend fun findOrCreate(
                    name: String,
                    brand: String?,
                    facts: FoodFacts,
                    barcode: String?,
                ): FoundOrCreated = throw IllegalStateException("disk full")
            }
            val problems = RecordingProblemLog()
            val viewModel = opened(carrying(mealId = 1), withSalad(), foods, problems)

            viewModel.beginCreatingFood()
            viewModel.createFood(form("Kohlrabi", kcal = "27"))
            advanceUntilIdle()

            assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
            assertThat(viewModel.state.value.creating).isTrue()
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
        }

    @Test
    fun `a failure goes when he dismisses it, and when the next action starts`() = runTest(dispatcher) {
        val meals = Failing(withSalad())
        val viewModel = opened(carrying(mealId = 1), meals, problems = RecordingProblemLog())

        meals.writes = true
        viewModel.rename("Green salad")
        advanceUntilIdle()
        viewModel.dismissRefusal()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed).isNull()

        viewModel.rename("Green salad")
        advanceUntilIdle()
        meals.writes = false
        viewModel.rename("Green salad")
        advanceUntilIdle()
        assertThat(viewModel.state.value.failed).isNull()
        assertThat(viewModel.state.value.meal?.name).isEqualTo("Green salad")
    }

    /**
     * A meal store that throws where a test says to, and is the store it wraps everywhere else:
     * [writes] makes every write throw, [reads] every read of a meal.
     */
    private class Failing(private val inner: SavedMealRepository) : SavedMealRepository by inner {
        var writes = false
        var reads = false

        private fun refuse(): Nothing = throw IllegalStateException("disk full")

        override suspend fun byId(id: Long): SavedMeal? = if (reads) refuse() else inner.byId(id)

        override suspend fun create(name: String): MealResult =
            if (writes) refuse() else inner.create(name)

        override suspend fun rename(mealId: Long, name: String): MealResult =
            if (writes) refuse() else inner.rename(mealId, name)

        override suspend fun put(mealId: Long, foodId: Long, amount: Double, countedAs: CountedAs) =
            if (writes) refuse() else inner.put(mealId, foodId, amount, countedAs)

        override suspend fun remove(componentId: Long) =
            if (writes) refuse() else inner.remove(componentId)

        override suspend fun reorder(mealId: Long, componentIdsInOrder: List<Long>) =
            if (writes) refuse() else inner.reorder(mealId, componentIdsInOrder)

        override suspend fun delete(mealId: Long) = if (writes) refuse() else inner.delete(mealId)
    }

    // --- A part already in the meal gets its amount changed (D53 §7, #4) ------------------------

    /** Knows what 100 g of it are worth. Only here as the third part, after the oil. */
    private val tomato = aFood("Tomato", FoodFacts(per100g = aPer100g(18.0))).copy(id = 4)

    private fun withThreeParts() = FakeSavedMealRepository(
        listOf(
            SavedMeal(
                name = "Vegetable salad",
                components = listOf(
                    MealComponent(id = 900, food = cucumber, amount = 100.0, countedAs = CountedAs.GRAMS, position = 0),
                    MealComponent(id = 901, food = oil, amount = 2.0, countedAs = CountedAs.UNITS, position = 1),
                    MealComponent(id = 902, food = tomato, amount = 80.0, countedAs = CountedAs.GRAMS, position = 2),
                ),
            ),
        ),
    ).knowsAbout(cucumber, oil, tomato)

    @Test
    fun `tapping a part opens its own amount and way of counting`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withThreeParts())

        viewModel.beginChanging(901)
        advanceUntilIdle()

        val adding = viewModel.state.value.adding!!
        assertThat(adding.food).isEqualTo(oil)
        assertThat(adding.countedAs).isEqualTo(CountedAs.UNITS)
        assertThat(adding.amount).isEqualTo("2")
        assertThat(adding.changing).isEqualTo(901L)
        // 2 spoons at 119 each, before anything is typed.
        assertThat(adding.preview!!.kcal).isEqualTo(238)
    }

    @Test
    fun `changing it keeps the part where it was`() = runTest(dispatcher) {
        val meals = withThreeParts()
        val viewModel = opened(carrying(mealId = 1), meals)

        viewModel.beginChanging(901)
        viewModel.setAmount("3")
        viewModel.confirmAdding()
        advanceUntilIdle()

        val parts = meals.current.single().components
        assertThat(parts.map { it.food.name }).containsExactly("Cucumber", "Olive oil", "Tomato").inOrder()
        assertThat(parts.single { it.food.id == oil.id }.amount).isEqualTo(3.0)
        assertThat(parts.map { it.id }).containsExactly(900L, 901L, 902L).inOrder()
        assertThat(viewModel.state.value.adding).isNull()
    }

    @Test
    fun `leaving the panel changes nothing`() = runTest(dispatcher) {
        val meals = withThreeParts()
        val viewModel = opened(carrying(mealId = 1), meals)

        viewModel.beginChanging(901)
        viewModel.setAmount("3")
        viewModel.cancelAdding()
        advanceUntilIdle()

        assertThat(meals.current.single().components.single { it.id == 901L }.amount).isEqualTo(2.0)
    }

    /** Grams or whole ones, as for a food being put in: only the ways the food knows. */
    @Test
    fun `a part can be switched to grams only if its food can be weighed`() = runTest(dispatcher) {
        val meals = withThreeParts()
        val viewModel = opened(carrying(mealId = 1), meals)

        viewModel.beginChanging(901)
        advanceUntilIdle()
        assertThat(viewModel.state.value.adding!!.cannotWeigh).isNotNull()
        viewModel.countAs(CountedAs.GRAMS)
        viewModel.setAmount("30")
        advanceUntilIdle()
        assertThat(viewModel.state.value.adding!!.canAdd).isFalse()
        viewModel.confirmAdding()
        advanceUntilIdle()
        assertThat(meals.current.single().components.single { it.id == 901L }.amount).isEqualTo(2.0)

        viewModel.cancelAdding()
        viewModel.beginChanging(900)
        viewModel.countAs(CountedAs.GRAMS)
        viewModel.setAmount("150")
        viewModel.confirmAdding()
        advanceUntilIdle()
        assertThat(meals.current.single().components.single { it.id == 900L }.amount).isEqualTo(150.0)
    }

    /**
     * The box holds the part's own number exactly (D30): a quarter spoon is "0.25", not the "0.3"
     * the day's one-decimal words would make of it — pressed without a change, that would save 0.3.
     * And a smaller one is not shown as "0.0", which no box accepts.
     */
    @Test
    fun `a part's own amount opens exactly, and Change it untouched keeps it`() = runTest(dispatcher) {
        val meals = withSaladHolding(
            MealComponent(id = 900, food = cucumber, amount = 100.0, countedAs = CountedAs.GRAMS, position = 0),
            MealComponent(id = 901, food = oil, amount = 0.25, countedAs = CountedAs.UNITS, position = 1),
        )
        val viewModel = opened(carrying(mealId = 1), meals)

        viewModel.beginChanging(901)
        advanceUntilIdle()
        assertThat(viewModel.state.value.adding!!.amount).isEqualTo("0.25")
        viewModel.confirmAdding()
        advanceUntilIdle()
        assertThat(meals.current.single().components.single { it.id == 901L }.amount).isEqualTo(0.25)

        viewModel.beginChanging(901)
        viewModel.setAmount("0.04")
        viewModel.confirmAdding()
        advanceUntilIdle()
        viewModel.beginChanging(901)
        advanceUntilIdle()
        val adding = viewModel.state.value.adding!!
        assertThat(adding.amount).isEqualTo("0.04")
        assertThat(adding.canAdd).isTrue()
    }

    /** D41's sentence now leads somewhere: the food named as already in opens its part. */
    @Test
    fun `a food already in the meal, found by the search, opens that part`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withThreeParts())

        viewModel.search("cucum")
        advanceUntilIdle()
        assertThat(viewModel.state.value.alreadyIn.map { it.id }).containsExactly(cucumber.id)

        viewModel.beginChangingFood(cucumber.id)
        advanceUntilIdle()

        val adding = viewModel.state.value.adding!!
        assertThat(adding.changing).isEqualTo(900L)
        assertThat(adding.amount).isEqualTo("100")
        assertThat(adding.countedAs).isEqualTo(CountedAs.GRAMS)
    }

    @Test
    fun `the panel's box follows the same ceiling`() = runTest(dispatcher) {
        val meals = withThreeParts()
        val viewModel = opened(carrying(mealId = 1), meals)

        viewModel.beginChanging(901)
        viewModel.setAmount("101")
        advanceUntilIdle()

        val adding = viewModel.state.value.adding!!
        assertThat(adding.amountTooMuch).isTrue()
        assertThat(adding.canAdd).isFalse()
        viewModel.confirmAdding()
        advanceUntilIdle()
        assertThat(meals.current.single().components.single { it.id == 901L }.amount).isEqualTo(2.0)
    }

    /** A food picked from the search to put in is not changing anything. */
    @Test
    fun `a food being put in is not a part being changed`() = runTest(dispatcher) {
        val viewModel = opened(carrying(mealId = 1), withSalad())

        viewModel.beginAdding(cucumber.id)
        advanceUntilIdle()

        assertThat(viewModel.state.value.adding!!.changing).isNull()
    }

    private fun withSalad() =
        FakeSavedMealRepository(listOf(SavedMeal(name = "Vegetable salad"))).knowsAbout(cucumber, oil)

    private fun withSaladHolding(vararg components: MealComponent) = FakeSavedMealRepository(
        listOf(SavedMeal(name = "Vegetable salad", components = components.toList())),
    ).knowsAbout(cucumber, oil)

    /** What the route carried, exactly as the nav host hands it over. */
    private fun carrying(mealId: Long, foods: String? = null) = SavedStateHandle(
        buildMap<String, Any?> {
            put("mealId", mealId.toString())
            foods?.let { put("foods", it) }
        },
    )

    /**
     * The state only flows while something is collecting it, and everything here reads the meal and
     * the waiting foods out of it — so a test that never collects would be building nothing.
     */
    private fun TestScope.opened(
        savedState: SavedStateHandle,
        meals: SavedMealRepository,
        foods: FoodRepository = FakeFoodRepository(listOf(cucumber, oil)),
        problems: ProblemLog = ProblemLog.NONE,
    ): MealBuilderViewModel {
        val viewModel = MealBuilderViewModel(meals, foods, Now { 1_000 }, problems, savedState)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()
        return viewModel
    }
}
