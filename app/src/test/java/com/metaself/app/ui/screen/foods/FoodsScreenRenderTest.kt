package com.metaself.app.ui.screen.foods

import com.google.common.truth.Truth.assertThat
import com.metaself.app.R
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.weighing
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.screen.food.greekYoghurt
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The food list: finding, choosing and joining (D55 §1). A food opens its own page; what that page
 * draws is `FoodPageScreenRenderTest`'s.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`. The two annotations
 * look identical at the call site and the wrong one produces a test that silently never runs.
 */
@RunWith(RobolectricTestRunner::class)
class FoodsScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /**
     * One summary line under the name (D55 §1), in parts: the brand or No brand, the first way of
     * counting it knows, and what one weighs. The row gives less at a glance than it did: a food that
     * knows both ways of counting shows per 100 g, not its per-one calories — the page shows all.
     */
    @Test
    fun `every food shows its name and one summary line`() {
        val texts = draw(FoodsUiState(foods = listOf(greekYoghurt().copy(id = 1))))

        assertThat(texts).containsAtLeast(
            "Greek yoghurt",
            "No brand",
            "100 kcal per 100 g",
            "one cup is 150 g",
        ).inOrder()
        assertThat(texts).doesNotContain("150 kcal per cup")
    }

    /** A branded food that knows per one and what one weighs (invented figures and brand). */
    @Test
    fun `a branded food counted per one says its brand and per one`() {
        val texts = draw(
            FoodsUiState(
                foods = listOf(
                    aFood(
                        name = "Protein bar",
                        facts = FoodFacts(perUnit = aPerUnit("bar", 190.0), gramsPerUnit = weighing(45.0)),
                    ).copy(brand = "Examplebrand"),
                ),
            ),
        )

        assertThat(texts).containsAtLeast("Protein bar", "Examplebrand", "190 kcal per bar", "one bar is 45 g")
            .inOrder()
    }

    // --- A tap (D55 §1) -----------------------------------------------------------------------

    /** A tap opens the food's page; nothing is drawn in place of the row any more. */
    @Test
    fun `a tap on a food opens its page and draws nothing in place`() {
        var opened: Long? = null
        draw(FoodsUiState(foods = threeFoods()), onOpen = { opened = it })

        render.click("Tomato")
        val after = render.textsAgain()

        assertThat(opened).isEqualTo(2L)
        assertThat(after).doesNotContain("Save")
        assertThat(after).doesNotContain("Name")
    }

    /** While choosing, a tap ticks — it does not open the page. */
    @Test
    fun `while choosing a tap ticks and opens nothing`() {
        var opened: Long? = null
        var ticked: Long? = null
        draw(
            FoodsUiState(foods = threeFoods(), chosen = setOf(1L)),
            onOpen = { opened = it },
            onToggleChosen = { ticked = it },
        )

        render.click("Tomato")

        assertThat(ticked).isEqualTo(2L)
        assertThat(opened).isNull()
    }

    /** While picking a duplicate, a tap picks — it does not open the page. */
    @Test
    fun `while picking a duplicate a tap picks and opens nothing`() {
        val foods = threeFoods()
        var opened: Long? = null
        var picked: Long? = null
        draw(
            FoodsUiState(foods = foods, merging = Merging(keeping = foods[0])),
            onOpen = { opened = it },
            onMergeInto = { picked = it },
        )

        render.click("Tomato")

        assertThat(picked).isEqualTo(2L)
        assertThat(opened).isNull()
    }

    // --- A food hidden from its page (D55 §6) ----------------------------------------------------

    @Test
    fun `a food hidden from its page is said at the top, with Show again and All right`() {
        val hidden = greekYoghurt().copy(id = 1, hidden = true)
        var shown = 0
        var dismissed = 0
        val texts = draw(
            FoodsUiState(foods = threeFoods(), hid = hidden),
            onShowAgain = { shown++ },
            onDismissHidden = { dismissed++ },
        )

        val line = "“Greek yoghurt” is hidden — it is no longer offered when you log something."
        assertThat(texts).contains(line)
        assertThat(texts).containsAtLeast("Show again", "All right")
        assertThat(render.isDrawnBefore(line, "Search your foods")).isTrue()

        render.click("Show again")
        render.click("All right")
        assertThat(shown).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `no hidden line is drawn when nothing was hidden`() {
        val texts = draw(FoodsUiState(foods = threeFoods()))

        assertThat(texts.none { it.contains("is hidden —") }).isTrue()
        assertThat(texts).doesNotContain("Show again")
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

    /** Nothing is said in an editor on the list any more: a failure is always at the top. */
    @Test
    fun `a failure is drawn at the top of the list`() {
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

    private fun draw(
        state: FoodsUiState,
        onOpen: (Long) -> Unit = {},
        onMergeInto: (Long) -> Unit = {},
        onToggleChosen: (Long) -> Unit = {},
        onShowAgain: () -> Unit = {},
        onDismissHidden: () -> Unit = {},
    ): List<String> = render.texts {
        FoodsScreen(
            state = state,
            onSearch = {},
            onShowOnlyPortions = {},
            onShowHidden = {},
            onOpen = onOpen,
            onMergeInto = onMergeInto,
            onConfirmMerging = {},
            onCancelMerging = {},
            onDismissRefusal = {},
            onShowAgain = onShowAgain,
            onDismissHidden = onDismissHidden,
            onBeginChoosing = {},
            onToggleChosen = onToggleChosen,
            onClearChoosing = {},
            onMakeMeal = {},
            onJoinChosen = {},
            onBack = {},
        )
    }

    private companion object {
        /** `R.string.action_refused_nothing_changed`, as the phone draws it. */
        const val NOTHING_CHANGED = "That didn't work, and nothing was changed. " +
            "What went wrong is under Settings → Recent problems."
    }
}
