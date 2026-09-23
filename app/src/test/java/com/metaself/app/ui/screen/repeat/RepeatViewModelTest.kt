package com.metaself.app.ui.screen.repeat

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.portion.Portions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
 * Changing how much of a past meal is being logged again.
 *
 * The case this pins: a row logged as two portions, repeated the next day, with no way to make it
 * one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val twoSlices = anItem(
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
    )

    private val risotto = anItem(
        name = "Risotto",
        portion = "~280 g",
        portionAmount = 280.0,
        portionUnit = "g",
        kcal = 600,
    )

    // --- A meal he built, for one day only -------------------------------------------------------

    /**
     * The adjustment starts at exactly what the meal says, and what he does to it does not touch
     * the meal.
     */
    @Test
    fun `adjusting a meal changes nothing about the meal`() = runTest(dispatcher) {
        val meals = FakeSavedMealRepository(listOf(salad()))
        val viewModel = watched(savedMeals = meals)

        viewModel.beginAdjusting(0)
        advanceUntilIdle()
        viewModel.setComponentAmount(10, 200.0)
        advanceUntilIdle()

        assertThat(viewModel.state.value.adjusting!!.rows.first().amount).isEqualTo(200.0)
        // The definition is untouched: tomorrow's salad still has 100 g of cucumber in it.
        assertThat(meals.current.single().components.first().amount).isEqualTo(100.0)
    }

    /**
     * A one-day adjustment may ADD, not only remove and rescale — and what is added reaches the
     * day's record with its own numbers, and marks the day's row as adjusted.
     */
    @Test
    fun `something not in the meal at all can be added for one day, and reaches the record`() =
        runTest(dispatcher) {
            val meals = FakeSavedMealRepository(listOf(salad()))
            val viewModel = watched(pantry(), meals)

            viewModel.beginAdjusting(0)
            viewModel.beginAddingToMeal()
            viewModel.searchToAdd("bread")
            advanceUntilIdle()
            viewModel.pickToAdd(BREAD)
            viewModel.setAddedAmount("60")
            advanceUntilIdle()
            viewModel.putItIn()
            advanceUntilIdle()

            val adjusting = viewModel.state.value.adjusting!!
            assertThat(adjusting.rows.map { it.food.name })
                .containsExactly("Cucumber", "Olive oil", "Bread").inOrder()
            // The step closes once it has put the food in.
            assertThat(adjusting.finding).isNull()
            assertThat(adjusting.adding).isNull()
            // 16 + 119 + 60 g of bread at 250 kcal per 100 g, which is 150.
            assertThat(adjusting.totalKcal).isEqualTo(285)

            val logged = viewModel.adjusted()!!
            assertThat(logged.adjusted).isTrue()
            assertThat(logged.savedMealId).isEqualTo(1)
            val bread = logged.items.last()
            assertThat(bread.name).isEqualTo("Bread")
            assertThat(bread.kcal).isEqualTo(150)
            assertThat(bread.portionAmount).isEqualTo(60.0)
            assertThat(bread.portionUnit).isEqualTo("g")
            assertThat(bread.foodId).isEqualTo(BREAD)
            // And the meal itself is untouched: tomorrow's salad has no bread in it.
            assertThat(meals.current.single().components.map { it.food.name })
                .containsExactly("Cucumber", "Olive oil")
        }

    /**
     * The screen's search closes whatever is open, on purpose. The panel's own search must not, or
     * typing the name of the thing to add would throw away the adjustment it was to be added to.
     */
    @Test
    fun `the panel's own search keeps the adjustment open`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        viewModel.setComponentAmount(10, 200.0)
        viewModel.beginAddingToMeal()
        viewModel.searchToAdd("tom")
        advanceUntilIdle()

        val adjusting = viewModel.state.value.adjusting!!
        assertThat(adjusting.rows.first().amount).isEqualTo(200.0)
        assertThat(adjusting.finding).isEqualTo("tom")
        assertThat(adjusting.offered.map { it.name }).containsExactly("Tomato")
    }

    /**
     * A meal holds a food once (D41). The panel's search does not offer one the adjustment already
     * holds — it names it instead, as the builder does, rather than letting a tap do nothing.
     */
    @Test
    fun `a food the meal already holds is named, not offered`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        viewModel.searchToAdd("cucumber")
        advanceUntilIdle()

        val adjusting = viewModel.state.value.adjusting!!
        assertThat(adjusting.offered).isEmpty()
        assertThat(adjusting.alreadyIn.map { it.name }).containsExactly("Cucumber")
    }

    /** With nothing typed, everything not already in it is offered, and nothing is named. */
    @Test
    fun `with nothing typed every food not already in it is offered`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        advanceUntilIdle()

        val adjusting = viewModel.state.value.adjusting!!
        assertThat(adjusting.offered.map { it.name })
            .containsExactly("Bread", "Tomato", "Rice", "Bread roll")
        assertThat(adjusting.alreadyIn).isEmpty()
    }

    /** A food removed for today can be put back, and is offered again for that reason. */
    @Test
    fun `a food removed for today is offered again`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        viewModel.removeComponent(10)
        viewModel.beginAddingToMeal()
        advanceUntilIdle()

        assertThat(viewModel.state.value.adjusting!!.offered.map { it.name }).contains("Cucumber")
    }

    /**
     * Two added, one removed, a third added: each row keeps an identity of its own. Derived from
     * the row count, the third took the second's, and removing or rescaling one then acted on both.
     */
    @Test
    fun `rows added after a removal never share an identity`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        advanceUntilIdle()

        viewModel.addToAdjustment(food(BREAD), amount = 60.0, countedAs = CountedAs.GRAMS)
        viewModel.addToAdjustment(food(TOMATO), amount = 100.0, countedAs = CountedAs.GRAMS)
        advanceUntilIdle()
        val bread = viewModel.state.value.adjusting!!.rows.single { it.food.id == BREAD }
        viewModel.removeComponent(bread.id)
        viewModel.addToAdjustment(food(RICE), amount = 150.0, countedAs = CountedAs.GRAMS)
        advanceUntilIdle()

        val rows = viewModel.state.value.adjusting!!.rows
        assertThat(rows.map { it.id }).containsNoDuplicates()

        val tomato = rows.single { it.food.id == TOMATO }
        viewModel.setComponentAmount(tomato.id, 200.0)
        viewModel.removeComponent(rows.single { it.food.id == RICE }.id)
        advanceUntilIdle()

        val after = viewModel.state.value.adjusting!!.rows
        assertThat(after.map { it.food.name }).containsExactly("Cucumber", "Olive oil", "Tomato")
            .inOrder()
        assertThat(after.single { it.food.id == TOMATO }.amount).isEqualTo(200.0)
    }

    /**
     * Something added goes on the record last, where the panel draws it. Its place was derived from
     * the row count, so after removals it could sort between two things added earlier.
     */
    @Test
    fun `something added after a removal goes on the record last`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        advanceUntilIdle()

        viewModel.addToAdjustment(food(BREAD), amount = 60.0, countedAs = CountedAs.GRAMS)
        viewModel.addToAdjustment(food(TOMATO), amount = 100.0, countedAs = CountedAs.GRAMS)
        viewModel.removeComponent(10)
        viewModel.removeComponent(11)
        viewModel.addToAdjustment(food(RICE), amount = 150.0, countedAs = CountedAs.GRAMS)
        advanceUntilIdle()

        assertThat(viewModel.adjusted()!!.items.map { it.name })
            .containsExactly("Bread", "Tomato", "Rice").inOrder()
    }

    /** The builder appends at one past the highest place, so a meal's own places can have gaps. */
    @Test
    fun `something added to a meal with gaps in its order still goes on last`() =
        runTest(dispatcher) {
            val gappy = salad().let { meal ->
                meal.copy(components = meal.components.mapIndexed { at, part -> part.copy(position = at * 5) })
            }
            val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(gappy)))
            viewModel.beginAdjusting(0)
            advanceUntilIdle()

            viewModel.addToAdjustment(food(BREAD), amount = 60.0, countedAs = CountedAs.GRAMS)
            advanceUntilIdle()

            assertThat(viewModel.adjusted()!!.items.map { it.name })
                .containsExactly("Cucumber", "Olive oil", "Bread").inOrder()
        }

    /** Picking a food asks how much, with nothing filled in, counted the way the food knows best. */
    @Test
    fun `picking a food to add asks how much, and nothing is filled in`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        advanceUntilIdle()

        viewModel.pickToAdd(BREAD)
        advanceUntilIdle()

        val adding = viewModel.state.value.adjusting!!.adding!!
        assertThat(adding.food.name).isEqualTo("Bread")
        assertThat(adding.countedAs).isEqualTo(CountedAs.GRAMS)
        assertThat(adding.amount).isEmpty()
        assertThat(adding.canLog).isFalse()

        // Nothing goes in until an amount makes sense.
        viewModel.putItIn()
        advanceUntilIdle()
        assertThat(viewModel.state.value.adjusting!!.rows).hasSize(2)
        assertThat(viewModel.state.value.adjusting!!.adding).isNotNull()
    }

    /** A food known only by the unit is counted, and goes in in its own unit. */
    @Test
    fun `a food counted in units goes in in its own unit`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        advanceUntilIdle()

        viewModel.pickToAdd(ROLL)
        viewModel.setAddedAmount("2")
        viewModel.putItIn()
        advanceUntilIdle()

        val roll = viewModel.adjusted()!!.items.last()
        assertThat(roll.portionUnit).isEqualTo("roll")
        assertThat(roll.kcal).isEqualTo(300)
    }

    /** "Not this one" goes back to the search with the words still typed; "Not now" leaves it. */
    @Test
    fun `backing out of the step changes nothing about the adjustment`() = runTest(dispatcher) {
        val viewModel = watched(pantry(), FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        viewModel.searchToAdd("bre")
        advanceUntilIdle()
        viewModel.pickToAdd(BREAD)
        viewModel.setAddedAmount("60")

        viewModel.dropPicked()
        advanceUntilIdle()
        assertThat(viewModel.state.value.adjusting!!.adding).isNull()
        assertThat(viewModel.state.value.adjusting!!.finding).isEqualTo("bre")

        viewModel.stopAddingToMeal()
        advanceUntilIdle()
        val adjusting = viewModel.state.value.adjusting!!
        assertThat(adjusting.finding).isNull()
        assertThat(adjusting.rows.map { it.food.name }).containsExactly("Cucumber", "Olive oil")
        assertThat(viewModel.adjusted()!!.adjusted).isFalse()
    }

    /** As on the foods tab: a portion given in the editor reaches the question already open. */
    @Test
    fun `a portion given in the editor reaches the food being added`() = runTest(dispatcher) {
        val foods = pantry()
        val viewModel = watched(foods, FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        advanceUntilIdle()
        viewModel.pickToAdd(TOMATO)
        advanceUntilIdle()
        assertThat(viewModel.state.value.adjusting!!.adding!!.cannotCount).isNotNull()

        foods.correct(TOMATO, FoodFacts(per100g = aPer100g(18.0), perUnit = aPerUnit("tomato", 22.0)))
        advanceUntilIdle()
        viewModel.countAddedAs(CountedAs.UNITS)
        viewModel.setAddedAmount("1")
        viewModel.putItIn()
        advanceUntilIdle()

        val tomato = viewModel.adjusted()!!.items.last()
        assertThat(tomato.portionUnit).isEqualTo("tomato")
        assertThat(tomato.kcal).isEqualTo(22)
    }

    /** A food deleted in the editor while it was being added is not put in. */
    @Test
    fun `a food deleted in the editor closes the question about adding it`() = runTest(dispatcher) {
        val foods = pantry()
        val viewModel = watched(foods, FakeSavedMealRepository(listOf(salad())))
        viewModel.beginAdjusting(0)
        viewModel.beginAddingToMeal()
        advanceUntilIdle()
        viewModel.pickToAdd(BREAD)
        viewModel.setAddedAmount("60")
        advanceUntilIdle()

        foods.delete(BREAD)
        advanceUntilIdle()
        viewModel.putItIn()
        advanceUntilIdle()

        val adjusting = viewModel.state.value.adjusting!!
        assertThat(adjusting.adding).isNull()
        assertThat(adjusting.rows.map { it.food.name }).containsExactly("Cucumber", "Olive oil")
    }

    @Test
    fun `dropping something leaves the rest`() = runTest(dispatcher) {
        val viewModel = watched(savedMeals = FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        advanceUntilIdle()
        viewModel.removeComponent(11)
        advanceUntilIdle()

        assertThat(viewModel.state.value.adjusting!!.rows.map { it.food.name })
            .containsExactly("Cucumber")
    }

    @Test
    fun `dropping the last thing closes the adjuster rather than logging an empty meal`() =
        runTest(dispatcher) {
            val viewModel = watched(savedMeals = FakeSavedMealRepository(listOf(salad())))

            viewModel.beginAdjusting(0)
            advanceUntilIdle()
            viewModel.removeComponent(10)
            viewModel.removeComponent(11)
            advanceUntilIdle()

            assertThat(viewModel.state.value.adjusting).isNull()
            assertThat(viewModel.adjusted()).isNull()
        }

    /** Logged as the meal says: not adjusted, and carrying which meal it was. */
    @Test
    fun `a meal logged as it was built says it was not adjusted`() = runTest(dispatcher) {
        val viewModel = watched(savedMeals = FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        advanceUntilIdle()
        val logged = viewModel.adjusted()!!

        assertThat(logged.adjusted).isFalse()
        assertThat(logged.savedMealId).isEqualTo(1)
        assertThat(logged.items.map { it.name }).containsExactly("Cucumber", "Olive oil").inOrder()
    }

    /**
     * Whether it was adjusted is answered against the definition AT THIS MOMENT and written once.
     * It must never be counted or turned into an offer to change the meal: nothing learns.
     */
    @Test
    fun `a meal logged differently says it was adjusted`() = runTest(dispatcher) {
        val viewModel = watched(savedMeals = FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        advanceUntilIdle()
        viewModel.setComponentAmount(10, 200.0)
        advanceUntilIdle()

        assertThat(viewModel.adjusted()!!.adjusted).isTrue()
    }

    @Test
    fun `the numbers are computed from the foods, once, on the way onto the record`() =
        runTest(dispatcher) {
            val viewModel = watched(savedMeals = FakeSavedMealRepository(listOf(salad())))

            viewModel.beginAdjusting(0)
            advanceUntilIdle()
            val logged = viewModel.adjusted()!!

            // 100 g of cucumber at 16 kcal per 100 g, and one spoon of oil at 119.
            assertThat(logged.items.sumOf { it.kcal }).isEqualTo(135)
            assertThat(logged.items.first().foodId).isEqualTo(1)
        }

    @Test
    fun `leaving it alone logs nothing at all`() = runTest(dispatcher) {
        val viewModel = watched(savedMeals = FakeSavedMealRepository(listOf(salad())))

        viewModel.beginAdjusting(0)
        advanceUntilIdle()
        viewModel.cancelAdjusting()
        advanceUntilIdle()

        assertThat(viewModel.adjusted()).isNull()
        assertThat(viewModel.state.value.adjusting).isNull()
    }

    // --- Picking a food, and saying how much of it ----------------------------------------------

    /**
     * The foods list is the saved foods now. Picking one asks how much rather than logging a past
     * amount, because a food is one entry and has no last time of its own.
     */
    @Test
    fun `picking a food opens the question of how much`() = runTest(dispatcher) {
        val viewModel = watched(foods = listOf(aFood(name = "Yoghurt")))

        viewModel.beginChoosing(0)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing?.food?.name).isEqualTo("Yoghurt")
    }

    /** Grams when it knows them: the more exact of the two, and what a kitchen scale answers. */
    @Test
    fun `a food that can be weighed opens on weighing`() = runTest(dispatcher) {
        val viewModel = watched(foods = listOf(aFood(facts = FoodFacts(per100g = aPer100g()))))

        viewModel.beginChoosing(0)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing?.countedAs).isEqualTo(CountedAs.GRAMS)
    }

    @Test
    fun `a food that can only be counted opens on counting`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(aFood(facts = FoodFacts(perUnit = aPerUnit("bar", 190.0)))),
        )

        viewModel.beginChoosing(0)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing?.countedAs).isEqualTo(CountedAs.UNITS)
    }

    /**
     * "Give this a portion" leaves for the food's editor with this question still open, and he comes
     * back to it. The count has to be on offer when he does: a question still holding the food as
     * it was when he picked it would keep the option he just made possible switched off.
     */
    @Test
    fun `a portion given in the editor reaches the question already open`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Rice", facts = FoodFacts(per100g = aPer100g()))))
        val viewModel = watched(foods)
        viewModel.beginChoosing(0)
        advanceUntilIdle()
        assertThat(viewModel.state.value.choosing?.cannotCount).isNotNull()

        foods.correct(1, FoodFacts(per100g = aPer100g(), perUnit = aPerUnit("bowl", 200.0)))
        advanceUntilIdle()

        val choosing = viewModel.state.value.choosing
        assertThat(choosing?.cannotCount).isNull()
        assertThat(choosing?.food?.facts?.perUnit?.unitName).isEqualTo("bowl")
        // Still on the way of counting it opened on: nothing he chose moves under him.
        assertThat(choosing?.countedAs).isEqualTo(CountedAs.GRAMS)

        // And the portion is what gets logged, not only what gets drawn: choosing the count, typing
        // 1 and pressing Log it must log one bowl, not return nothing and log nothing at all.
        viewModel.countAs(CountedAs.UNITS)
        viewModel.setAmount("1")
        advanceUntilIdle()
        val logged = viewModel.chosen()
        assertThat(logged).isNotNull()
        assertThat(logged!!.portionUnit).isEqualTo("bowl")
        assertThat(logged.kcal).isEqualTo(200)
    }

    /** Corrected figures per 100 g are the ones logged, not the ones the question was opened on. */
    @Test
    fun `figures corrected in the editor are the ones logged from the question already open`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(
                listOf(aFood(name = "Rice", facts = FoodFacts(per100g = aPer100g(kcal = 100.0)))),
            )
            val viewModel = watched(foods)
            viewModel.beginChoosing(0)
            advanceUntilIdle()

            foods.correct(1, FoodFacts(per100g = aPer100g(kcal = 150.0)))
            advanceUntilIdle()
            viewModel.setAmount("100")
            advanceUntilIdle()

            assertThat(viewModel.state.value.choosing?.preview?.kcal).isEqualTo(150)
            assertThat(viewModel.chosen()!!.kcal).isEqualTo(150)
        }

    /**
     * A food gone from the list while its question was open — deleted, hidden or joined into another
     * in the editor — closes the question. Logging it would write a row about a food that is gone.
     */
    @Test
    fun `a food deleted in the editor closes the question open about it`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Rice"), aFood(name = "Oats")))
        val viewModel = watched(foods)
        viewModel.beginChoosing(viewModel.state.value.foods.indexOfFirst { it.name == "Rice" })
        viewModel.setAmount("100")
        advanceUntilIdle()

        foods.delete(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing).isNull()
        assertThat(viewModel.chosen()).isNull()
    }

    @Test
    fun `a food hidden in the editor closes the question open about it`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Rice")))
        val viewModel = watched(foods)
        viewModel.beginChoosing(0)
        viewModel.setAmount("100")
        advanceUntilIdle()

        foods.hide(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing).isNull()
        assertThat(viewModel.chosen()).isNull()
    }

    @Test
    fun `a food joined into another in the editor closes the question open about it`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Rice"), aFood(name = "White rice")))
            val viewModel = watched(foods)
            viewModel.beginChoosing(viewModel.state.value.foods.indexOfFirst { it.name == "Rice" })
            viewModel.setAmount("100")
            advanceUntilIdle()

            foods.merge(winnerId = 2, loserId = 1)
            advanceUntilIdle()

            assertThat(viewModel.state.value.choosing).isNull()
            assertThat(viewModel.chosen()).isNull()
        }

    /**
     * Renamed in the editor so the words still in the search no longer find it: the food is still
     * his, so the question stays open — but it is no longer at any row of the list, and must not
     * claim a row's place that now belongs to a different food.
     */
    @Test
    fun `a food renamed out of the search keeps its question, at no row of the list`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(
                listOf(aFood(name = "Rice", updatedAtMillis = 2), aFood(name = "Rice cake", updatedAtMillis = 1)),
            )
            val viewModel = watched(foods)
            viewModel.search("rice")
            advanceUntilIdle()
            viewModel.beginChoosing(viewModel.state.value.foods.indexOfFirst { it.name == "Rice" })
            viewModel.setAmount("100")
            advanceUntilIdle()

            foods.rename(1, "Porridge")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.foods.map { it.name }).containsExactly("Rice cake")
            assertThat(state.choosing).isNotNull()
            assertThat(state.choosing!!.food.name).isEqualTo("Porridge")
            assertThat(state.choosing!!.index).isEqualTo(-1)
            assertThat(viewModel.chosen()!!.foodId).isEqualTo(1)
        }

    @Test
    fun `nothing is logged until an amount makes sense`() = runTest(dispatcher) {
        val viewModel = watched(foods = listOf(aFood()))
        viewModel.beginChoosing(0)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing?.canLog).isFalse()
        viewModel.setAmount("0")
        advanceUntilIdle()
        assertThat(viewModel.state.value.choosing?.canLog).isFalse()
        viewModel.setAmount("200")
        advanceUntilIdle()
        assertThat(viewModel.state.value.choosing?.canLog).isTrue()
    }

    /**
     * The numbers are computed once, here, and frozen onto the row. Nothing recomputes them
     * afterwards, which is why correcting the food later cannot change what this day was worth.
     */
    @Test
    fun `what is logged carries the numbers, the amount and the food it is`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(aFood(name = "Yoghurt", facts = FoodFacts(per100g = aPer100g(kcal = 72.0)))),
        )
        viewModel.beginChoosing(0)
        viewModel.setAmount("200")

        val logged = viewModel.chosen()!!

        assertThat(logged.name).isEqualTo("Yoghurt")
        assertThat(logged.kcal).isEqualTo(144)
        assertThat(logged.portionAmount).isEqualTo(200.0)
        assertThat(logged.portionUnit).isEqualTo("g")
        assertThat(logged.foodId).isNotNull()
    }

    @Test
    fun `counting a food logs it in its own unit`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(
                aFood(name = "Protein bar", facts = FoodFacts(perUnit = aPerUnit("bar", 190.0))),
            ),
        )
        viewModel.beginChoosing(0)
        viewModel.setAmount("2")

        val logged = viewModel.chosen()!!

        assertThat(logged.kcal).isEqualTo(380)
        assertThat(logged.portionUnit).isEqualTo("bar")
        assertThat(logged.portion).isEqualTo("2 bar")
    }

    /** Switching from grams to bars having typed 2 almost always means two bars. */
    @Test
    fun `switching the way of counting keeps the amount typed`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(
                aFood(
                    facts = FoodFacts(per100g = aPer100g(), perUnit = aPerUnit("bar", 190.0)),
                ),
            ),
        )
        viewModel.beginChoosing(0)
        viewModel.setAmount("2")

        viewModel.countAs(CountedAs.UNITS)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing?.amount).isEqualTo("2")
    }

    /** Searching finds every name a food answers to, which is what merging two duplicates buys. */
    @Test
    fun `a merged food is found under either of its names`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(aFood(name = "Yoghurt").copy(alsoKnownAs = listOf("יוגורט"))),
        )

        viewModel.search("יוגורט")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Yoghurt")
    }

    /**
     * The brand is printed on the row, so typing it has to find the row (D41, issue #14) — and a
     * food found only by its brand is still a food he has, so describing it afresh is not offered:
     * that is the duplicate the search exists to prevent (D28).
     */
    @Test
    fun `a food is found by its brand`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(aFood(name = "Cucumber"), aFood(name = "Milk").copy(brand = "Dairyco")),
        )

        viewModel.search("dairyco")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Milk")
        assertThat(viewModel.state.value.searchedAndFoundNothing).isFalse()
        assertThat(viewModel.state.value.nothingMatchedEither).isFalse()
    }

    /**
     * The issue's harm: `Dairyco milk` found nothing, so describing it afresh was offered — the
     * duplicate the search exists to prevent (D28). Brand and name together find it (D41, #33).
     */
    @Test
    fun `brand and name typed together find the food, and describing it afresh is not offered`() =
        runTest(dispatcher) {
            val viewModel = watched(
                foods = listOf(aFood(name = "Cucumber"), aFood(name = "Milk").copy(brand = "Dairyco")),
            )

            viewModel.search("Dairyco milk")
            advanceUntilIdle()

            assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Milk")
            assertThat(viewModel.state.value.searchedAndFoundNothing).isFalse()
            assertThat(viewModel.state.value.nothingMatchedEither).isFalse()
        }

    /**
     * Every word has to be found, not one: the brand alone must not swallow a food he does not have,
     * or describing it would stop being offered when it is the only way to log it.
     */
    @Test
    fun `a brand with a food he does not have still offers to describe it`() = runTest(dispatcher) {
        val viewModel = watched(
            foods = listOf(aFood(name = "Cucumber"), aFood(name = "Milk").copy(brand = "Dairyco")),
        )

        viewModel.search("Dairyco bread")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods).isEmpty()
        assertThat(viewModel.state.value.searchedAndFoundNothing).isTrue()
        assertThat(viewModel.state.value.nothingMatchedEither).isTrue()
    }

    /**
     * "Infinity" as the amount logged a 2,147,483,647-kcal row (D42, issue #32). Past its ceiling,
     * nothing is chosen, and the panel stays open with what he typed so the refusal can be read.
     */
    @Test
    fun `an unbelievable amount chooses nothing, and the panel stays open`() = runTest(dispatcher) {
        val viewModel = watched(foods = listOf(aFood(name = "Yoghurt")))
        viewModel.beginChoosing(0)
        viewModel.setAmount("Infinity")
        advanceUntilIdle()

        assertThat(viewModel.chosen()).isNull()
        advanceUntilIdle()
        val choosing = viewModel.state.value.choosing
        assertThat(choosing).isNotNull()
        assertThat(choosing!!.amount).isEqualTo("Infinity")
        assertThat(choosing.amountTooMuch).isTrue()
        assertThat(choosing.canLog).isFalse()
    }

    /**
     * The state only flows while something is collecting it, and `beginAdjusting` reads the meals
     * out of it — so a test that never collects would be adjusting an empty list.
     */
    private fun TestScope.watched(
        foods: List<Food> = emptyList(),
        savedMeals: FakeSavedMealRepository = FakeSavedMealRepository(),
    ): RepeatViewModel = watched(FakeFoodRepository(foods), savedMeals)

    private fun TestScope.watched(
        foods: FakeFoodRepository,
        savedMeals: FakeSavedMealRepository = FakeSavedMealRepository(),
    ): RepeatViewModel {
        val viewModel = RepeatViewModel(
            foods = foods,
            savedMeals = savedMeals,
        )
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()
        return viewModel
    }

    /** A salad he built: two foods, counted the way each of them knows. */
    /**
     * His foods, with the salad's two first so their ids match the salad's parts: the fake numbers
     * foods 1, 2, 3… in the order given. Every figure is invented and chosen to keep sums round.
     */
    private fun pantry() = FakeFoodRepository(
        listOf(
            aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))),
            aFood("Olive oil", FoodFacts(perUnit = aPerUnit("spoon", 119.0))),
            aFood("Bread", FoodFacts(per100g = aPer100g(250.0))),
            aFood("Tomato", FoodFacts(per100g = aPer100g(18.0))),
            aFood("Rice", FoodFacts(per100g = aPer100g(130.0))),
            aFood("Bread roll", FoodFacts(perUnit = aPerUnit("roll", 150.0))),
        ),
    )

    /** A food from [pantry] by its id, for the tests that add directly. */
    private fun food(id: Long): Food = pantry().current.single { it.id == id }

    private fun salad(): SavedMeal {
        val cucumber = aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)
        val oil = aFood("Olive oil", FoodFacts(perUnit = aPerUnit("spoon", 119.0))).copy(id = 2)
        return SavedMeal(
            id = 1,
            name = "Vegetable salad",
            components = listOf(
                MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
                MealComponent(11, oil, 1.0, CountedAs.UNITS, position = 1),
            ),
        )
    }

    private companion object {
        const val BREAD = 3L
        const val TOMATO = 4L
        const val RICE = 5L
        const val ROLL = 6L
    }
}
