package com.metaself.app.ui.screen.manager

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The list of meals the owner has built, on the manager's second tab.
 *
 * Pure but for the view model's own scope, so JUnit 5 — `org.junit.jupiter.api.Test`, never
 * `org.junit.Test`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The order is the repository's, and re-sorting here would make two screens disagree about what
     * "first" means. The pair below is deliberately not in alphabetical order, so a sort slipped in
     * later fails this rather than passing quietly.
     */
    @Test
    fun `it lists the meals he has built, newest name first is not assumed`() = runTest {
        val meals = FakeSavedMeals(listOf(salad, shakshuka))
        val model = MealsViewModel(meals, ProblemLog.NONE)
        advanceUntilIdle()
        assertThat(model.state.value.meals.map { it.name })
            .containsExactly("Vegetable salad", "Shakshuka breakfast").inOrder()
    }

    @Test
    fun `with nothing built it says so rather than showing an empty space`() = runTest {
        val model = MealsViewModel(FakeSavedMeals(emptyList()), ProblemLog.NONE)
        advanceUntilIdle()
        assertThat(model.state.value.nothingBuiltYet).isTrue()
    }

    /** Having built one, there is nothing to say about an empty list. */
    @Test
    fun `with something built it is a list and not a sentence`() = runTest {
        val model = MealsViewModel(FakeSavedMeals(listOf(salad)), ProblemLog.NONE)
        advanceUntilIdle()
        assertThat(model.state.value.nothingBuiltYet).isFalse()
    }

    /**
     * Hiding a meal is not offered anywhere in the app yet, so the manager shows what the rest of
     * the app is offered and nothing more. This pins that the hidden one is not simply listed.
     */
    @Test
    fun `a meal put out of sight is not on the list`() = runTest {
        val meals = FakeSavedMeals(listOf(salad, shakshuka.copy(hidden = true)))
        val model = MealsViewModel(meals, ProblemLog.NONE)
        advanceUntilIdle()
        assertThat(model.state.value.meals.map { it.name }).containsExactly("Vegetable salad")
    }

    /**
     * A list that cannot be read says it could not be opened, is written down, and goes no further —
     * an exception that escaped would fail this on its own, because `runTest` reports it. It does not
     * claim he has built nothing: an empty list after a failed read says nothing about that.
     */
    @Test
    fun `a list that cannot be read says so rather than that none is built`() = runTest {
        val broken = object : SavedMealRepository by FakeSavedMeals(emptyList()) {
            override fun observeOffered(): Flow<List<SavedMeal>> =
                flow { throw IllegalStateException("disk unreadable") }
        }
        val problems = RecordingProblemLog()
        val model = MealsViewModel(broken, problems)
        advanceUntilIdle()

        assertThat(model.state.value.failed).isEqualTo(ActionRefused.COULD_NOT_OPEN)
        assertThat(model.state.value.nothingBuiltYet).isFalse()
        assertThat(problems.recorded.single().kind).isEqualTo("refused")

        model.dismissFailure()
        assertThat(model.state.value.failed).isNull()
    }

    private val cucumber = aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)
    private val oil = aFood("Olive oil", FoodFacts(perUnit = aPerUnit("spoon", 119.0))).copy(id = 2)
    private val egg = aFood("Egg", FoodFacts(perUnit = aPerUnit("egg", 78.0))).copy(id = 3)

    private val salad = SavedMeal(
        id = 1,
        name = "Vegetable salad",
        components = listOf(
            MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
            MealComponent(11, oil, 1.0, CountedAs.UNITS, position = 1),
        ),
    )

    private val shakshuka = SavedMeal(
        id = 2,
        name = "Shakshuka breakfast",
        components = listOf(MealComponent(12, egg, 2.0, CountedAs.UNITS, position = 0)),
    )
}

/**
 * The saved meals, in memory, for this test.
 *
 * A name for the shared fake rather than a second copy of it: `FakeSavedMealRepository` is already
 * faithful about everything a list of meals can ask of it — a name is taken or it is not, the same
 * food goes in once, and a hidden meal is not offered — and a second fake would be a second thing
 * to keep honest.
 */
private fun FakeSavedMeals(meals: List<SavedMeal>): FakeSavedMealRepository =
    FakeSavedMealRepository(meals)
