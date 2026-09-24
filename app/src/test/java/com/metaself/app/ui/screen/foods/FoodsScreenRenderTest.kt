package com.metaself.app.ui.screen.foods

import com.google.common.truth.Truth.assertThat
import com.metaself.app.R
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.weighing
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.food.FormReview
import com.metaself.app.ui.food.Review
import com.metaself.app.ui.food.ReviewActions
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The screen where the food list is put right.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`. The two annotations
 * look identical at the call site and the wrong one produces a test that silently never runs.
 */
@RunWith(RobolectricTestRunner::class)
class FoodsScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `every food shows what it knows about itself`() {
        val texts = draw(
            FoodsUiState(
                foods = listOf(
                    aFood(
                        name = "Protein bar",
                        facts = FoodFacts(
                            per100g = aPer100g(kcal = 422.0),
                            perUnit = aPerUnit("bar", 190.0),
                            gramsPerUnit = weighing(45.0),
                        ),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("Protein bar")
        assertThat(texts).contains("422 kcal per 100 g")
        assertThat(texts).contains("190 kcal per bar")
        assertThat(texts).contains("one bar is 45 g")
    }

    /** The other name a merge left behind, which is the whole reason merging is worth doing. */
    @Test
    fun `a merged food shows the other name it answers to`() {
        val texts = draw(
            FoodsUiState(
                foods = listOf(aFood(name = "Yoghurt").copy(alsoKnownAs = listOf("יוגורט"))),
            ),
        )

        assertThat(texts.any { it.contains("יוגורט") }).isTrue()
    }

    /**
     * The count is derived, so it falls as he fixes them. A number written down once by the
     * conversion would have gone stale the first time he corrected one.
     */
    @Test
    fun `the foods that only know a portion are offered as a list to work through`() {
        val texts = draw(FoodsUiState(foods = listOf(aFood()), onlyAPortionCount = 11))

        assertThat(texts.any { it.contains("Only know a portion (11)") }).isTrue()
    }

    @Test
    fun `working through them to the end says so`() {
        val texts = draw(
            FoodsUiState(foods = emptyList(), onlyPortions = true, onlyAPortionCount = 0),
        )

        assertThat(texts.any { it.startsWith("Nothing left on this list") }).isTrue()
    }

    /**
     * Shown and never reconciled. The app cannot know which of the three facts is the wrong one, and
     * choosing would be exactly the silent guess this model exists to avoid.
     */
    @Test
    fun `a food whose own facts disagree says so rather than picking one`() {
        val texts = draw(
            FoodsUiState(
                foods = listOf(
                    aFood(
                        name = "Protein bar",
                        facts = FoodFacts(
                            per100g = aPer100g(kcal = 422.0),
                            perUnit = aPerUnit("bar", 260.0),
                            gramsPerUnit = weighing(45.0),
                        ),
                    ),
                ),
            ),
        )

        assertThat(texts.any { it.contains("disagree by about") }).isTrue()
    }

    // --- The editor ------------------------------------------------------------------------------

    @Test
    fun `opening a food shows every number it holds, ready to change`() {
        val food = aFood(
            name = "Yoghurt",
            facts = FoodFacts(per100g = aPer100g(kcal = 72.0)),
        ).copy(id = 1)

        val texts = draw(
            FoodsUiState(
                foods = listOf(food),
                editing = Editing(foodId = 1, form = FoodForm.of(food)),
            ),
        )

        assertThat(texts).contains("Yoghurt")
        assertThat(texts).contains("72")
        assertThat(texts).contains("What 100 g of it are worth")
        assertThat(texts).contains("What one of it is worth")
        assertThat(texts).contains("What one of it weighs")
    }

    /**
     * Where a number came from, beside the number he is about to overwrite. A figure off a packet
     * deserves more hesitation than one a model guessed, and only the record can say which it is.
     */
    @Test
    fun `the editor says where each number came from`() {
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
            FoodsUiState(foods = listOf(food), editing = Editing(1, FoodForm.of(food))),
        )

        assertThat(texts.any { it.contains("a rough estimate") }).isTrue()
    }

    /**
     * His own decision, restated where he is about to act on it: correcting fixes the food from now
     * on, and the day that was wrong stays wrong.
     */
    @Test
    fun `the editor says plainly that a correction does not reach backwards`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            FoodsUiState(foods = listOf(food), editing = Editing(1, FoodForm.of(food))),
        )

        assertThat(texts.any { it.contains("Days you have already logged keep the numbers") })
            .isTrue()
    }

    /** Nothing computes what one of something weighs, and the screen says so where it is typed. */
    @Test
    fun `the editor says that nothing works out what one of it weighs`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            FoodsUiState(foods = listOf(food), editing = Editing(1, FoodForm.of(food))),
        )

        assertThat(texts.any { it.contains("Nothing works this out for you") }).isTrue()
    }

    /** Putting a brand on a food changes what it is. Said before he does it, not after. */
    @Test
    fun `the editor warns that a brand splits a food`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            FoodsUiState(foods = listOf(food), editing = Editing(1, FoodForm.of(food))),
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
    fun `the editor says decimals are kept, and never says whole grams`() {
        val food = aFood("Yoghurt", FoodFacts(per100g = aPer100g(kcal = 72.0))).copy(id = 1)

        val texts = draw(
            FoodsUiState(foods = listOf(food), editing = Editing(1, FoodForm.of(food))),
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
            FoodsUiState(
                foods = listOf(food),
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
            FoodsUiState(
                foods = listOf(food),
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

    // --- Refusals and merging ----------------------------------------------------------------------

    /** A refusal names what stands in the way, so it is a next step rather than a dead end. */
    @Test
    fun `a refusal names the meals standing in the way`() {
        val texts = draw(
            FoodsUiState(
                foods = listOf(aFood()),
                refusal = "“Vegetable salad” uses this. Change the meal, or hide this instead.",
            ),
        )

        assertThat(texts.any { it.contains("Vegetable salad") }).isTrue()
    }

    /** Which one survives is not left to be guessed at: a merge cannot be undone. */
    @Test
    fun `merging says which food is the one that stays`() {
        val yoghurt = aFood(name = "Yoghurt").copy(id = 1)
        val hebrew = aFood(name = "יוגורט").copy(id = 2)

        val texts = draw(
            FoodsUiState(foods = listOf(yoghurt, hebrew), merging = Merging(keeping = yoghurt)),
        )

        assertThat(texts.any { it.contains("Pick the food that is the same thing as") }).isTrue()
        assertThat(texts).contains("This is the one that stays")
    }

    /**
     * Picking which food a duplicate is, is the one decision on this screen that cannot be undone,
     * and the brand and the numbers are what tell two duplicates apart. So each row he is picking
     * from shows everything an ordinary row does — the one staying as well as the ones he may pick.
     */
    @Test
    fun `while picking the duplicate every row still shows its brand and what it knows`() {
        val yoghurt = aFood(name = "Yoghurt", facts = FoodFacts(per100g = aPer100g(kcal = 60.0)))
            .copy(id = 1, brand = "Dairyco")
        val other = aFood(name = "Yoghurt 3%", facts = FoodFacts(per100g = aPer100g(kcal = 90.0)))
            .copy(id = 2, brand = "Milkworks")

        val texts = draw(
            FoodsUiState(foods = listOf(yoghurt, other), merging = Merging(keeping = yoghurt)),
        )

        assertThat(texts).contains("Dairyco")
        assertThat(texts).contains("60 kcal per 100 g")
        assertThat(texts).contains("Milkworks")
        assertThat(texts).contains("90 kcal per 100 g")
        assertThat(texts).contains("This is the one that stays")
    }

    /**
     * Joining the two he ticked is a different question from joining one to a food not yet picked:
     * both are settled, so the screen names both, says which survives, and asks — and the list stops
     * being a picker, because with the pair already known a stray tap on a row could only join the
     * wrong two, irreversibly.
     */
    @Test
    fun `joining two chosen foods names both and does not turn the list into a picker`() {
        val yoghurt = aFood(name = "Yoghurt").copy(id = 1)
        val hebrew = aFood(name = "יוגורט").copy(id = 2)

        val texts = draw(
            FoodsUiState(
                foods = listOf(yoghurt, hebrew),
                chosen = setOf(1L, 2L),
                merging = Merging(keeping = yoghurt, losing = hebrew),
            ),
        )

        assertThat(texts.any { it.contains("Joining “יוגורט” into “Yoghurt”") }).isTrue()
        assertThat(texts.any { it.contains("cannot be undone") }).isTrue()
        assertThat(texts).contains("Join them")
        assertThat(texts).doesNotContain("This is the one that stays")
    }

    @Test
    fun `hiding is described as the gentler of the two`() {
        val food = aFood().copy(id = 1)

        val texts = draw(
            FoodsUiState(foods = listOf(food), editing = Editing(1, FoodForm.of(food))),
        )

        assertThat(texts).contains("Hide")
        assertThat(texts).contains("Delete")
        assertThat(texts.any { it.contains("every past day still shows its name") }).isTrue()
    }

    // --- Deleting asks first (D36, issue #16) ---------------------------------------------------

    /**
     * The question takes the place of the buttons it came from, so exactly one thing on screen says
     * Delete — the answer — and no other button in the editor can be pressed past it.
     */
    @Test
    fun `while asking, the editor offers only the answer`() {
        val yoghurt = aFood(name = "Yoghurt").copy(id = 1)

        val texts = draw(
            FoodsUiState(
                foods = listOf(yoghurt),
                editing = Editing(1, FoodForm.of(yoghurt)),
                deleting = Deleting.Asking(yoghurt),
            ),
        )

        assertThat(texts).contains("Delete “Yoghurt”? This cannot be undone.")
        assertThat(texts).contains("Keep it")
        assertThat(texts).doesNotContain("Join with a duplicate")
        assertThat(texts).doesNotContain("Hide")
        assertThat(texts).doesNotContain("Save")
    }

    @Test
    fun `a food not being asked about draws no question`() {
        val yoghurt = aFood(name = "Yoghurt").copy(id = 1)

        val texts = draw(
            FoodsUiState(foods = listOf(yoghurt), editing = Editing(1, FoodForm.of(yoghurt))),
        )

        assertThat(texts.none { it.contains("cannot be undone") }).isTrue()
        assertThat(texts).contains("Delete")
    }

    /**
     * Drawn in the editor, above its buttons, and not in the slot at the top of the list: Delete sits
     * at the foot of a long editor, and a refusal drawn up there would be off screen — the tap would
     * look dead. The buttons stay, because the sentence recommends Hide. With the screen-wide refusal
     * empty, the sentence can only have come from the editor.
     */
    @Test
    fun `a refusal to delete is drawn in the editor, with its buttons still there`() {
        val yoghurt = aFood(name = "Yoghurt").copy(id = 1)
        val sentence = "“Vegetable salad” uses this. Change the meal, or hide this instead."

        val texts = draw(
            FoodsUiState(
                foods = listOf(yoghurt),
                editing = Editing(1, FoodForm.of(yoghurt)),
                deleting = Deleting.Refused(yoghurt, sentence),
                refusal = null,
            ),
        )

        assertThat(texts).contains(sentence)
        assertThat(texts).contains("Hide")
        assertThat(texts).contains("Delete")
        assertThat(texts).contains("Save")
        assertThat(texts.none { it.contains("cannot be undone") }).isTrue()
        // The top-of-list refusal's own dismiss button: its presence would mean the wrong slot.
        assertThat(texts).doesNotContain("All right")
    }

    /**
     * A Save or a Delete that threw, on a food far down the list, is said in its editor beside the
     * buttons he pressed. At the top of the list it would be off screen, and the tap would look dead.
     */
    @Test
    fun `a failure while a food is open is drawn in its editor, not at the top of the list`() {
        val foods = threeFoods()
        val olive = foods.last()

        val texts = draw(
            FoodsUiState(
                foods = foods,
                editing = Editing(olive.id, FoodForm.of(olive)),
                failed = ActionRefused.NOTHING_CHANGED,
            ),
        )

        assertThat(texts).contains(NOTHING_CHANGED)
        // After the rows above the open food, and before the button that failed.
        assertThat(render.isDrawnBefore("Tomato", NOTHING_CHANGED)).isTrue()
        assertThat(render.isDrawnBefore(NOTHING_CHANGED, "Save")).isTrue()
        // Once, and dismissible where it is.
        assertThat(texts.count { it == NOTHING_CHANGED }).isEqualTo(1)
        assertThat(texts).contains("All right")
    }

    /** With nothing open there is nowhere nearer to say it, so it stays at the top. */
    @Test
    fun `a failure with no food open is drawn at the top of the list`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), failed = ActionRefused.NOTHING_CHANGED))

        assertThat(texts).contains(NOTHING_CHANGED)
        assertThat(render.isDrawnBefore(NOTHING_CHANGED, "Cucumber")).isTrue()
    }

    @Test
    fun `a hidden food says it is hidden`() {
        val texts = draw(FoodsUiState(foods = listOf(aFood().copy(hidden = true))))

        assertThat(texts.any { it.startsWith("Hidden") }).isTrue()
    }

    @Test
    fun `an empty list says so rather than showing an empty screen`() {
        val texts = draw(FoodsUiState())

        assertThat(texts.any { it.startsWith("No foods yet") }).isTrue()
    }

    @Test
    fun `a search with no matches says so`() {
        val texts = draw(FoodsUiState(query = "shakshuka", foods = emptyList()))

        assertThat(texts.any { it.contains("Nothing matches") }).isTrue()
    }

    // --- Choosing several foods at once ---------------------------------------------------------

    private fun threeFoods() = listOf(
        aFood(name = "Cucumber").copy(id = 1),
        aFood(name = "Tomato").copy(id = 2),
        aFood(name = "Olive oil").copy(id = 3),
    )

    /**
     * The tick is a checkbox, and a checkbox has no words of its own: the only text it contributes
     * is what it says to a screen reader. So asserting on that is both the test of the tick and the
     * test that the tick is reachable without sight.
     */
    @Test
    fun `a tick is drawn against a chosen food`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L)))

        assertThat(texts).contains("Chosen")
        assertThat(texts).contains("Not chosen")
    }

    /** A count replaces the title, so the list says what it is in the middle of. */
    @Test
    fun `the title says how many are chosen`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L, 2L)))

        assertThat(texts).contains("2 chosen")
    }

    @Test
    fun `the action says how many the meal would be made from`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L, 2L, 3L)))

        assertThat(texts).contains("Make a meal from these 3")
    }

    /**
     * The same gesture serves merging, which is currently the best-hidden thing in the app — and it
     * is pairwise, so it is offered at two and at no other number.
     */
    @Test
    fun `with exactly two chosen joining them into one food is offered as well`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L, 2L)))

        assertThat(texts).contains("Make a meal from these 2")
        assertThat(texts).contains("Join these two into one food")
    }

    @Test
    fun `with three chosen only the meal is offered`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L, 2L, 3L)))

        assertThat(texts).doesNotContain("Join these two into one food")
    }

    @Test
    fun `with nothing chosen neither action is drawn`() {
        val texts = draw(FoodsUiState(foods = threeFoods()))

        // "Make a meal from", not "…these": the one-form reads "this one", and an absence test
        // narrower than every form the action can take would pass with the action drawn.
        assertThat(texts.none { it.startsWith("Make a meal from") }).isTrue()
        assertThat(texts).doesNotContain("Join these two into one food")
    }

    /**
     * A meal is offered from two foods or more, so "these 1" cannot be drawn here — pinned, because
     * the resource now has a one-form and nothing else would notice this screen starting to use it.
     */
    @Test
    fun `with one food chosen no meal is offered, and nothing says "these 1"`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L)))

        assertThat(texts).contains("1 chosen")
        assertThat(texts.none { it.startsWith("Make a meal from") }).isTrue()
        assertThat(texts.none { it.contains("these 1") }).isTrue()
    }

    /**
     * Read straight from the resources, because this screen never draws the one-form (above) and
     * the day's is the same sentence. Plural resources rather than a test for 1 in code: Hebrew (#9)
     * has more forms than English and will translate these same entries.
     */
    @Test
    fun `the meal action's words read naturally at one and at many`() {
        val resources = RuntimeEnvironment.getApplication().resources

        assertThat(resources.getQuantityString(R.plurals.foods_make_meal, 1, 1))
            .isEqualTo("Make a meal from this one")
        assertThat(resources.getQuantityString(R.plurals.foods_make_meal, 3, 3))
            .isEqualTo("Make a meal from these 3")
        assertThat(resources.getQuantityString(R.plurals.day_make_meal, 1, 1))
            .isEqualTo("Make a meal from this one")
        assertThat(resources.getQuantityString(R.plurals.day_make_meal, 3, 3))
            .isEqualTo("Make a meal from these 3")
    }

    /** A gesture nothing on screen mentions is a gesture he will never find. */
    @Test
    fun `the list says how choosing is started`() {
        val texts = draw(FoodsUiState(foods = threeFoods()))

        assertThat(texts.any { it.contains("Hold a food to start choosing") }).isTrue()
    }

    /**
     * With one food ticked, the bar says how to tick more (public issue #12). The hint about
     * holding is gone by then, and nothing can be done with one food alone, so without this the
     * screen has no next step on it at all.
     */
    @Test
    fun `with one food chosen the bar says how to add more, and with two it does not`() {
        assertThat(draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L))))
            .contains("Tap another food to add it.")
        assertThat(draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L, 2L))))
            .doesNotContain("Tap another food to add it.")
    }

    @Test
    fun `choosing can be abandoned from the screen`() {
        val texts = draw(FoodsUiState(foods = threeFoods(), chosen = setOf(1L)))

        assertThat(texts).contains("Clear")
    }

    // --- A review (D54) ---------------------------------------------------------------------------

    /**
     * The suggestion is drawn beside the figures it would change — under the per-one heading and not
     * under per 100 g — and nothing of it is in any box until he accepts.
     */
    @Test
    fun `a review's change is drawn under the group it would change and in no box`() {
        val texts = draw(reviewing(Review.Shown(FoodReview(null, fatTo4, null, emptyList()))))

        val line = "Fat 1 → 4 g — A reason."
        assertThat(texts).contains(line)
        assertThat(texts.count { it == "Use these" }).isEqualTo(1)
        assertThat(render.isDrawnBefore("What 100 g of it are worth", "What one of it is worth"))
            .isTrue()
        assertThat(render.isDrawnBefore("What one of it is worth", line)).isTrue()
        assertThat(render.isDrawnBefore("What one of it is worth", "Use these")).isTrue()
        assertThat(render.isDrawnBefore("Use these", "What one of it weighs")).isTrue()
        assertThat(render.fieldTexts()).doesNotContain("4")
        assertThat(texts).doesNotContain("Use all")
        assertThat(texts).contains("Dismiss")
    }

    @Test
    fun `the button says what is sent, and reads Reviewing while it is out`() {
        val idle = draw(reviewing(null))

        assertThat(idle).contains("Review the figures")
        assertThat(idle).contains(SENDS)
        assertThat(render.isDrawnBefore("Review the figures", "What 100 g of it are worth")).isTrue()

        val asking = draw(reviewing(Review.Asking()))

        assertThat(asking).contains("Reviewing…")
        assertThat(asking).doesNotContain("Review the figures")
    }

    @Test
    fun `two suggestions offer Use all, and a note and a set-aside group are said under the button`() {
        val texts = draw(
            reviewing(
                Review.Shown(
                    FoodReview(
                        per100g = null,
                        perUnit = fatTo4,
                        note = "A short note.",
                        setAside = listOf(FactGroup.PER_100G),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("A short note.")
        assertThat(texts).contains("Its suggestion for per 100 g couldn't be used.")
        assertThat(texts).doesNotContain("Use all")

        val both = draw(
            reviewing(
                Review.Shown(
                    FoodReview(
                        per100g = fatTo4.copy(
                            changes = listOf(FigureChange(Figure.KCAL, 480.0, 470.0, "Another.")),
                        ),
                        perUnit = fatTo4,
                        note = null,
                        setAside = emptyList(),
                    ),
                ),
            ),
        )

        assertThat(both.count { it == "Use these" }).isEqualTo(2)
        assertThat(both).contains("Use all")
    }

    @Test
    fun `a review that changes nothing says so`() {
        val texts = draw(
            reviewing(Review.Shown(FoodReview(null, null, null, emptyList()), nothingSuggested = true)),
        )

        assertThat(texts).contains("No changes suggested.")
        assertThat(texts).doesNotContain("Use these")
    }

    /** An editor with the Oat biscuit open (invented figures) and [review] as its review. */
    private fun reviewing(review: Review?): FoodsUiState {
        val food = Food(
            id = 1,
            name = "Oat biscuit",
            facts = FoodFacts(
                per100g = PerHundredGrams(Nutrients(480.0, 7.0, 62.0, 22.0), Provenance(Source.LABEL, null, 0)),
                perUnit = aPerUnit("biscuit", 90.0).copy(nutrients = Nutrients(90.0, 1.0, 12.0, 1.0)),
                gramsPerUnit = weighing(18.0),
            ),
        )
        return FoodsUiState(
            foods = listOf(food),
            editing = Editing(1, FoodForm.of(food), reviewing = FormReview(review = review)),
        )
    }

    private val fatTo4 = Suggestion(
        nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
        confidence = Confidence.MEDIUM,
        filled = false,
        changes = listOf(FigureChange(Figure.FAT, 1.0, 4.0, "A reason.")),
        reason = null,
    )

    private fun draw(state: FoodsUiState): List<String> = render.texts {
        FoodsScreen(
            state = state,
            onSearch = {},
            onShowOnlyPortions = {},
            onShowHidden = {},
            onEdit = {},
            onSetForm = {},
            onSave = {},
            onCancelEditing = {},
            onHide = {},
            onUnhide = {},
            onDelete = {},
            onConfirmDeleting = {},
            onCancelDeleting = {},
            onBeginMerging = {},
            onMergeInto = {},
            onConfirmMerging = {},
            onCancelMerging = {},
            onDismissRefusal = {},
            review = ReviewActions.NONE,
            onBeginChoosing = {},
            onToggleChosen = {},
            onClearChoosing = {},
            onMakeMeal = {},
            onJoinChosen = {},
            onBack = {},
        )
    }

    private companion object {
        /**
         * Shared by the two food forms: this editor and the builder's. The packet-label form has its
         * own sentence, "…for this packet…" (D38), because "on this food" is not true there.
         */
        /** `R.string.action_refused_nothing_changed`, as the phone draws it. */
        const val NOTHING_CHANGED = "That didn't work, and nothing was changed. " +
            "What went wrong is under Settings → Recent problems."

        const val SENDS =
            "Sends this food's name, brand and figures, and where each came from, to the model, " +
                "with your key. Nothing else."

        const val DECIMALS_KEPT =
            "Numbers here can have a decimal point: 0.5 g is kept as 0.5 g on this food."
    }
}
