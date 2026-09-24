package com.metaself.app.ui.screen.foods

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.EditRefused
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.screen.foods.FoodsViewModel.Companion.JOIN_FROM
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
 * Maintaining the food list: finding, choosing and joining (D55 §1). Everything about one food —
 * correcting, reviewing, hiding and deleting it — is its page's, and tested in
 * `FoodPageViewModelTest`.
 *
 * The thing under test is the honesty of the screen's answers: a refusal has to name what stood in
 * the way, and a merge has to leave the surviving food answering to both names.
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


    // --- Merging ----------------------------------------------------------------------------------------

    /**
     * Picking the duplicate from a food's page no longer joins: it settles the pair and asks,
     * exactly as choosing mode does — the same irreversible act, so the same question (D36).
     */
    @Test
    fun `picking the duplicate from a food's page asks and joins nothing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = watched(foods)

            pickedFromItsPage(viewModel)

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

        pickedFromItsPage(viewModel)
        // Asked, not yet answered: the join below is the answer's doing, not the pick's.
        assertThat(foods.current).hasSize(2)
        viewModel.confirmJoining()
        advanceUntilIdle()

        assertThat(foods.current).hasSize(1)
        assertThat(foods.current.single().name).isEqualTo("Yoghurt")
        assertThat(foods.current.single().alsoKnownAs).contains("יוגורט")
        assertThat(viewModel.state.value.merging).isNull()
    }

    /** Not now ends the join outright, as it does on the choosing route: one question, one behaviour. */
    @Test
    fun `not now after picking joins nothing and ends the join`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods)

        pickedFromItsPage(viewModel)
        assertThat(viewModel.state.value.merging?.losing).isNotNull()
        viewModel.cancelMerging()
        advanceUntilIdle()

        assertThat(foods.current).hasSize(2)
        assertThat(viewModel.state.value.merging).isNull()
        assertThat(viewModel.state.value.chosen).isEmpty()
    }

    /** As on the choosing route: the refusal names the meal, and the question stays up to act on. */
    @Test
    fun `a refused join from a food's page leaves both foods and the question standing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
                .refusesToMerge(EditRefused.MealsHoldingBoth(listOf("Vegetable salad")))
            val viewModel = watched(foods)

            pickedFromItsPage(viewModel)
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
            viewModel.beginJoiningFrom(1)
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
        viewModel.beginJoiningFrom(1)
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
        viewModel.beginJoiningFrom(1)
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

        viewModel.beginJoiningFrom(1)
        advanceUntilIdle()
        viewModel.mergeInto(1)
        advanceUntilIdle()

        assertThat(foods.current).hasSize(1)
        assertThat(viewModel.state.value.merging?.losing).isNull()
    }

    // --- Joining from a food's page (D55 §5) -----------------------------------------------------

    /** From a page with no list beneath it, the list is opened by route, already picking. */
    @Test
    fun `opened to join from a food, the list is picking a duplicate for it`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods, savedState = SavedStateHandle(mapOf(JOIN_FROM to "1")))

        assertThat(viewModel.state.value.merging).isEqualTo(Merging(keeping = foods.current[0]))
        assertThat(foods.current).hasSize(2)
    }

    /**
     * The food kept is looked up by id, not out of the list on screen: the search he left the list
     * with is kept, and may not match it, and a hidden one is not listed with Show hidden off.
     */
    @Test
    fun `the food to join from is found even when the list on screen does not hold it`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(
                listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini"), aFood(name = "Bun")),
            )
            foods.hide(3)
            val viewModel = watched(foods)
            viewModel.search("tahini")
            advanceUntilIdle()

            viewModel.beginJoiningFrom(1)
            advanceUntilIdle()
            assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
            // The search he left the list with is kept: the duplicate is probably what it was for.
            assertThat(viewModel.state.value.query).isEqualTo("tahini")

            viewModel.cancelMerging()
            viewModel.search("")
            viewModel.beginJoiningFrom(3)
            advanceUntilIdle()
            assertThat(viewModel.state.value.showHidden).isFalse()
            assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Bun")
        }

    /** A join and a choice cannot both be under way: the choice is let go of. */
    @Test
    fun `joining from a food lets go of what was chosen`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"), aFood(name = "Tahini"))
        viewModel.beginChoosing(2)
        advanceUntilIdle()

        viewModel.beginJoiningFrom(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.chosen).isEmpty()
        assertThat(viewModel.state.value.merging?.keeping?.name).isEqualTo("Yoghurt")
    }

    /**
     * Spent once read (§8): a list restored after the process ended must not begin again a join he
     * has since finished or walked away from.
     */
    @Test
    fun `the food to join from is spent once read`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val handle = SavedStateHandle(mapOf(JOIN_FROM to "1"))
        watched(foods, savedState = handle)

        assertThat(handle.get<String>(JOIN_FROM)).isNull()
        val again = watched(foods, savedState = handle)
        assertThat(again.state.value.merging).isNull()
    }

    /** Gone between the page and the list — joined away, or deleted — there is nothing to join. */
    @Test
    fun `a food gone meanwhile begins nothing and says nothing`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"))

        viewModel.beginJoiningFrom(99)
        advanceUntilIdle()

        assertThat(viewModel.state.value.merging).isNull()
        assertThat(viewModel.state.value.refusal).isNull()
        assertThat(viewModel.state.value.failed).isNull()
    }

    /** Opening a food's page while the join question is up ends the join, as opening one did. */
    @Test
    fun `opening a food ends a join`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        val viewModel = watched(foods)
        pickedFromItsPage(viewModel)

        viewModel.openingAFood()
        advanceUntilIdle()

        assertThat(viewModel.state.value.merging).isNull()
        assertThat(foods.current).hasSize(2)
    }

    // --- A food hidden from its page (D55 §6) -------------------------------------------------------

    /** A food that silently leaves a list it was just in reads as deleted; this says what happened. */
    @Test
    fun `a food hidden from its page is said on the list, by its current name`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
            foods.hide(1)
            val viewModel = watched(foods)

            viewModel.sayHidden(1)
            advanceUntilIdle()
            assertThat(viewModel.state.value.hid?.name).isEqualTo("Yoghurt")

            foods.rename(1, "Greek yoghurt")
            advanceUntilIdle()
            assertThat(viewModel.state.value.hid?.name).isEqualTo("Greek yoghurt")
        }

    @Test
    fun `Show again brings the food back and takes the line down`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        foods.hide(1)
        val viewModel = watched(foods)
        viewModel.sayHidden(1)
        advanceUntilIdle()

        viewModel.showAgain()
        advanceUntilIdle()

        assertThat(foods.current.single().hidden).isFalse()
        assertThat(viewModel.state.value.hid).isNull()
        assertThat(viewModel.state.value.foods.map { it.name }).containsExactly("Yoghurt")
    }

    @Test
    fun `All right takes the line down and leaves the food hidden`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        foods.hide(1)
        val viewModel = watched(foods)
        viewModel.sayHidden(1)
        advanceUntilIdle()

        viewModel.dismissHidden()
        advanceUntilIdle()

        assertThat(foods.current.single().hidden).isTrue()
        assertThat(viewModel.state.value.hid).isNull()
    }

    @Test
    fun `searching, filtering, choosing or opening a food takes the line down`() =
        runTest(dispatcher) {
            val ways: List<Pair<String, (FoodsViewModel) -> Unit>> = listOf(
                "searching" to { it.search("tahini") },
                "showing hidden foods" to { it.showHidden(true) },
                "narrowing to the foods that only know a portion" to { it.showOnlyPortions(true) },
                "choosing" to { it.beginChoosing(2) },
                "opening a food" to { it.openingAFood() },
            )
            ways.forEach { (way, act) ->
                val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
                foods.hide(1)
                val viewModel = watched(foods)
                viewModel.sayHidden(1)
                advanceUntilIdle()

                act(viewModel)
                advanceUntilIdle()

                assertWithMessage(way).that(viewModel.state.value.hid).isNull()
                assertWithMessage(way).that(foods.current.first().hidden).isTrue()
            }
        }

    /** Deleted or joined away before the list was back: there is nothing to say it about. */
    @Test
    fun `a food gone meanwhile is not said to be hidden`() = runTest(dispatcher) {
        val viewModel = watched(aFood(name = "Yoghurt"))

        viewModel.sayHidden(99)
        advanceUntilIdle()

        assertThat(viewModel.state.value.hid).isNull()
    }

    // --- When an action throws -----------------------------------------------------------------------

    /** Every write here is one transaction, so every one of them can honestly say nothing changed. */
    @Test
    fun `every write that throws says nothing was changed`() = runTest(dispatcher) {
        val ways: List<Pair<String, (FoodsViewModel) -> Unit>> = listOf(
            "showing again" to { it.showAgain() },
            "joining" to { it.confirmJoining() },
        )
        ways.forEach { (way, act) ->
            val foods = Failing(FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood("Tahini"))))
            val problems = RecordingProblemLog()
            val viewModel = watched(foods, problems)
            if (way == "showing again") {
                foods.inner.hide(1)
                viewModel.sayHidden(1)
            }
            if (way == "joining") pickedFromItsPage(viewModel)
            advanceUntilIdle()

            foods.writesFail = true
            act(viewModel)
            advanceUntilIdle()

            assertWithMessage(way).that(viewModel.state.value.failed)
                .isEqualTo(ActionRefused.NOTHING_CHANGED)
            assertWithMessage(way).that(problems.recorded).hasSize(1)
            assertWithMessage(way).that(foods.inner.current).hasSize(2)
        }
    }

    /** A read that was to open a question wrote nothing, and says it could not open. */
    @Test
    fun `a lookup that throws says it could not be opened`() = runTest(dispatcher) {
        val ways: List<Pair<String, (FoodsViewModel) -> Unit>> = listOf(
            "joining from a food's page" to { it.beginJoiningFrom(1) },
            "joining the two chosen" to { it.beginChoosing(1); it.toggleChosen(2); it.joinChosen() },
        )
        ways.forEach { (way, act) ->
            val foods = Failing(FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood("Tahini"))))
            val problems = RecordingProblemLog()
            val viewModel = watched(foods, problems)

            foods.readsFail = true
            act(viewModel)
            advanceUntilIdle()

            assertWithMessage(way).that(viewModel.state.value.failed)
                .isEqualTo(ActionRefused.COULD_NOT_OPEN)
            assertWithMessage(way).that(problems.recorded).hasSize(1)
        }
    }

    /** One sentence in the slot at a time: dismissing takes it down, and a refusal replaces it. */
    @Test
    fun `the failure is dismissed like a refusal, and a later refusal replaces it`() =
        runTest(dispatcher) {
            val foods = Failing(
                FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood("יוגורט")))
                    .refusesToMerge(EditRefused.MealsHoldingBoth(listOf("Vegetable salad"))),
            )
            val viewModel = watched(foods, RecordingProblemLog())
            pickedFromItsPage(viewModel)
            foods.writesFail = true
            viewModel.confirmJoining()
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isNotNull()

            viewModel.dismissRefusal()
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isNull()

            viewModel.confirmJoining()
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isNotNull()
            foods.writesFail = false
            viewModel.confirmJoining()
            advanceUntilIdle()
            assertThat(viewModel.state.value.refusal).contains("Vegetable salad")
            assertThat(viewModel.state.value.failed).isNull()
        }

    // --- Choosing several foods at once ---------------------------------------------------------

    /**
     * Holding is what starts it, and an ordinary tap still opens a food's page. Choosing that
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

        // On its page, which leaves the list and its choice where they were.
        foods.hide(1)
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

    /** The fake, with a switch that makes its writes — or its lookups — throw. */
    private class Failing(val inner: FakeFoodRepository) : FoodRepository by inner {
        var writesFail = false
        var readsFail = false

        private fun write() {
            if (writesFail) throw IllegalStateException("disk full")
        }

        override suspend fun byId(id: Long): Food? {
            if (readsFail) throw IllegalStateException("disk unreadable")
            return inner.byId(id)
        }

        override suspend fun saveForm(foodId: Long, name: String, brand: String?, facts: FoodFacts) =
            write().let { inner.saveForm(foodId, name, brand, facts) }

        override suspend fun hide(foodId: Long) = write().let { inner.hide(foodId) }

        override suspend fun unhide(foodId: Long) = write().let { inner.unhide(foodId) }

        override suspend fun delete(foodId: Long) = write().let { inner.delete(foodId) }

        override suspend fun merge(winnerId: Long, loserId: Long) =
            write().let { inner.merge(winnerId, loserId) }
    }

    /** "Join with a duplicate" on Yoghurt's page, then the duplicate picked off the list. */
    private fun TestScope.pickedFromItsPage(viewModel: FoodsViewModel) {
        viewModel.beginJoiningFrom(1)
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

    private fun TestScope.watched(
        foods: FoodRepository,
        problems: ProblemLog = ProblemLog.NONE,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): FoodsViewModel {
        val viewModel = FoodsViewModel(foods, problems, savedState)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()
        return viewModel
    }
}
