package com.metaself.app.ui.screen.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodUse
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.food.ReviewActions
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One food's own page (D55 §2), drawn from a state: what it holds and in what order.
 *
 * Nothing here about width or size — a render in this project has no real font and a 320 dp canvas
 * whatever it is asked for (`CLAUDE.md`); the one wrapping test narrows the canvas and asserts only
 * where the buttons sit relative to each other and to the edge. What typed boxes hold is read through
 * `EditableText`, which [ComposeRender] returns beside the labels. Every figure, name and count is
 * invented.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class FoodPageScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the page is headed by the food's name and the list row's summary`() {
        val texts = draw(opened(greekYoghurt()))

        assertThat(texts).contains("Greek yoghurt")
        assertThat(texts).containsAtLeast("No brand", "100 kcal per 100 g", "one cup is 150 g").inOrder()
        assertThat(render.isDrawnBefore("No brand", "Name")).isTrue()
    }

    @Test
    fun `the name and brand boxes open filled with what is stored`() {
        val texts = draw(opened(examplebrandOatBiscuit()))

        // Once in the heading and once in its box; the brand once in the summary and once in its.
        assertThat(texts.count { it == "Oat biscuit" }).isEqualTo(2)
        assertThat(texts.count { it == "Examplebrand" }).isEqualTo(2)
        assertThat(texts).containsAtLeast("Name", "Brand")
    }

    @Test
    fun `review the figures is offered with its small print, under the name and brand`() {
        val texts = draw(opened(greekYoghurt()))

        assertThat(texts).contains("Review the figures")
        assertThat(texts).contains(SENDS)
        assertThat(render.isDrawnBefore("Brand", "Review the figures")).isTrue()
        assertThat(render.isDrawnBefore("Review the figures", "What 100 g of it are worth")).isTrue()
    }

    /**
     * The two groups draw the same four labels, and a screen reader used to hear eight boxes by four
     * names (public issue #3). Each box now has a name of its own, on the box itself; the labels on
     * screen are unchanged.
     */
    @Test
    fun `each figure box has a name of its own, per 100 g and per the food's unit`() {
        val texts = draw(opened(greekYoghurt()))

        assertThat(texts.count { it == "Calories" }).isEqualTo(2)
        for (label in listOf("Calories", "Protein (g)", "Carbs (g)", "Fat (g)")) {
            for (name in listOf("$label per 100 g", "$label per cup")) {
                assertWithMessage(name).that(render.describedCount(name)).isEqualTo(1)
                assertWithMessage(name).that(render.describedIsField(name)).isTrue()
            }
        }
    }

    /** A food with no unit named yet: the heading's "one of it" is what the boxes are per. */
    @Test
    fun `a figure box per unit with no unit named is said per one`() {
        draw(opened(examplebrandOatBiscuit()))

        assertThat(render.describedCount("Calories per one")).isEqualTo(1)
        assertThat(render.describedCount("Calories per 100 g")).isEqualTo(1)
    }

    /** Today's origin words, in the drawn place (D55 open question 2, default): none for typed. */
    @Test
    fun `each group says where its figures came from, and a typed one says nothing`() {
        val texts = draw(opened(greekYoghurt()))

        assertThat(texts).containsAtLeast(
            "What 100 g of it are worth",
            "What one of it is worth",
            "What one of it weighs",
        ).inOrder()
        assertThat(texts.count { it == "This came off the packet" }).isEqualTo(2)
        assertThat(texts.count { it.startsWith("This came") }).isEqualTo(2)
    }

    @Test
    fun `the four figures and the unit and weight boxes are all there, filled`() {
        val texts = draw(opened(greekYoghurt()))

        assertThat(texts).containsAtLeast("100", "8", "4", "5", "cup", "150", "12", "6", "7.5", "150")
        assertThat(texts.count { it == "Calories" }).isEqualTo(2)
        assertThat(texts).contains("Grams")
    }

    @Test
    fun `save and leave it alone come after the groups and before where it is used`() {
        val texts = draw(opened(greekYoghurt(), FoodUse(logged = 24, savedMeals = emptyList())))

        assertThat(texts).containsAtLeast("Save", "Leave it alone")
        assertThat(render.isDrawnBefore("What one of it weighs", "Save")).isTrue()
        assertThat(render.isDrawnBefore("Save", "Where it's used")).isTrue()
    }

    @Test
    fun `where it is used counts the entries and names the saved meals`() {
        val texts = draw(
            opened(greekYoghurt(), FoodUse(logged = 24, savedMeals = listOf("Breakfast bowl", "Snack plate"))),
        )

        assertThat(texts).containsAtLeast(
            "Where it's used",
            "Logged 24 times",
            "in 2 saved meals:",
            "Breakfast bowl",
            "Snack plate",
        ).inOrder()
    }

    @Test
    fun `one entry is said as once, and a food in no meal leaves the meals part out`() {
        val texts = draw(opened(examplebrandOatBiscuit(), FoodUse(logged = 1, savedMeals = emptyList())))

        assertThat(texts).contains("Logged once")
        assertThat(texts.none { it.contains("saved meal") }).isTrue()
    }

    @Test
    fun `a food never logged says so`() {
        val texts = draw(opened(lentilSoup(), FoodUse(logged = 0, savedMeals = emptyList())))

        assertThat(texts).contains("Not logged yet")
        assertThat(texts.none { it.contains("saved meal") }).isTrue()
    }

    @Test
    fun `one saved meal is said in the singular`() {
        val texts = draw(opened(greekYoghurt(), FoodUse(logged = 3, savedMeals = listOf("Breakfast bowl"))))

        assertThat(texts).contains("in 1 saved meal:")
    }

    /** An exact count, grouped the way every other figure is grouped: no rounding, no cap. */
    @Test
    fun `a large count is written in full and grouped`() {
        val texts = draw(opened(greekYoghurt(), FoodUse(logged = 1_204, savedMeals = emptyList())))

        assertThat(texts).contains("Logged 1,204 times")
    }

    @Test
    fun `join, hide and delete are at the foot, with the line on hiding or deleting`() {
        val texts = draw(opened(greekYoghurt(), FoodUse(logged = 24, savedMeals = emptyList())))

        assertThat(texts).containsAtLeast("Join with a duplicate", "Hide", "Delete").inOrder()
        assertThat(texts.any { it.startsWith("Hiding keeps it off your lists") }).isTrue()
        assertThat(render.isDrawnBefore("Where it's used", "Join with a duplicate")).isTrue()
    }

    @Test
    fun `a hidden food says so and offers to show it again instead of hiding it`() {
        val texts = draw(opened(hamburgerBun()))

        assertThat(texts).contains("Hidden — not offered when you log something")
        assertThat(texts).contains("Show again")
        assertThat(texts).doesNotContain("Hide")
    }

    /** D36: the question takes the buttons' place, so exactly one thing on the page says Delete. */
    @Test
    fun `the delete question replaces the buttons`() {
        val food = greekYoghurt().copy(id = 1)
        val texts = draw(opened(food).copy(deleting = Deleting.Asking(food)))

        assertThat(texts).contains("Delete “Greek yoghurt”? This cannot be undone.")
        assertThat(texts).contains("Keep it")
        assertThat(texts).doesNotContain("Join with a duplicate")
        assertThat(texts).doesNotContain("Hide")
    }

    @Test
    fun `a refusal to delete is drawn directly above the buttons, which stay`() {
        val food = greekYoghurt().copy(id = 1)
        val sentence = "“Breakfast bowl” uses this. Change the meal, or hide this instead."
        val used = FoodUse(logged = 24, savedMeals = listOf("Breakfast bowl"))
        val texts = draw(opened(food, used).copy(deleting = Deleting.Refused(food, sentence)))

        assertThat(texts).contains(sentence)
        assertThat(texts).contains("Hide")
        assertThat(render.isDrawnBefore("Where it's used", sentence)).isTrue()
        assertThat(render.isDrawnBefore(sentence, "Join with a duplicate")).isTrue()
    }

    @Test
    fun `a refused Save or a failed action is said directly above Save`() {
        val refused = draw(opened(greekYoghurt()).copy(refusal = "“Hummus” is already a food."))
        assertThat(refused).contains("“Hummus” is already a food.")
        assertThat(render.isDrawnBefore("“Hummus” is already a food.", "Save")).isTrue()
        assertThat(render.isDrawnBefore("Leave it alone", "“Hummus” is already a food.")).isFalse()

        val failed = draw(opened(greekYoghurt()).copy(failed = ActionRefused.NOTHING_CHANGED))
        assertThat(failed).contains(NOTHING_CHANGED)
        assertThat(failed).contains("All right")
    }

    /** Before the first read there is nothing to draw a form for, and no form is drawn. */
    @Test
    fun `nothing is drawn before the food is read`() {
        val texts = draw(FoodPageUiState())

        assertThat(texts).contains("My foods")
        assertThat(texts).doesNotContain("Save")
    }

    // --- Moved with the editor from the list (D55): what the form says, and its refusals -------

    /**
     * Where a number came from, beside the number he is about to overwrite. A figure off a packet
     * deserves more hesitation than one a model guessed, and only the record can say which it is.
     */
    @Test
    fun `the page says where each number came from`() {
        val food = Food(
            id = 1,
            name = "Yoghurt",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(72.0, 4.0, 6.0, 2.0),
                    Provenance(Source.AI_ESTIMATE, Confidence.LOW, setAtMillis = 0),
                ),
            ),
        )

        val texts = draw(
            opened(food),
        )

        assertThat(texts.any { it.contains("a rough estimate") }).isTrue()
    }

    /**
     * His own decision, restated where he is about to act on it: correcting fixes the food from now
     * on, and the day that was wrong stays wrong.
     */
    @Test
    fun `the page says plainly that a correction does not reach backwards`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            opened(food),
        )

        assertThat(texts.any { it.contains("Days you have already logged keep the numbers") })
            .isTrue()
    }

    /** Nothing computes what one of something weighs, and the screen says so where it is typed. */
    @Test
    fun `the page says that nothing works out what one of it weighs`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            opened(food),
        )

        assertThat(texts.any { it.contains("Nothing works this out for you") }).isTrue()
    }

    /** Putting a brand on a food changes what it is. Said before he does it, not after. */
    @Test
    fun `the page warns that a brand splits a food`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            opened(food),
        )

        assertThat(texts.any { it.contains("makes it a different food") }).isTrue()
    }

    /**
     * A food's facts are stored as decimals and keep them, so this form says so — the opposite of
     * the entry editor's whole-grams rule, and as plainly (issue #18, D38). Saying "whole grams"
     * here would be false, and would teach him to round a packet's 0.5 g by hand on a form that
     * would have kept it. Said once, above the first group of numbers, covering both groups.
     */
    @Test
    fun `the page says decimals are kept, and never says whole grams`() {
        val food = aFood("Yoghurt", FoodFacts(per100g = aPer100g(kcal = 72.0))).copy(id = 1)

        val texts = draw(
            opened(food),
        )

        assertThat(texts.count { it == DECIMALS_KEPT }).isEqualTo(1)
        assertThat(texts.indexOf(DECIMALS_KEPT))
            .isLessThan(texts.indexOf("What 100 g of it are worth"))
        assertThat(texts.none { it.contains("whole grams") }).isTrue()
    }

    @Test
    fun `a food that would know nothing at all is refused, with the reason`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            FoodPageUiState(
                food = food,
                editing = Editing(
                    foodId = 1,
                    form = FoodForm(name = "Tahini"),
                    showErrors = true,
                ),
            ),
        )

        assertThat(
            texts.any { it.contains("has to know what 100 g of it are worth") },
        ).isTrue()
    }

    /**
     * 150 g of protein in 100 g of anything was saved before D42 (issue #32). The food form refuses
     * a group as one answer, so the refusal is the group's, once, under the per-100 g calories box —
     * where every refusal of that group has always been drawn — and it names every ceiling, since it
     * has to cover whichever of the four is wrong. The box keeps what he typed.
     */
    @Test
    fun `a per-100 g figure past its ceiling is refused once, under the group's calories box`() {
        val food = aFood(
            name = "Yoghurt",
            facts = FoodFacts(per100g = aPer100g(kcal = 72.0)),
        ).copy(id = 1)

        val texts = draw(
            FoodPageUiState(
                food = food,
                editing = Editing(
                    foodId = 1,
                    form = FoodForm.of(food).copy(proteinPer100g = "150"),
                    showErrors = true,
                ),
            ),
        )

        val refused = "All four per 100 g (at most 1000 kcal, and 110 g of protein, " +
            "carbohydrate or fat), or leave them all empty."
        val group = texts.indexOf("What 100 g of it are worth")
        val calories = texts.subList(group, texts.size).indexOf("Calories") + group
        val protein = texts.subList(group, texts.size).indexOf("Protein (g)") + group

        assertThat(texts.filter { it == refused }).hasSize(1)
        assertThat(texts.indexOf(refused)).isGreaterThan(calories)
        assertThat(texts.indexOf(refused)).isLessThan(protein)
        assertThat(texts).contains("150")
    }

    // --- A food counted in millilitres (D56) ----------------------------------------------------

    @Test
    fun `a food counted in ml is worth per 100 ml, on the page and in its head`() {
        val texts = draw(opened(oatDrink()))

        assertThat(texts).contains("What 100 ml of it are worth")
        assertThat(texts).doesNotContain("What one of it is worth")
        assertThat(texts).containsAtLeast("ml", "57", "2.9", "4.7", "3.6").inOrder()
        assertThat(texts).contains("57 kcal per 100 ml")
    }

    @Test
    fun `each figure box of a food counted in ml is said per 100 ml`() {
        draw(opened(oatDrink()))

        for (label in listOf("Calories", "Protein (g)", "Carbs (g)", "Fat (g)")) {
            assertWithMessage(label).that(render.describedCount("$label per 100 ml")).isEqualTo(1)
            assertWithMessage(label).that(render.describedCount("$label per ml")).isEqualTo(0)
        }
    }

    /** What a millilitre weighs is a density, which the app does not assume (D4); it is not asked. */
    @Test
    fun `a food counted in ml is not asked what one of it weighs`() {
        val texts = draw(opened(oatDrink()))

        assertThat(texts).doesNotContain("What one of it weighs")
        assertThat(texts).doesNotContain("Grams")
    }

    /** One typed before D56 is not deleted by the app: shown, with its value, to be cleared. */
    @Test
    fun `a stored weight on a food counted in ml stays on the page, with its value and why`() {
        val texts = draw(opened(oatDrinkWithAWeight()))

        assertThat(texts).contains("What one of it weighs")
        assertThat(texts).containsAtLeast("Grams", "1.03").inOrder()
        assertThat(texts).contains(WEIGHT_NOT_ASKED_ML)
        assertThat(texts).doesNotContain(WEIGHT_NEVER_GUESSED)
    }

    /**
     * A weight typed while the unit was a glass, then the unit changed to ml: Save would store it,
     * so the box stays in sight with what it holds and why it is not asked (D56, D4). Invented.
     */
    @Test
    fun `a weight typed before the unit became ml stays in sight`() {
        val glass = hamburgerBun().copy(id = 1, hidden = false)
        val form = FoodForm.of(glass).copy(unitName = "ml", gramsPerUnit = "250")
        val texts = draw(FoodPageUiState(food = glass, editing = Editing(foodId = 1, form = form)))

        assertThat(texts).contains("What one of it weighs")
        assertThat(texts).containsAtLeast("Grams", "250").inOrder()
        assertThat(texts).contains(WEIGHT_NOT_ASKED_ML)
    }

    /** A refused weight on a food now in ml: its refusal is drawn, never only in a hidden box. */
    @Test
    fun `a refused weight on a food now in ml is said where it can be seen`() {
        val glass = hamburgerBun().copy(id = 1, hidden = false)
        val form = FoodForm.of(glass).copy(unitName = "ml", gramsPerUnit = "abc")
        val texts = draw(
            FoodPageUiState(food = glass, editing = Editing(foodId = 1, form = form, showErrors = true)),
        )

        assertThat(texts.any { it.startsWith("What one of it weighs, in grams") }).isTrue()
    }

    // --- Counting it in ml in one tap (public issue #5) -----------------------------------------

    /** Per 100 g only, no unit named: the per-one boxes are empty, so the switch is offered. */
    @Test
    fun `a food with empty per one boxes offers counting it in ml`() {
        val texts = draw(opened(lentilSoup()))

        assertThat(texts).contains(COUNT_IN_ML)
        assertThat(texts).contains(UNIT_LABEL)
    }

    @Test
    fun `a unit other than ml with empty boxes offers it too`() {
        val soup = lentilSoup().copy(id = 1)
        val texts = draw(
            FoodPageUiState(food = soup, editing = Editing(foodId = 1, form = FoodForm.of(soup).copy(unitName = "bar"))),
        )

        assertThat(texts).contains(COUNT_IN_ML)
    }

    @Test
    fun `pressing it names ml in the unit box and the group becomes per 100 ml`() {
        val soup = lentilSoup().copy(id = 1)
        render.texts {
            var form by remember { mutableStateOf(FoodForm.of(soup)) }
            FoodPageScreen(
                state = FoodPageUiState(food = soup, editing = Editing(foodId = 1, form = form)),
                onSetForm = { form = it },
                onSave = {},
                onHide = {},
                onUnhide = {},
                onDelete = {},
                onConfirmDeleting = {},
                onCancelDeleting = {},
                onBeginJoining = {},
                onDismissRefusal = {},
                review = ReviewActions.NONE,
                onBack = {},
            )
        }

        render.click(COUNT_IN_ML)
        val after = render.textsAgain()

        assertThat(after).contains("What 100 ml of it are worth")
        assertThat(after).doesNotContain("What one of it is worth")
        assertThat(render.fieldTexts()).contains("ml")
        assertThat(after).doesNotContain(COUNT_IN_ML)
        // The per 100 g figures are not touched.
        assertThat(after).containsAtLeast("90", "5", "12", "2").inOrder()
    }

    @Test
    fun `a food already counted in ml is not offered it`() {
        val texts = draw(opened(oatDrink()))

        assertThat(texts).doesNotContain(COUNT_IN_ML)
    }

    /**
     * Figures typed per bun would silently become per 100 ml under a new unit, so with the per-one
     * boxes filled the switch is not offered; the unit box's label still says ml can be typed.
     */
    @Test
    fun `a food whose per one boxes hold figures is not offered it`() {
        val texts = draw(opened(hamburgerBun().copy(hidden = false)))

        assertThat(texts).doesNotContain(COUNT_IN_ML)
        assertThat(texts).contains(UNIT_LABEL)
    }

    // --- The food's own buttons wrap whole (Join · Hide · Delete) ------------------------------------

    /**
     * On a narrow phone the last of three buttons was squeezed into what the row had left, and its
     * label broke mid-word. A button that does not fit now goes onto the next line, whole. Robolectric
     * has no real font (`CLAUDE.md`), so a narrow canvas stands in for the phone and only relative
     * geometry is asserted: Delete sits below Join, and nothing reaches past the edge. The canvas is
     * tall as well, because these buttons are at the foot of a long page and a node scrolled out of
     * the window reports no bounds at all.
     */
    @Test
    @Config(qualifiers = "+w150dp-h8000dp")
    fun `join, hide and delete wrap onto another line rather than breaking a label`() {
        draw(opened(greekYoghurt()))

        assertThat(render.topDp("Delete")).isGreaterThan(render.topDp("Join with a duplicate"))
        assertThat(render.rightEdgeDp("Join with a duplicate")).isAtMost(render.canvasWidthDp)
        assertThat(render.rightEdgeDp("Hide")).isAtMost(render.canvasWidthDp)
        assertThat(render.rightEdgeDp("Delete")).isAtMost(render.canvasWidthDp)
    }

    private fun opened(food: Food, use: FoodUse? = null): FoodPageUiState {
        val stored = food.copy(id = 1)
        return FoodPageUiState(
            food = stored,
            use = use,
            editing = Editing(foodId = 1, form = FoodForm.of(stored)),
        )
    }

    private fun draw(state: FoodPageUiState): List<String> = render.texts {
        FoodPageScreen(
            state = state,
            onSetForm = {},
            onSave = {},
            onHide = {},
            onUnhide = {},
            onDelete = {},
            onConfirmDeleting = {},
            onCancelDeleting = {},
            onBeginJoining = {},
            onDismissRefusal = {},
            review = ReviewActions.NONE,
            onBack = {},
        )
    }

    private companion object {
        /** `R.string.foods_count_in_ml`. */
        const val COUNT_IN_ML = "Count it in ml"

        /** `R.string.foods_field_unit`. */
        const val UNIT_LABEL = "One what? A slice, an egg, or ml"

        /** `R.string.foods_weight_not_asked_ml`. */
        const val WEIGHT_NOT_ASKED_ML =
            "What a millilitre weighs depends on what is poured, so it is not asked of a food " +
                "counted in ml, and nothing here works it out. This figure stays until you clear it."

        /** `R.string.foods_weight_never_guessed`. */
        const val WEIGHT_NEVER_GUESSED =
            "Nothing works this out for you. It is what turns grams into units and back, so a " +
                "wrong one would follow into everything you log afterwards."

        const val DECIMALS_KEPT =
            "Numbers here can have a decimal point: 0.5 g is kept as 0.5 g on this food."

        const val SENDS =
            "Sends this food's name, brand and figures, and where each came from, to the model, " +
                "with your key. Nothing else."

        /** `R.string.action_refused_nothing_changed`, as the phone draws it. */
        const val NOTHING_CHANGED = "That didn't work, and nothing was changed. " +
            "What went wrong is under Settings → Recent problems."
    }
}
