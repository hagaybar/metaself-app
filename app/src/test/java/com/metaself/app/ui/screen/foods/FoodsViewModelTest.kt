package com.metaself.app.ui.screen.foods

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.food.EditRefused
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Maintaining the food list.
 *
 * The thing under test is the honesty of the screen's answers: a refusal has to name what stood in
 * the way, a correction must not half-apply, and a merge has to leave the surviving food answering
 * to both names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every food is listed, hidden ones included when asked for`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini").copy(hidden = true))

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Yoghurt")

        viewModel.showHidden(true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name })
            .containsExactly("Yoghurt", "Tahini")
    }

    /** Derived, so the number falls as he fixes them rather than going stale. */
    @Test
    fun `the foods that only know a portion are counted and can be worked through`() =
        runTest(dispatcher) {
            val viewModel = watched(
                aFood(name = "Stew", facts = FoodFacts(perUnit = aPerUnit(FoodFacts.PORTION, 400.0))),
                aFood(name = "Yoghurt"),
            )

            assertThat(viewModel.state.value.onlyAPortionCount).isEqualTo(1)

            viewModel.showOnlyPortions(true)
            advanceUntilIdle()

            assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Stew")
        }

    @Test
    fun `searching finds every name a food answers to`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt").copy(alsoKnownAs = listOf("יוגורט")))

        viewModel.search("יוגורט")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Yoghurt")
    }

    /** The brand is printed on the row, so typing it has to find the row (D41, issue #14). */
    @Test
    fun `searching a brand finds the food it is printed on`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Cucumber"), aFood(name = "Milk").copy(brand = "Dairyco"))

        viewModel.search("Dairyco")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Milk")
        assertThat(viewModel.state.value.searchedAndFoundNothing).isFalse()
    }

    /** He types what the row prints, brand and name together, in either order (D41, issue #33). */
    @Test
    fun `brand and name typed together find the food`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Cucumber"), aFood(name = "Milk").copy(brand = "Dairyco"))

        viewModel.search("DAIRYCO  Milk")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Milk")
        assertThat(viewModel.state.value.searchedAndFoundNothing).isFalse()

        viewModel.search("milk dairyco")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Milk")
    }

    // --- Correcting ---------------------------------------------------------------------------------

    @Test
    fun `correcting a food changes what it knows from now on`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.edit(1)
        advanceUntilIdle()
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(kcalPer100g = "90"))
        viewModel.save()
        advanceUntilIdle()

        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(90.0)
        assertThat(viewModel.state.value.editing).isNull()
    }

    @Test
    fun `renaming a food keeps everything it knows`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "yogurt", facts = FoodFacts(per100g = aPer100g(72.0)))))
        val viewModel = watched(foods)

        viewModel.edit(1)
        advanceUntilIdle()
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Greek yoghurt"))
        viewModel.save()
        advanceUntilIdle()

        assertThat(foods.current.single().name).isEqualTo("Greek yoghurt")
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(72.0)
    }

    /** A form that complains before he has done anything is a form shouting at nobody. */
    @Test
    fun `an opened food does not complain until he tries to save`() = runTest(dispatcher) {
        val viewModel = watched(aFood())

        viewModel.edit(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.editing!!.showErrors).isFalse()
    }

    @Test
    fun `saving a food that would know nothing shows the reason and changes nothing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            val viewModel = watched(foods)

            viewModel.edit(1)
            advanceUntilIdle()
            viewModel.setForm(FoodForm(name = "Yoghurt"))
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.state.value.editing!!.showErrors).isTrue()
            assertThat(foods.current.single().facts.per100g).isNotNull()
        }

    /**
     * Renaming onto a name another food holds must not half-apply: the numbers must not be changed
     * with the name left alone.
     */
    @Test
    fun `a refused rename leaves the numbers alone too`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(
            listOf(
                aFood(name = "Yoghurt", facts = FoodFacts(per100g = aPer100g(72.0))),
                aFood(name = "Hummus"),
            ),
        )
        val viewModel = watched(foods)

        viewModel.edit(1)
        advanceUntilIdle()
        viewModel.setForm(
            viewModel.state.value.editing!!.form.copy(name = "Hummus", kcalPer100g = "99"),
        )
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refusal).isNotNull()
        val yoghurt = foods.current.first { it.id == 1L }
        assertThat(yoghurt.name).isEqualTo("Yoghurt")
        assertThat(yoghurt.facts.per100g!!.nutrients.kcal).isEqualTo(72.0)
    }

    /** A refusal names what stands in the way, so it is a next step rather than a dead end. */
    @Test
    fun `a refusal names the food already holding the name`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Hummus"))

        viewModel.edit(1)
        advanceUntilIdle()
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Hummus"))
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refusal).contains("Hummus")
    }

    // --- Hiding and deleting --------------------------------------------------------------------------

    @Test
    fun `hiding takes a food off the lists without losing it`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.hide(1)
        advanceUntilIdle()

        assertThat(foods.current.single().hidden).isTrue()
        assertThat(viewModel.state.value.foods).isEmpty()
    }

    // --- Deleting asks first (D36, issue #16) ---------------------------------------------------

    /**
     * Neither a food nor a meal can be put back by the owner, so the question comes before the act —
     * and it names the STORED food, because that is what goes, not whatever is half-typed in the
     * name field.
     */
    @Test
    fun `asking to delete a food names it and deletes nothing`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.edit(1)
        viewModel.askToDelete(1)
        advanceUntilIdle()

        val deleting = viewModel.state.value.deleting
        assertThat(deleting).isInstanceOf(Deleting.Asking::class.java)
        assertThat(deleting!!.food.name).isEqualTo("Yoghurt")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    /** Keep it changes nothing: the question goes, and what he had typed stays his. */
    @Test
    fun `keeping it leaves the food and the open editor as they were`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.edit(1)
        advanceUntilIdle()
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Yog"))
        viewModel.askToDelete(1)
        advanceUntilIdle()
        // The question was up, so what follows is Keep it answering it, not a no-op on nothing.
        assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Asking::class.java)

        viewModel.cancelDeleting()
        advanceUntilIdle()

        assertThat(viewModel.state.value.deleting).isNull()
        assertThat(viewModel.state.value.editing?.form?.name).isEqualTo("Yog")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    /**
     * The answer that does it. What a deleted food leaves behind — days keeping their numbers and
     * losing its name — is why hiding is usually the better answer, which is why this is asked.
     */
    @Test
    fun `deleting it once asked removes the food and closes the editor`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.edit(1)
        viewModel.askToDelete(1)
        advanceUntilIdle()
        viewModel.confirmDeleting()
        advanceUntilIdle()

        assertThat(foods.current).isEmpty()
        assertThat(viewModel.state.value.editing).isNull()
        assertThat(viewModel.state.value.deleting).isNull()
    }

    /**
     * Asking "Delete it?" and then refusing would be a question with no real answer. So a food a
     * saved meal uses is refused before anything is asked — and in the editor, where Delete was
     * pressed, not in the slot at the top of the list, which is off screen from the foot of an open
     * editor.
     */
    @Test
    fun `a food a saved meal uses is refused before anything is asked`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            .usedBySavedMeals(1, "Vegetable salad")
        val viewModel = watched(foods)

        viewModel.edit(1)
        viewModel.askToDelete(1)
        advanceUntilIdle()

        val deleting = viewModel.state.value.deleting
        assertThat(deleting).isInstanceOf(Deleting.Refused::class.java)
        assertThat((deleting as Deleting.Refused).sentence).contains("Vegetable salad")
        assertThat(viewModel.state.value.refusal).isNull()
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
        assertThat(viewModel.state.value.editing?.foodId).isEqualTo(1L)
    }

    /**
     * The check before asking is for the owner; the check inside the delete is for the data. If a
     * meal starts using the food between the question and the answer, the answer is still a refusal
     * he can see, in the same place as the early one.
     */
    @Test
    fun `a refusal that arrives only at the delete is still shown`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.edit(1)
        viewModel.askToDelete(1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Asking::class.java)

        foods.usedBySavedMeals(1, "Vegetable salad")
        viewModel.confirmDeleting()
        advanceUntilIdle()

        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
        val deleting = viewModel.state.value.deleting
        assertThat(deleting).isInstanceOf(Deleting.Refused::class.java)
        assertThat((deleting as Deleting.Refused).sentence).contains("Vegetable salad")
        assertThat(viewModel.state.value.refusal).isNull()
        assertThat(viewModel.state.value.editing?.foodId).isEqualTo(1L)
    }

    /**
     * The question is not modal, so every way off the food has to let go of it — or a Delete pressed
     * later, on something else, would answer a question he had walked away from.
     */
    @Test
    fun `moving off the food lets go of the question`() = runTest(dispatcher) {
        val ways: List<Pair<String, (FoodsViewModel) -> Unit>> = listOf(
            "opening another food" to { it.edit(2) },
            "starting a join" to { it.beginMerging(1) },
            "searching" to { it.search("x") },
        )
        ways.forEach { (way, moveOff) ->
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
            val viewModel = watched(foods)
            viewModel.edit(1)
            viewModel.askToDelete(1)
            advanceUntilIdle()
            assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Asking::class.java)

            moveOff(viewModel)
            advanceUntilIdle()
            assertWithMessage(way).that(viewModel.state.value.deleting).isNull()

            viewModel.confirmDeleting()
            advanceUntilIdle()
            assertWithMessage(way).that(foods.current).hasSize(2)
        }

        // A refusal is let go of the same way.
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
            .usedBySavedMeals(1, "Vegetable salad")
        val viewModel = watched(foods)
        viewModel.edit(1)
        viewModel.askToDelete(1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Refused::class.java)

        viewModel.edit(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.deleting).isNull()
    }

    /**
     * A refusal to delete is drawn directly above Save. Once he has gone back to typing, or pressed
     * Save, it has to go: left standing, it reads as the reason his Save was refused — "Vegetable
     * salad uses this" above a rename that was refused for a quite different reason.
     */
    @Test
    fun `typing or saving lets go of a refusal to delete`() = runTest(dispatcher) {
        // (a) Typing.
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
                .usedBySavedMeals(1, "Vegetable salad")
            val viewModel = watched(foods)
            viewModel.edit(1)
            viewModel.askToDelete(1)
            advanceUntilIdle()
            assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Refused::class.java)

            viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Yog"))
            advanceUntilIdle()

            assertThat(viewModel.state.value.deleting).isNull()
            assertThat(viewModel.state.value.editing?.form?.name).isEqualTo("Yog")
        }
        // (b) A Save that is itself refused: its own refusal shows, and the delete's does not.
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
                .usedBySavedMeals(1, "Vegetable salad")
            val viewModel = watched(foods)
            viewModel.edit(1)
            advanceUntilIdle()
            // Typed before Delete, so it is the Save alone that has to let go of the refusal.
            viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Tahini"))
            viewModel.askToDelete(1)
            advanceUntilIdle()
            assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Refused::class.java)

            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.state.value.deleting).isNull()
            assertThat(viewModel.state.value.refusal).isNotNull()
            assertThat(foods.current.map { it.name }).containsExactly("Yoghurt", "Tahini")
        }
    }

    /**
     * Asking resolves after two reads. If he has moved on in the meantime, the answer belongs to a
     * food he is no longer looking at, and landing it would bring a question — or a refusal, or a
     * closed editor — back onto a screen he has left. Each case moves off BEFORE the dispatcher runs,
     * which is the timing the fake's instant answers otherwise hide.
     */
    @Test
    fun `an answer that arrives after he has moved on is dropped`() = runTest(dispatcher) {
        // (a) The question itself.
        run {
            val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini"))
            viewModel.edit(1)
            viewModel.askToDelete(1)
            viewModel.edit(2)
            advanceUntilIdle()

            assertThat(viewModel.state.value.deleting).isNull()
            assertThat(viewModel.state.value.editing?.foodId).isEqualTo(2L)
        }
        // (b) A refusal, which must not land in the top-of-list slot either.
        run {
            val viewModel = watched(
                FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
                    .usedBySavedMeals(1, "Vegetable salad"),
            )
            viewModel.edit(1)
            viewModel.askToDelete(1)
            viewModel.edit(2)
            advanceUntilIdle()

            assertThat(viewModel.state.value.deleting).isNull()
            assertThat(viewModel.state.value.refusal).isNull()
        }
        // (c) A confirmed delete: the food goes — he pressed Delete — but another food's open
        // editor is not closed by it.
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
            val viewModel = watched(foods)
            viewModel.edit(1)
            viewModel.askToDelete(1)
            advanceUntilIdle()
            viewModel.confirmDeleting()
            viewModel.edit(2)
            advanceUntilIdle()

            assertThat(foods.current.map { it.name }).containsExactly("Tahini")
            assertThat(viewModel.state.value.editing?.foodId).isEqualTo(2L)
        }
        // (d) A duplicate picked just before he backed out of the join.
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)
            viewModel.beginMerging(1)
            viewModel.mergeInto(2)
            viewModel.cancelMerging()
            advanceUntilIdle()

            assertThat(viewModel.state.value.merging).isNull()
            assertThat(foods.current).hasSize(2)
        }
        // (e) ...or opened a food, which also ends the join. (Searching does not: see below.)
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)
            viewModel.beginMerging(1)
            viewModel.mergeInto(2)
            viewModel.edit(2)
            advanceUntilIdle()

            assertThat(viewModel.state.value.merging).isNull()
            assertThat(foods.current).hasSize(2)
        }
    }

    // --- Merging ----------------------------------------------------------------------------------------

    /**
     * Picking the duplicate from a food's own screen no longer joins: it settles the pair and asks,
     * exactly as choosing mode does — the same irreversible act, so the same question (D36).
     */
    @Test
    fun `picking the duplicate from a food's own screen asks and joins nothing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)

            pickedFromItsOwnScreen(viewModel)

            assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
            assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
            assertThat(foods.current).hasSize(2)
        }

    /**
     * The step that makes merging worth having: the loser's name becomes an alias, so the next log
     * under that name finds the one food rather than making a third.
     */
    @Test
    fun `joining once asked leaves one food answering to both names`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods)

        pickedFromItsOwnScreen(viewModel)
        // Asked, not yet answered: the join below is the answer's doing, not the pick's.
        assertThat(foods.current).hasSize(2)
        viewModel.confirmJoining()
        advanceUntilIdle()

        assertThat(foods.current).hasSize(1)
        assertThat(foods.current.single().name).isEqualTo("Yoghurt")
        assertThat(foods.current.single().alsoKnownAs).contains("יוגורט")
        assertThat(viewModel.state.value.merging).isNull()
    }

    /**
     * A join moves the meals that used the absorbed food onto the survivor, so the survivor is now
     * one a saved meal uses, and deleting it is refused for that meal.
     */
    @Test
    fun `after a join the survivor is refused for the meals the absorbed food was in`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
                .usedBySavedMeals(2, "Vegetable salad")
            val viewModel = watched(foods)

            pickedFromItsOwnScreen(viewModel)
            viewModel.confirmJoining()
            advanceUntilIdle()
            viewModel.edit(1)
            viewModel.askToDelete(1)
            advanceUntilIdle()

            val deleting = viewModel.state.value.deleting
            assertThat(deleting).isInstanceOf(Deleting.Refused::class.java)
            assertThat((deleting as Deleting.Refused).sentence).contains("Vegetable salad")
            assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
        }

    /** Not now ends the join outright, as it does on the choosing route: one question, one behaviour. */
    @Test
    fun `not now after picking joins nothing and ends the join`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods)

        pickedFromItsOwnScreen(viewModel)
        assertThat(viewModel.state.value.merging?.losing).isNotNull()
        viewModel.cancelMerging()
        advanceUntilIdle()

        assertThat(foods.current).hasSize(2)
        assertThat(viewModel.state.value.merging).isNull()
        assertThat(viewModel.state.value.chosen).isEmpty()
    }

    /** As on the choosing route: the refusal names the meal, and the question stays up to act on. */
    @Test
    fun `a refused join from a food's own screen leaves both foods and the question standing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
                .refusesToMerge(EditRefused.MealsHoldingBoth(listOf("Vegetable salad")))
            val viewModel = watched(foods)

            pickedFromItsOwnScreen(viewModel)
            viewModel.confirmJoining()
            advanceUntilIdle()

            assertThat(foods.current).hasSize(2)
            assertThat(viewModel.state.value.refusal).contains("Vegetable salad")
            assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
        }

    /**
     * Looking is not backing out. On a list of any length the search is how he finds the duplicate,
     * and a search that ended the join would make the one flow that cannot be undone cancel itself,
     * silently, the moment it was used — the same reasoning that keeps what is chosen through a
     * search.
     */
    @Test
    fun `searching or filtering while picking the duplicate keeps the join`() = runTest(dispatcher) {
        val ways: List<Pair<String, (FoodsViewModel) -> Unit>> = listOf(
            "searching" to { it.search("יוגורט") },
            "showing hidden foods" to { it.showHidden(true) },
            "narrowing to the foods that only know a portion" to { it.showOnlyPortions(true) },
        )
        ways.forEach { (way, look) ->
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)
            viewModel.beginMerging(1)
            advanceUntilIdle()

            look(viewModel)
            advanceUntilIdle()

            assertWithMessage(way).that(viewModel.state.value.merging?.keeping?.name)
                .isEqualTo("Yoghurt")
            assertWithMessage(way).that(viewModel.state.value.merging?.losing).isNull()
        }
    }

    /** The search that found the duplicate is not the end of the join: picking it still asks. */
    @Test
    fun `the duplicate found by searching can be picked`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(
            listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini"), aFood(name = "יוגורט")),
        )
        val viewModel = watched(foods)
        viewModel.beginMerging(1)
        advanceUntilIdle()

        viewModel.search("יוגורט")
        advanceUntilIdle()
        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("יוגורט")
        viewModel.mergeInto(3)
        advanceUntilIdle()

        assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
        assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
        assertThat(foods.current).hasSize(3)
    }

    /** A hidden duplicate is still a duplicate, and Show hidden is the only way to reach it. */
    @Test
    fun `a hidden duplicate can be picked once hidden foods are shown`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        foods.hide(2)
        val viewModel = watched(foods)
        viewModel.beginMerging(1)
        advanceUntilIdle()

        viewModel.showHidden(true)
        advanceUntilIdle()
        viewModel.mergeInto(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
    }

    @Test
    fun `merging a food into itself does nothing`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = watched(foods)

        viewModel.beginMerging(1)
        advanceUntilIdle()
        viewModel.mergeInto(1)
        advanceUntilIdle()

        assertThat(foods.current).hasSize(1)
        assertThat(viewModel.state.value.merging?.losing).isNull()
    }

    // --- Choosing several foods at once ---------------------------------------------------------

    /**
     * Holding is what starts it, and an ordinary tap still opens a food for editing. Choosing that
     * began on a tap would turn every attempt to fix a wrong number into the start of a meal.
     */
    @Test
    fun `holding a food starts choosing and chooses that one`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini"))

        viewModel.beginChoosing(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.choosing).isTrue()
        assertThat(viewModel.state.value.chosen).containsExactly(1L)
    }

    @Test
    fun `tapping another food while choosing adds it`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini"))

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L)
        assertThat(viewModel.state.value.canMakeAMeal).isTrue()
        assertThat(viewModel.state.value.canJoin).isTrue()
    }

    @Test
    fun `tapping a food that is already chosen takes it out again`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini"))

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        viewModel.toggleChosen(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.chosen).containsExactly(1L)
        assertThat(viewModel.state.value.choosing).isTrue()
    }

    /** Otherwise the list would sit in a mode with nothing in it, and every tap would still add. */
    @Test
    fun `taking the last one out ends choosing`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"))

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.chosen).isEmpty()
        assertThat(viewModel.state.value.choosing).isFalse()
    }

    @Test
    fun `clearing ends choosing outright`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini"))

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        viewModel.clearChoosing()
        advanceUntilIdle()

        assertThat(viewModel.state.value.chosen).isEmpty()
        assertThat(viewModel.state.value.choosing).isFalse()
    }

    /**
     * The whole point of choosing rather than adding one food at a time: he may be collecting the
     * parts of a salad from three different searches. A search that emptied the choice would make
     * that impossible, and would do it silently.
     */
    @Test
    fun `a search that changes the list keeps what is chosen`() = runTest(dispatcher) {
        val viewModel = watched(
            aFood(name = "Yoghurt"),
            aFood(name = "Tahini"),
            aFood(name = "Hummus"),
        )

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        advanceUntilIdle()

        viewModel.search("hummus")
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Hummus")
        assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L)
        assertThat(viewModel.state.value.choosing).isTrue()
    }

    /** Filtering is looking, not un-choosing — the same rule a search follows. */
    @Test
    fun `narrowing to the foods that only know a portion keeps what is chosen`() =
        runTest(dispatcher) {
            val viewModel = watched(
                aFood(name = "Yoghurt"),
                aFood(name = "Stew", facts = FoodFacts(perUnit = aPerUnit(FoodFacts.PORTION, 400.0))),
            )

            viewModel.beginChoosing(1)
            advanceUntilIdle()

            viewModel.showOnlyPortions(true)
            advanceUntilIdle()

            assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Stew")
            assertThat(viewModel.state.value.chosen).containsExactly(1L)
            assertThat(viewModel.state.value.choosing).isTrue()
        }

    /** Hiding one takes it out of the pickers, not out of the salad he is collecting. */
    @Test
    fun `hiding a chosen food leaves it chosen`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
        val viewModel = watched(foods)

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        advanceUntilIdle()

        viewModel.hide(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Tahini")
        assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L)
    }

    // --- Joining the two he chose -----------------------------------------------------------------

    /**
     * A merge cannot be undone, so which food survives is named before anything happens — and both
     * of the two he chose are carried into it, rather than one being remembered and the other
     * becoming "whichever row he taps next".
     */
    @Test
    fun `joining the two chosen names the survivor and the one it absorbs`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods)

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        viewModel.joinChosen()
        advanceUntilIdle()

        assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
        assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
        // Nothing has happened yet: this is the question, not the answer.
        assertThat(foods.current).hasSize(2)
    }

    /**
     * The defect this guards against: choosing outlives a search, so the food he chose first may not
     * be on the list at the moment he joins. Looked up by id, that is nothing; looked up in the list
     * on screen, it was a tap that did nothing and threw the choice away with it.
     */
    @Test
    fun `the two are found even when the list on screen no longer holds them`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)

            viewModel.beginChoosing(1)
            viewModel.toggleChosen(2)
            advanceUntilIdle()

            viewModel.search("nothing matches this")
            advanceUntilIdle()
            assertThat(viewModel.state.value.foods).isEmpty()

            viewModel.joinChosen()
            advanceUntilIdle()

            assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
            assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
            assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L)
        }

    @Test
    fun `the choice is held until the join actually happens`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods)

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        viewModel.joinChosen()
        advanceUntilIdle()

        assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L)

        viewModel.confirmJoining()
        advanceUntilIdle()

        assertThat(foods.current).hasSize(1)
        assertThat(foods.current.single().name).isEqualTo("Yoghurt")
        assertThat(foods.current.single().alsoKnownAs).contains("יוגורט")
        assertThat(viewModel.state.value.chosen).isEmpty()
        assertThat(viewModel.state.value.merging).isNull()
    }

    @Test
    fun `backing out of the join leaves both foods alone and lets the choice go`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)

            viewModel.beginChoosing(1)
            viewModel.toggleChosen(2)
            viewModel.joinChosen()
            advanceUntilIdle()

            viewModel.cancelMerging()
            advanceUntilIdle()

            assertThat(foods.current).hasSize(2)
            assertThat(viewModel.state.value.merging).isNull()
            assertThat(viewModel.state.value.chosen).isEmpty()
        }

    /**
     * A join the database will not allow, which is reachable exactly from this pairwise one: one
     * saved meal holding both of the foods he ticked would end up holding the same food twice.
     *
     * The refusal leaves everything where it was — both foods untouched, the pair still ticked, the
     * question still on screen — so that dealing with the meal it names and saying yes again is the
     * next step, rather than starting the whole choice over.
     */
    @Test
    fun `a refused join leaves both foods, the pair and the question standing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
                .refusesToMerge(EditRefused.MealsHoldingBoth(listOf("Vegetable salad")))
            val viewModel = watched(foods)

            viewModel.beginChoosing(1)
            viewModel.toggleChosen(2)
            viewModel.joinChosen()
            advanceUntilIdle()

            viewModel.confirmJoining()
            advanceUntilIdle()

            assertThat(foods.current.map { it.name })
                .containsExactly("Yoghurt", "יוגורט")
            assertThat(foods.current.flatMap { it.alsoKnownAs }).isEmpty()
            assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
            assertThat(viewModel.state.value.merging?.losing?.name).isEqualTo("יוגורט")
            assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L)
            assertThat(viewModel.state.value.refusal).contains("Vegetable salad")
        }

    /** Merging is pairwise and stays pairwise, so three chosen is not a join at all. */
    @Test
    fun `joining is not offered or done at any size but two`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(
            listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט"), aFood(name = "Tahini")),
        )
        val viewModel = watched(foods)

        viewModel.beginChoosing(1)
        viewModel.toggleChosen(2)
        viewModel.toggleChosen(3)
        viewModel.joinChosen()
        advanceUntilIdle()

        assertThat(viewModel.state.value.merging).isNull()
        assertThat(viewModel.state.value.chosen).containsExactly(1L, 2L, 3L)
        assertThat(foods.current).hasSize(3)
    }

    /**
     * Arriving from "Give this a portion" on the logging screen: that food's editor is already open,
     * and the list is searched down to it so the editor is on screen rather than somewhere below a
     * long list.
     */
    @Test
    fun `opened for one food, its editor is open and in view`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Apple"), aFood(name = "Rice"), aFood(name = "Tahini")))
        val viewModel = FoodsViewModel(foods, Now { 1_000 }, SavedStateHandle(mapOf("food" to "2")))
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.editing?.foodId).isEqualTo(2L)
        assertThat(state.editing?.form?.name).isEqualTo("Rice")
        assertThat(state.query).isEqualTo("Rice")
        assertThat(state.foods.map { it.name }).containsExactly("Rice")
    }

    /**
     * The route's food is acted on once. Recreated after the system ended the process, with the same
     * saved state, the manager must not reopen an editor he closed and search down to it again.
     */
    @Test
    fun `recreated after the process ended, the editor opened for one food is not opened again`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Apple"), aFood(name = "Rice")))
            val saved = SavedStateHandle(mapOf("food" to "2"))
            val first = FoodsViewModel(foods, Now { 1_000 }, saved)
            backgroundScope.launch { first.state.collect { } }
            advanceUntilIdle()
            assertThat(first.state.value.editing?.foodId).isEqualTo(2L)

            val recreated = FoodsViewModel(foods, Now { 1_000 }, saved)
            backgroundScope.launch { recreated.state.collect { } }
            advanceUntilIdle()

            assertThat(recreated.state.value.editing).isNull()
            assertThat(recreated.state.value.query).isEmpty()
        }

    /** A food that has gone by the time the manager opens leaves the manager as it always opens. */
    @Test
    fun `opened for a food that is not there, nothing is open`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Apple")))
        val viewModel = FoodsViewModel(foods, Now { 1_000 }, SavedStateHandle(mapOf("food" to "9")))
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        assertThat(viewModel.state.value.editing).isNull()
        assertThat(viewModel.state.value.query).isEmpty()
    }

    /** "Join with a duplicate" on Yoghurt's own screen, then the duplicate picked off the list. */
    private fun TestScope.pickedFromItsOwnScreen(viewModel: FoodsViewModel) {
        viewModel.beginMerging(1)
        advanceUntilIdle()
        viewModel.mergeInto(2)
        advanceUntilIdle()
    }

    /**
     * The state only flows while something is collecting it, and everything here reads the foods out
     * of it — so a test that never collects would be editing an empty list.
     */
    private fun TestScope.watched(vararg foods: Food): FoodsViewModel =
        watched(FakeFoodRepository(foods.toList()))

    private fun TestScope.watched(foods: FakeFoodRepository): FoodsViewModel {
        val viewModel = FoodsViewModel(foods, Now { 1_000 })
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()
        return viewModel
    }
}
