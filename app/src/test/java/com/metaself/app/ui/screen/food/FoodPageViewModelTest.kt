package com.metaself.app.ui.screen.food

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.ai.FakeFoodReviewer
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.time.Now
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.FoodReviewer
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodUse
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.food.FormReview
import com.metaself.app.ui.food.Review
import com.metaself.app.ui.food.ReviewedBox
import kotlinx.coroutines.CompletableDeferred
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
 * One food's own page (D55): everything about one food that the list's inline editor did, and the
 * page's own ways of closing.
 *
 * The cases about correcting, deleting and reviewing were the list's (`FoodsViewModelTest`),
 * re-pointed here. Where one of them moved on by opening another food, the page moves on by closing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodPageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // --- Opening (D55 §2) -----------------------------------------------------------------------

    @Test
    fun `the page opens on the food as stored`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(greekYoghurt())))

        val state = viewModel.state.value
        assertThat(state.food?.name).isEqualTo("Greek yoghurt")
        assertThat(state.editing?.form).isEqualTo(FoodForm.of(greekYoghurt().copy(id = 1)))
        assertThat(state.closing).isNull()
    }

    @Test
    fun `the page opens on its own food, whichever others are stored`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(greekYoghurt(), lentilSoup()))

        val viewModel = page(foods, foodId = 2)

        assertThat(viewModel.state.value.editing?.form?.name).isEqualTo("Lentil soup")
    }

    /** The boxes are his once the page is open: a change to the stored food never retypes them. */
    @Test
    fun `a later change to the stored food does not overwrite what was typed`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(greekYoghurt()))
        val viewModel = page(foods)
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Yog"))

        foods.rename(1, "Strained yoghurt")
        advanceUntilIdle()

        assertThat(viewModel.state.value.food?.name).isEqualTo("Strained yoghurt")
        assertThat(viewModel.state.value.editing?.form?.name).isEqualTo("Yog")
    }

    // --- Where it's used (D55 §3) ---------------------------------------------------------------

    @Test
    fun `where it is used carries the count and the saved meals, and follows them`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(greekYoghurt()))
                .logged(1, 24)
                .usedBySavedMeals(1, "Snack plate", "Breakfast bowl")
            val viewModel = page(foods)

            assertThat(viewModel.state.value.use)
                .isEqualTo(FoodUse(logged = 24, savedMeals = listOf("Breakfast bowl", "Snack plate")))

            foods.logged(1, 25)
            advanceUntilIdle()

            assertThat(viewModel.state.value.use?.logged).isEqualTo(25)
        }

    // --- Correcting -----------------------------------------------------------------------------

    @Test
    fun `correcting a food changes what it knows from now on, and the page closes`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            val viewModel = page(foods)

            viewModel.setForm(viewModel.state.value.editing!!.form.copy(kcalPer100g = "90"))
            viewModel.save()
            advanceUntilIdle()

            assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(90.0)
            assertThat(viewModel.state.value.closing).isEqualTo(Closing.Saved)
        }

    @Test
    fun `renaming a food keeps everything it knows`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "yogurt", facts = FoodFacts(per100g = aPer100g(72.0)))))
        val viewModel = page(foods)

        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Greek yoghurt"))
        viewModel.save()
        advanceUntilIdle()

        assertThat(foods.current.single().name).isEqualTo("Greek yoghurt")
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(72.0)
    }

    /** A form that complains before he has done anything is a form shouting at nobody. */
    @Test
    fun `an opened food does not complain until he tries to save`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(aFood())))

        assertThat(viewModel.state.value.editing!!.showErrors).isFalse()
    }

    @Test
    fun `saving a food that would know nothing shows the reason and changes nothing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            val viewModel = page(foods)

            viewModel.setForm(FoodForm(name = "Yoghurt"))
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.state.value.editing!!.showErrors).isTrue()
            assertThat(viewModel.state.value.closing).isNull()
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
        val viewModel = page(foods)

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
        val viewModel = page(FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Hummus"))))

        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Hummus"))
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refusal).contains("Hummus")
    }

    /** D55 §2: a refused Save leaves the page, its boxes and its marks exactly as they were. */
    @Test
    fun `a refused Save does not close the page, and leaves the boxes and their marks`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(oatBiscuit(), aFood(name = "Hummus")))
            val viewModel = page(foods, reviewer = FakeFoodReviewer(bothChanged()))
            viewModel.review()
            advanceUntilIdle()
            viewModel.applyReview()
            advanceUntilIdle()
            viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Hummus"))
            advanceUntilIdle()
            val before = viewModel.state.value.editing!!
            assertThat(before.reviewing.changedBoxes).isNotEmpty()

            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.closing).isNull()
            assertThat(state.refusal).contains("Hummus")
            assertThat(state.editing!!.form).isEqualTo(before.form)
            assertThat(state.editing!!.reviewing.changedBoxes).isEqualTo(before.reviewing.changedBoxes)
        }

    // --- Hiding and showing again (D55 §6) ------------------------------------------------------

    @Test
    fun `hiding takes a food off the lists without losing it, and closes the page`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(greekYoghurt()))
            val viewModel = page(foods)

            viewModel.hide()
            advanceUntilIdle()

            assertThat(foods.current.single().hidden).isTrue()
            assertThat(viewModel.state.value.closing).isEqualTo(Closing.Hidden(1, "Greek yoghurt"))
        }

    @Test
    fun `showing a hidden food again keeps the page open`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(hamburgerBun()))
        val viewModel = page(foods)
        assertThat(viewModel.state.value.food?.hidden).isTrue()

        viewModel.unhide()
        advanceUntilIdle()

        assertThat(viewModel.state.value.food?.hidden).isFalse()
        assertThat(viewModel.state.value.closing).isNull()
    }

    /**
     * Show again pressed on a page already leaving because it hid the food: the list beneath is
     * about to say the food is hidden, so bringing it back here would make that line untrue.
     */
    @Test
    fun `showing again after the page began closing does nothing`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(greekYoghurt()))
        val viewModel = page(foods)
        viewModel.hide()
        advanceUntilIdle()

        viewModel.unhide()
        advanceUntilIdle()

        assertThat(foods.current.single().hidden).isTrue()
        assertThat(viewModel.state.value.closing).isEqualTo(Closing.Hidden(1, "Greek yoghurt"))
    }

    // --- Deleting asks first (D36) --------------------------------------------------------------

    /** The question names the STORED food, because that is what goes, not what is half-typed. */
    @Test
    fun `asking to delete a food names it and deletes nothing`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = page(foods)
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Yog"))

        viewModel.askToDelete()
        advanceUntilIdle()

        val deleting = viewModel.state.value.deleting
        assertThat(deleting).isInstanceOf(Deleting.Asking::class.java)
        assertThat(deleting!!.food.name).isEqualTo("Yoghurt")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    /** Keep it changes nothing: the question goes, and what he had typed stays his. */
    @Test
    fun `keeping it leaves the food and the page as they were`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = page(foods)
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Yog"))
        viewModel.askToDelete()
        advanceUntilIdle()
        assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Asking::class.java)

        viewModel.cancelDeleting()
        advanceUntilIdle()

        assertThat(viewModel.state.value.deleting).isNull()
        assertThat(viewModel.state.value.editing?.form?.name).isEqualTo("Yog")
        assertThat(viewModel.state.value.closing).isNull()
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    @Test
    fun `deleting it once asked removes the food and closes the page`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = page(foods)

        viewModel.askToDelete()
        advanceUntilIdle()
        viewModel.confirmDeleting()
        advanceUntilIdle()

        assertThat(foods.current).isEmpty()
        assertThat(viewModel.state.value.deleting).isNull()
        // Deleted, not Gone: the food leaving the store afterwards does not close it a second time.
        assertThat(viewModel.state.value.closing).isEqualTo(Closing.Deleted)
    }

    /** A question with no real answer is not asked: a food a saved meal uses is refused first. */
    @Test
    fun `a food a saved meal uses is refused before anything is asked`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            .usedBySavedMeals(1, "Vegetable salad")
        val viewModel = page(foods)

        viewModel.askToDelete()
        advanceUntilIdle()

        val deleting = viewModel.state.value.deleting
        assertThat(deleting).isInstanceOf(Deleting.Refused::class.java)
        assertThat((deleting as Deleting.Refused).sentence).contains("Vegetable salad")
        assertThat(viewModel.state.value.refusal).isNull()
        assertThat(viewModel.state.value.closing).isNull()
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    /** A meal that began using the food between the question and the answer: refused, and stays. */
    @Test
    fun `a refusal that arrives only at the delete is still shown, and the page stays`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            val viewModel = page(foods)
            viewModel.askToDelete()
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
            assertThat(viewModel.state.value.closing).isNull()
        }

    /**
     * Every way off the page lets go of the question — or a Delete answered after he had left for a
     * join would delete the food he was joining.
     */
    @Test
    fun `leaving for a join lets go of the question`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = page(foods)
        viewModel.askToDelete()
        advanceUntilIdle()
        assertThat(viewModel.state.value.deleting).isInstanceOf(Deleting.Asking::class.java)

        viewModel.beginJoining()
        advanceUntilIdle()
        assertThat(viewModel.state.value.deleting).isNull()

        viewModel.confirmDeleting()
        advanceUntilIdle()
        assertThat(foods.current).hasSize(1)
    }

    /**
     * A refusal to delete is drawn directly above Save. Once he has gone back to typing, or pressed
     * Save, it has to go: left standing, it reads as the reason his Save was refused.
     */
    @Test
    fun `typing or saving lets go of a refusal to delete`() = runTest(dispatcher) {
        // (a) Typing.
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
                .usedBySavedMeals(1, "Vegetable salad")
            val viewModel = page(foods)
            viewModel.askToDelete()
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
            val viewModel = page(foods)
            viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Tahini"))
            viewModel.askToDelete()
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
     * Asking resolves after two reads. If the page has closed meanwhile, the answer has no page to
     * land on. Each case closes BEFORE the dispatcher runs, the timing the fake's instant answers
     * otherwise hide.
     */
    @Test
    fun `an answer that arrives after the page closed is dropped`() = runTest(dispatcher) {
        // (a) The question itself, and (b) a refusal.
        listOf(
            FakeFoodRepository(listOf(aFood(name = "Yoghurt"))),
            FakeFoodRepository(listOf(aFood(name = "Yoghurt"))).usedBySavedMeals(1, "Vegetable salad"),
        ).forEach { foods ->
            val viewModel = page(foods)
            viewModel.askToDelete()
            viewModel.beginJoining()
            viewModel.closed()
            advanceUntilIdle()

            assertThat(viewModel.state.value.deleting).isNull()
            assertThat(viewModel.state.value.refusal).isNull()
            assertThat(viewModel.state.value.closing).isNull()
        }
        // (c) A confirmed delete: the food goes — he pressed Delete — but a page already closing
        // for a join is not closed a second time, as deleted.
        run {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            val viewModel = page(foods)
            viewModel.askToDelete()
            advanceUntilIdle()
            viewModel.confirmDeleting()
            viewModel.beginJoining()
            advanceUntilIdle()

            assertThat(foods.current).isEmpty()
            assertThat(viewModel.state.value.closing).isEqualTo(Closing.Join(1))
        }
    }

    // --- Joining (D55 §5) -----------------------------------------------------------------------

    /** The pick is made on the list; the page only says so. Nothing typed goes with it. */
    @Test
    fun `joining with a duplicate closes the page for the list, and joins and saves nothing`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
            val viewModel = page(foods)
            viewModel.setForm(viewModel.state.value.editing!!.form.copy(kcalPer100g = "90"))

            viewModel.beginJoining()
            advanceUntilIdle()

            assertThat(viewModel.state.value.closing).isEqualTo(Closing.Join(1))
            assertThat(foods.current.map { it.name }).containsExactly("Yoghurt", "יוגורט")
            assertThat(foods.current.first().facts.per100g!!.nutrients.kcal).isEqualTo(72.0)
        }

    // --- When the food is gone (D55 §7) ---------------------------------------------------------

    @Test
    fun `a page for a food that is not stored closes`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(aFood(name = "Yoghurt"))), foodId = 9)

        assertThat(viewModel.state.value.closing).isEqualTo(Closing.Gone)
        assertThat(viewModel.state.value.editing).isNull()
    }

    /** Deleted from elsewhere while the page is open: no form is left standing for nothing. */
    @Test
    fun `a food that goes while its page is open closes it`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val viewModel = page(foods)

        foods.delete(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.closing).isEqualTo(Closing.Gone)
    }

    /** One close (§7): once the page is closing for a reason, the food leaving does not add another. */
    @Test
    fun `a food that goes after the page began closing does not close it again`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            val viewModel = page(foods)
            viewModel.beginJoining()
            advanceUntilIdle()
            viewModel.closed()

            foods.delete(1)
            advanceUntilIdle()

            assertThat(viewModel.state.value.closing).isNull()
        }

    @Test
    fun `closed takes the closing down`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(aFood(name = "Yoghurt"))))
        viewModel.beginJoining()
        advanceUntilIdle()
        assertThat(viewModel.state.value.closing).isEqualTo(Closing.Join(1))

        viewModel.closed()
        advanceUntilIdle()

        assertThat(viewModel.state.value.closing).isNull()
    }

    // --- When an action throws ------------------------------------------------------------------

    @Test
    fun `a Save that throws says nothing was changed and leaves the form as typed`() =
        runTest(dispatcher) {
            val foods = Failing(FakeFoodRepository(listOf(aFood(name = "Yoghurt"))))
            val problems = RecordingProblemLog()
            val viewModel = page(foods, problems = problems)
            val typed = viewModel.state.value.editing!!.form.copy(name = "Kefir")
            viewModel.setForm(typed)

            foods.writesFail = true
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.failed).isEqualTo(ActionRefused.NOTHING_CHANGED)
            assertThat(state.refusal).isNull()
            assertThat(state.closing).isNull()
            assertThat(state.editing?.form).isEqualTo(typed)
            assertThat(foods.inner.current.single().name).isEqualTo("Yoghurt")
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
            assertThat(problems.recorded.single().detail).contains("disk full")
        }

    /** Every write here is one transaction, so every one of them can honestly say nothing changed. */
    @Test
    fun `every write that throws says nothing was changed, and the page stays`() =
        runTest(dispatcher) {
            val ways: List<Pair<String, (FoodPageViewModel) -> Unit>> = listOf(
                "hiding" to { it.hide() },
                "unhiding" to { it.unhide() },
                "deleting" to { it.confirmDeleting() },
            )
            ways.forEach { (way, act) ->
                val foods = Failing(FakeFoodRepository(listOf(aFood(name = "Yoghurt"))))
                val problems = RecordingProblemLog()
                val viewModel = page(foods, problems = problems)
                if (way == "deleting") viewModel.askToDelete()
                advanceUntilIdle()

                foods.writesFail = true
                act(viewModel)
                advanceUntilIdle()

                assertWithMessage(way).that(viewModel.state.value.failed)
                    .isEqualTo(ActionRefused.NOTHING_CHANGED)
                assertWithMessage(way).that(viewModel.state.value.closing).isNull()
                assertWithMessage(way).that(problems.recorded).hasSize(1)
                assertWithMessage(way).that(foods.inner.current).hasSize(1)
            }
        }

    /** A read that was to open a question wrote nothing, and says it could not open. */
    @Test
    fun `a lookup that throws says it could not be opened`() = runTest(dispatcher) {
        val foods = Failing(FakeFoodRepository(listOf(aFood(name = "Yoghurt"))))
        val problems = RecordingProblemLog()
        val viewModel = page(foods, problems = problems)

        foods.readsFail = true
        viewModel.askToDelete()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed).isEqualTo(ActionRefused.COULD_NOT_OPEN)
        assertThat(viewModel.state.value.deleting).isNull()
        assertThat(problems.recorded).hasSize(1)
    }

    /** One sentence in the slot at a time: dismissing takes it down, and a refusal replaces it. */
    @Test
    fun `the failure is dismissed like a refusal, and a later refusal replaces it`() =
        runTest(dispatcher) {
            val foods = Failing(FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood("Tahini"))))
            val viewModel = page(foods, problems = RecordingProblemLog())
            foods.writesFail = true
            viewModel.unhide()
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isNotNull()

            viewModel.dismissRefusal()
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isNull()

            viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "Tahini"))
            viewModel.unhide()
            advanceUntilIdle()
            assertThat(viewModel.state.value.failed).isNotNull()
            foods.writesFail = false
            viewModel.save()
            advanceUntilIdle()
            assertThat(viewModel.state.value.refusal).isNotNull()
            assertThat(viewModel.state.value.failed).isNull()
        }

    // --- Asking for a review (D54) --------------------------------------------------------------
    //
    // The Oat biscuit here is D54's invented example: per 100 g off a label, per biscuit and its
    // weight typed. The review keeps per 100 g and changes the biscuit's fat from 1 to 4 g.

    @Test
    fun `a review sends the untouched groups with their sources and a retyped one as typed`() =
        runTest(dispatcher) {
            val reviewer = FakeFoodReviewer(fatChanged())
            val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = reviewer)

            viewModel.review()
            advanceUntilIdle()

            val first = reviewer.requests.single()
            assertThat(first.process).isEqualTo(ReviewProcess.EXISTING_FOOD)
            assertThat(first.name).isEqualTo("Oat biscuit")
            assertThat(first.per100g!!.source).isEqualTo(Source.LABEL)
            assertThat(first.perUnit!!.source).isEqualTo(Source.TYPED)
            assertThat(first.unitName).isEqualTo("biscuit")

            viewModel.dismissReview()
            viewModel.setForm(viewModel.state.value.editing!!.form.copy(kcalPer100g = "470"))
            viewModel.review()
            advanceUntilIdle()

            assertThat(reviewer.requests.last().per100g!!.source).isEqualTo(Source.TYPED)
        }

    @Test
    fun `nothing is written to the boxes until he accepts`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = FakeFoodReviewer(fatChanged()))
        val opened = viewModel.state.value.editing!!.form

        viewModel.review()
        advanceUntilIdle()

        val editing = viewModel.state.value.editing!!
        assertThat(editing.form).isEqualTo(opened)
        val shown = editing.reviewing.review as Review.Shown
        assertThat(shown.review.perUnit!!.changes.single().to).isEqualTo(4.0)
        assertThat(shown.review.per100g).isNull()
    }

    @Test
    fun `applying per biscuit fills its four boxes, and Save stores only that group as an estimate`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(oatBiscuit()))
            val viewModel = page(foods, reviewer = FakeFoodReviewer(fatChanged()))
            viewModel.review()
            advanceUntilIdle()

            viewModel.applyReview()
            advanceUntilIdle()

            val form = viewModel.state.value.editing!!.form
            assertThat(listOf(form.kcalPerUnit, form.proteinPerUnit, form.carbsPerUnit, form.fatPerUnit))
                .containsExactly("90", "1", "12", "4").inOrder()
            assertThat(form.unitName).isEqualTo("biscuit")
            assertThat(form.gramsPerUnit).isEqualTo("18")
            assertThat(viewModel.state.value.editing!!.reviewing.review).isNull()

            viewModel.save()
            advanceUntilIdle()

            val saved = foods.current.single().facts
            assertThat(saved.perUnit!!.nutrients.fatG).isEqualTo(4.0)
            assertThat(saved.perUnit!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
            assertThat(saved.perUnit!!.provenance.confidence).isEqualTo(Confidence.MEDIUM)
            assertThat(saved.per100g!!.provenance.source).isEqualTo(Source.LABEL)
            assertThat(saved.gramsPerUnit!!.provenance.source).isEqualTo(Source.TYPED)
        }

    /** D54 §8.5: a label's long figures open rounded; Save on untouched boxes keeps them exactly. */
    @Test
    fun `saving a food whose figures opened rounded keeps them exactly, and their source`() =
        runTest(dispatcher) {
            val scanned = aFood(
                name = "Seeded cracker",
                facts = FoodFacts(
                    per100g = PerHundredGrams(
                        Nutrients(100.0, 8.571428571428571, 12.857142857142858, 5.714285714285714),
                        Provenance(Source.LABEL, null, 0),
                    ),
                ),
            )
            val foods = FakeFoodRepository(listOf(scanned))
            val viewModel = page(foods)
            assertThat(viewModel.state.value.editing!!.form.proteinPer100g).isEqualTo("8.57")

            viewModel.save()
            advanceUntilIdle()

            assertThat(foods.current.single().facts.per100g).isEqualTo(scanned.facts.per100g)
        }

    /** Still a mix with a guess in it (D54 §5): labelled by its weakest member. */
    @Test
    fun `a figure changed after accepting still saves the group as an estimate`() =
        runTest(dispatcher) {
            val foods = FakeFoodRepository(listOf(oatBiscuit()))
            val viewModel = page(foods, reviewer = FakeFoodReviewer(fatChanged()))
            viewModel.review()
            advanceUntilIdle()
            viewModel.applyReview()
            advanceUntilIdle()
            viewModel.setForm(viewModel.state.value.editing!!.form.copy(kcalPerUnit = "95"))

            viewModel.save()
            advanceUntilIdle()

            val perUnit = foods.current.single().facts.perUnit!!
            assertThat(perUnit.nutrients.kcal).isEqualTo(95.0)
            assertThat(perUnit.nutrients.fatG).isEqualTo(4.0)
            assertThat(perUnit.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        }

    @Test
    fun `typing in a group withdraws its suggestion and leaves the other`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = FakeFoodReviewer(bothChanged()))
        viewModel.review()
        advanceUntilIdle()

        viewModel.setForm(viewModel.state.value.editing!!.form.copy(fatPerUnit = "2"))
        advanceUntilIdle()

        val shown = viewModel.state.value.editing!!.reviewing.review as Review.Shown
        assertThat(shown.review.perUnit).isNull()
        assertThat(shown.review.per100g).isNotNull()
    }

    @Test
    fun `typing in a group while the review is out withdraws what comes back for it`() =
        runTest(dispatcher) {
            val reviewer = FakeFoodReviewer(bothChanged()).apply { gate = CompletableDeferred() }
            val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = reviewer)
            viewModel.review()
            advanceUntilIdle()
            assertThat(viewModel.state.value.editing!!.reviewing.asking).isTrue()

            viewModel.setForm(viewModel.state.value.editing!!.form.copy(kcalPer100g = "470"))
            reviewer.gate!!.complete(Unit)
            advanceUntilIdle()

            val shown = viewModel.state.value.editing!!.reviewing.review as Review.Shown
            assertThat(shown.review.per100g).isNull()
            assertThat(shown.review.perUnit).isNotNull()
        }

    @Test
    fun `while a review is out, asking again sends nothing`() = runTest(dispatcher) {
        val reviewer = FakeFoodReviewer(fatChanged()).apply { gate = CompletableDeferred() }
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = reviewer)

        viewModel.review()
        advanceUntilIdle()
        viewModel.review()
        advanceUntilIdle()

        assertThat(reviewer.requests).hasSize(1)
    }

    @Test
    fun `Dismiss takes down what is left and keeps what was accepted`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(oatBiscuit()))
        val viewModel = page(foods, reviewer = FakeFoodReviewer(fatChanged()))
        viewModel.review()
        advanceUntilIdle()

        viewModel.applyReview()
        viewModel.dismissReview()
        advanceUntilIdle()

        val editing = viewModel.state.value.editing!!
        assertThat(editing.reviewing.review).isNull()
        assertThat(editing.reviewing.accepted)
            .containsExactly(FactGroup.PER_UNIT, AcceptedGroup(Confidence.MEDIUM))
        assertThat(editing.form.kcalPer100g).isEqualTo("480")

        viewModel.save()
        advanceUntilIdle()

        val saved = foods.current.single().facts
        assertThat(saved.perUnit!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(saved.per100g!!.provenance.source).isEqualTo(Source.LABEL)
    }

    @Test
    fun `Apply these changes accepts every group with a suggestion`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = FakeFoodReviewer(bothChanged()))
        viewModel.review()
        advanceUntilIdle()

        viewModel.applyReview()
        advanceUntilIdle()

        val editing = viewModel.state.value.editing!!
        assertThat(editing.form.kcalPer100g).isEqualTo("470")
        assertThat(editing.form.fatPerUnit).isEqualTo("4")
        assertThat(editing.reviewing.accepted.keys)
            .containsExactly(FactGroup.PER_100G, FactGroup.PER_UNIT)
    }

    /** D54 §11: Undo puts the boxes and the review back as they stood, and Save then keeps them. */
    @Test
    fun `Undo restores the boxes and the suggestions, and nothing is accepted`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(oatBiscuit()))
        val viewModel = page(foods, reviewer = FakeFoodReviewer(bothChanged()))
        val opened = viewModel.state.value.editing!!.form
        viewModel.review()
        advanceUntilIdle()
        val shown = viewModel.state.value.editing!!.reviewing

        viewModel.applyReview()
        advanceUntilIdle()
        assertThat(viewModel.state.value.editing!!.reviewing.changedBoxes).hasSize(2)
        viewModel.undoReview()
        advanceUntilIdle()

        val editing = viewModel.state.value.editing!!
        assertThat(editing.form).isEqualTo(opened)
        assertThat(editing.reviewing).isEqualTo(shown)
        assertThat(editing.reviewing.accepted).isEmpty()
        assertThat(editing.reviewing.changedBoxes).isEmpty()

        viewModel.save()
        advanceUntilIdle()

        assertThat(foods.current.single()).isEqualTo(oatBiscuit().copy(id = 1))
    }

    @Test
    fun `typing in a box the review changed takes its mark off`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = FakeFoodReviewer(bothChanged()))
        viewModel.review()
        advanceUntilIdle()
        viewModel.applyReview()
        advanceUntilIdle()

        viewModel.setForm(viewModel.state.value.editing!!.form.copy(fatPerUnit = "5"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.editing!!.reviewing.changedBoxes)
            .containsExactly(ReviewedBox(FactGroup.PER_100G, Figure.KCAL))
        assertThat(viewModel.state.value.editing!!.reviewing.accepted.keys)
            .containsExactly(FactGroup.PER_100G, FactGroup.PER_UNIT)
    }

    /** Leaving the page discards the review with the rest (D55 §2); a page opened again is fresh. */
    @Test
    fun `leaving the page forgets the review and everything accepted`() = runTest(dispatcher) {
        val foods = FakeFoodRepository(listOf(oatBiscuit()))
        val left = page(foods, reviewer = FakeFoodReviewer(fatChanged()))
        left.review()
        advanceUntilIdle()
        left.applyReview()

        val again = page(foods)

        val editing = again.state.value.editing!!
        assertThat(editing.reviewing).isEqualTo(FormReview())
        assertThat(editing.form.fatPerUnit).isEqualTo("1")
        assertThat(foods.current.single()).isEqualTo(oatBiscuit().copy(id = 1))
    }

    /** `askToDelete`'s rule: an answer for a page that has closed is not put back on it. */
    @Test
    fun `a review answer that lands after the page began closing is dropped`() = runTest(dispatcher) {
        val ways: List<Pair<String, (FoodPageViewModel) -> Unit>> = listOf(
            "closing" to { it.beginJoining() },
            "closed" to { it.beginJoining(); it.closed() },
        )
        ways.forEach { (way, move) ->
            val reviewer = FakeFoodReviewer(fatChanged()).apply { gate = CompletableDeferred() }
            val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = reviewer)
            viewModel.review()
            advanceUntilIdle()

            move(viewModel)
            advanceUntilIdle()
            val waiting = viewModel.state.value.editing!!.reviewing
            reviewer.gate!!.complete(Unit)
            advanceUntilIdle()

            assertWithMessage(way).that(viewModel.state.value.editing!!.reviewing).isEqualTo(waiting)
            assertWithMessage(way).that(viewModel.state.value.refusal).isNull()
        }
    }

    /** The same rule for a review that throws: its sentence belongs to a page that has closed. */
    @Test
    fun `a review that throws after the page began closing says nothing`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val throwing = object : FoodReviewer {
            override suspend fun review(request: ReviewRequest): ReviewResult {
                gate.await()
                throw IllegalStateException("no network stack")
            }
        }
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = throwing)
        viewModel.review()
        advanceUntilIdle()

        viewModel.beginJoining()
        advanceUntilIdle()
        val waiting = viewModel.state.value.editing!!.reviewing
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.failed).isNull()
        assertThat(viewModel.state.value.refusal).isNull()
        assertThat(viewModel.state.value.editing!!.reviewing).isEqualTo(waiting)
    }

    @Test
    fun `a review that changes nothing says so`() = runTest(dispatcher) {
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = FakeFoodReviewer())

        viewModel.review()
        advanceUntilIdle()

        val shown = viewModel.state.value.editing!!.reviewing.review as Review.Shown
        assertThat(shown.nothingSuggested).isTrue()
    }

    /** D54 §8.3: an answer that arrived and could not be used says so, not "could not be understood". */
    @Test
    fun `an answer whose every suggestion was set aside is said as unusable, not as a failure`() =
        runTest(dispatcher) {
            val answer = FoodReview(null, null, null, listOf(FactGroup.PER_100G))
            val viewModel = page(
                FakeFoodRepository(listOf(oatBiscuit())),
                reviewer = FakeFoodReviewer(ReviewResult.Unusable(answer)),
            )

            viewModel.review()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.refusal).isNull()
            assertThat(state.editing!!.reviewing.review).isEqualTo(Review.Shown(answer, unusable = true))
        }

    /** D54 §8.4: the model's answer reaches the page to be shown on request, and is never logged. */
    @Test
    fun `the model's answer reaches the page after an unreadable or unusable review, and is not logged`() =
        runTest(dispatcher) {
            val raw = """{"per_100g":{"kcal":500},"note":"Oat biscuit"}"""
            listOf(
                ReviewResult.Failed(EstimateResult.Unreadable("not the shape"), raw),
                ReviewResult.Unusable(FoodReview(null, null, null, listOf(FactGroup.PER_100G)), raw),
            ).forEach { answer ->
                val problems = RecordingProblemLog()
                val viewModel = page(
                    FakeFoodRepository(listOf(oatBiscuit())),
                    problems = problems,
                    reviewer = FakeFoodReviewer(answer),
                )

                viewModel.review()
                advanceUntilIdle()

                assertWithMessage("$answer").that(viewModel.state.value.editing!!.reviewing.modelAnswer)
                    .isEqualTo(raw)
                assertWithMessage("$answer").that(problems.recorded).isEmpty()
            }
        }

    /** D8: the way on is typing, and the form is exactly as he left it. */
    @Test
    fun `a review past the day's allowance says the existing sentence and leaves the form alone`() =
        runTest(dispatcher) {
            val viewModel = page(
                FakeFoodRepository(listOf(oatBiscuit())),
                reviewer = FakeFoodReviewer(ReviewResult.Failed(EstimateResult.CeilingReached)),
            )
            val opened = viewModel.state.value.editing!!.form

            viewModel.review()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.refusal).isNull()
            assertThat(state.editing!!.form).isEqualTo(opened)
            assertThat(state.editing!!.reviewing.review)
                .isEqualTo(Review.Failed(EstimateResult.CeilingReached))
        }

    /** A review writes nothing, so a thrown one says it could not open — and is not left asking. */
    @Test
    fun `a review that throws says it could not be opened and can be asked again`() =
        runTest(dispatcher) {
            val problems = RecordingProblemLog()
            val throwing = object : FoodReviewer {
                override suspend fun review(request: ReviewRequest): ReviewResult =
                    throw IllegalStateException("no network stack")
            }
            val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), problems = problems, reviewer = throwing)

            viewModel.review()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertThat(state.failed).isNull()
            assertThat(state.editing!!.reviewing.review).isEqualTo(Review.Failed(null))
            assertThat(state.editing!!.reviewing.asking).isFalse()
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
        }

    @Test
    fun `a review is not asked for a food with no name the form would take`() = runTest(dispatcher) {
        val reviewer = FakeFoodReviewer()
        val viewModel = page(FakeFoodRepository(listOf(oatBiscuit())), reviewer = reviewer)
        viewModel.setForm(viewModel.state.value.editing!!.form.copy(name = "  "))

        viewModel.review()
        advanceUntilIdle()

        assertThat(reviewer.requests).isEmpty()
    }

    /** Invented figures: D54's Oat biscuit. */
    private fun oatBiscuit() = aFood(
        name = "Oat biscuit",
        facts = FoodFacts(
            per100g = PerHundredGrams(Nutrients(480.0, 7.0, 62.0, 22.0), Provenance(Source.LABEL, null, 0)),
            perUnit = PerUnit("biscuit", Nutrients(90.0, 1.0, 12.0, 1.0), Provenance(Source.TYPED, null, 0)),
            gramsPerUnit = GramsPerUnit(18.0, Provenance(Source.TYPED, null, 0)),
        ),
    )

    /** Per biscuit's fat 1 → 4 with its reason, the rest kept (D54 §3's invented example). */
    private fun fatChanged() = ReviewResult.Proposed(
        FoodReview(per100g = null, perUnit = biscuitSuggestion(), note = null, setAside = emptyList()),
    )

    /** As [fatChanged], and per 100 g's calories 480 → 470 too — invented, to have two groups. */
    private fun bothChanged() = ReviewResult.Proposed(
        FoodReview(
            per100g = Suggestion(
                nutrients = Nutrients(470.0, 7.0, 62.0, 22.0),
                confidence = Confidence.LOW,
                filled = false,
                changes = listOf(FigureChange(Figure.KCAL, 480.0, 470.0, "The macros give about 470.")),
                reason = null,
            ),
            perUnit = biscuitSuggestion(),
            note = null,
            setAside = emptyList(),
        ),
    )

    private fun biscuitSuggestion() = Suggestion(
        nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
        confidence = Confidence.MEDIUM,
        filled = false,
        changes = listOf(
            FigureChange(Figure.FAT, 1.0, 4.0, "18 g of a food with 22 g of fat per 100 g holds about 4 g."),
        ),
        reason = null,
    )

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
    }

    /**
     * The page for one food, its state collected — the state only flows while something collects
     * it, and the form is filled from what flows.
     */
    private fun TestScope.page(
        foods: FoodRepository,
        foodId: Long = 1,
        problems: ProblemLog = ProblemLog.NONE,
        reviewer: FoodReviewer = FakeFoodReviewer(),
    ): FoodPageViewModel {
        val viewModel = FoodPageViewModel(
            foods,
            Now { 1_000 },
            problems,
            reviewer,
            SavedStateHandle(mapOf("foodId" to foodId)),
        )
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()
        return viewModel
    }
}
