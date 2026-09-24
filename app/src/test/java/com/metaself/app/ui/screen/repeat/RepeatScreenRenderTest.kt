package com.metaself.app.ui.screen.repeat

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.weighing
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class RepeatScreenRenderTest {

    private val render = ComposeRender()

    /** What the describe fallback was called with, or null if it was never taken. */
    private var describedWith: String? = null

    /** The food "Give this a portion" was pressed for, or null if it never was. */
    private var portionFor: Long? = null

    /** The tab asked for, or null if none was. */
    private var shownTab: RepeatTab? = null

    /** Whether the adjuster's "Put something in" was pressed. */
    private var beganAdding = false

    /** The food picked from the adjuster's own search, or null if none was. */
    private var pickedToAdd: Long? = null

    /** Whether the adjuster's "Put it in" was pressed. */
    private var putIn = false

    @After
    fun tearDown() = render.dispose()

    /**
     * This branch returns early, before the search box is drawn. Without a way out, a fresh install
     * would open its front door onto a screen offering no way to log anything at all.
     */
    @Test
    fun `with nothing ever logged it still offers to describe a meal`() {
        val texts = draw(RepeatUiState())

        assertThat(texts.any { it.startsWith("Nothing to repeat yet") }).isTrue()
        assertThat(texts).contains("Describe a meal")
    }

    @Test
    fun `on a fresh install describing is what the offer leads to`() {
        draw(RepeatUiState())

        render.click("Describe a meal")

        // Nothing to carry: nothing was typed, because there was nothing to type into.
        assertThat(describedWith).isEqualTo("")
    }

    @Test
    fun `a search with no matches offers to describe what was typed`() {
        val texts = draw(RepeatUiState(query = "shakshuka", foods = emptyList(), meals = emptyList()))

        assertThat(texts.any { it.contains("shakshuka") && it.startsWith("Describe") }).isTrue()
    }

    /** The whole point of the issue: a miss costs one tap and no retyping. */
    @Test
    fun `the describe fallback carries the typed words`() {
        draw(RepeatUiState(query = "shakshuka", foods = emptyList(), meals = emptyList()))

        render.click("Describe")

        assertThat(describedWith).isEqualTo("shakshuka")
    }

    /** The offer is an addition beneath the sentence, never a replacement for it. */
    @Test
    fun `a search with no matches still says plainly that nothing matched`() {
        val texts = draw(RepeatUiState(query = "fish", foods = emptyList()))

        assertThat(texts.any { it.contains("Nothing you have logged matches") }).isTrue()
    }

    /** The fallback belongs to the miss, not to every screenful. */
    @Test
    fun `a search that matches is not cluttered with a describe offer`() {
        val texts = draw(RepeatUiState(query = "hummus", foods = someFoods()))

        assertThat(texts.any { it.startsWith("Describe") }).isFalse()
    }

    /**
     * The third dead end: no search typed, one list holding something, the list in front empty. The
     * tabs stay drawn above, so the list that does hold something is visibly one tap away.
     *
     * **The way onward has to be the right one for the list that is empty.** This test used to
     * assert that an empty MEALS tab offered describing a meal to the model, which is how the meal
     * builder came to be unreachable — the assertion locked the dead end in place. An empty meals
     * tab is not something to escape from; it is where building a meal is the obvious thing to do.
     */
    @Test
    fun `a tab with nothing on it still offers a way onward`() {
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                query = "",
                foods = someFoods(),
                meals = emptyList(),
            ),
        )

        assertThat(texts).contains("Build a meal")
        assertThat(texts).contains("My foods")
    }

    /**
     * The sentence about this list is about this list, so it stays. The OFFER does not: the owner
     * already has a hummus one tab away, and offering to describe one here would manufacture the
     * duplicate this screen exists to prevent.
     */
    @Test
    fun `a miss on this tab does not offer to describe what the other tab has`() {
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                query = "hummus",
                foods = someFoods(),
                meals = emptyList(),
            ),
        )

        assertThat(texts).contains("None of your meals matches “hummus”.")
        assertThat(texts.any { it.contains("Nothing you have logged matches") }).isFalse()
        assertThat(texts.any { it.startsWith("Describe") }).isFalse()
    }

    /**
     * A miss here while the other list holds the match: the screen already knows, so it says where
     * and takes him there — rather than leaving the owner to guess that the other tab is worth a look.
     */
    @Test
    fun `a miss on the meals tab offers the foods that matched`() {
        draw(RepeatUiState(tab = RepeatTab.MEALS, query = "hummus", foods = someFoods()))

        render.click("Found in your foods")

        assertThat(shownTab).isEqualTo(RepeatTab.FOODS)
    }

    @Test
    fun `a miss on the foods tab offers the meals that matched`() {
        val meal = SavedMeal(id = 1, name = "Salad", components = emptyList())
        draw(RepeatUiState(tab = RepeatTab.FOODS, query = "salad", meals = listOf(meal)))

        render.click("Found in your meals")

        assertThat(shownTab).isEqualTo(RepeatTab.MEALS)
    }

    /** Nothing to point at when neither list matched: describing is the offer there. */
    @Test
    fun `a miss on both lists points at neither`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, query = "fish"))

        assertThat(texts.any { it.startsWith("Found in your") }).isFalse()
    }

    /** A meal he built, listed by its own name and by what he put in it. */
    @Test
    fun `a meal he built is listed by its name and its parts`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(salad())))

        assertThat(texts).contains("Vegetable salad")
        assertThat(texts).contains("Cucumber — 100 g · 16 kcal")
        assertThat(texts).contains("Olive oil — 1 spoon · 119 kcal")
    }

    /**
     * Repeating a two-portion row showed only the meal's total, so there was no way to see that
     * the quantity had carried, and it read as though it had not. Every part still shows how much
     * of it there is.
     */
    @Test
    fun `each part shows how much of it there is, and the meal its total`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(salad())))

        assertThat(texts).contains("135 kcal")
    }

    /**
     * The note sits above both lists, and it used to say "One tap logs it" of both. True of a meal:
     * a tap puts it on the day as he built it. Not of a food: a tap asks how much first, because the
     * amount is the one thing the app will not fill in for him (D4). The note now says each (D37).
     */
    @Test
    fun `the note says a meal is one tap and a food asks how much first`() {
        val onMeals = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(salad())))
        assertThat(onMeals).contains(REPEAT_NOTE)
        assertThat(onMeals.none { it.contains("One tap logs it") }).isTrue()

        val onFoods = draw(
            RepeatUiState(tab = RepeatTab.FOODS, foods = someFoods(), meals = listOf(salad())),
        )
        assertThat(onFoods).contains(REPEAT_NOTE)
        assertThat(onFoods.none { it.contains("One tap logs it") }).isTrue()
    }

    @Test
    fun `it says plainly that nothing is sent`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(salad())))

        assertThat(texts.any { it.contains("Nothing is sent anywhere") }).isTrue()
    }

    @Test
    fun `a meal he built can be opened for one day only`() {
        assertThat(draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(salad()))))
            .contains("Adjust")
    }

    /**
     * Nothing done in the adjuster changes the meal, and the screen says so where he is about to do
     * it. Without that, dropping the oil looks like editing the salad.
     */
    @Test
    fun `adjusting says plainly that the meal itself does not change`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components),
            ),
        )

        assertThat(texts.any { it.contains("The meal itself stays as you built it") }).isTrue()
        assertThat(texts).contains("Log it")
    }

    // --- Putting something in, for today only (issue #10) --------------------------------------

    /** The adjuster could shrink and drop but never add; the way to add is now on it. */
    @Test
    fun `the adjuster offers to put something in`() {
        val salad = salad()
        draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components),
            ),
        )

        render.click("Put something in")

        assertThat(beganAdding).isTrue()
    }

    /**
     * Its own search, with his foods under it, and a food the meal already holds named rather than
     * offered. Log it steps aside while the step is open, so a food half-added is not left behind.
     */
    @Test
    fun `putting something in has its own search and names what the meal already holds`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(
                    asDefined = salad,
                    rows = salad.components,
                    finding = "c",
                    offered = listOf(slicedBread),
                    alreadyIn = listOf(weighedCucumber),
                ),
            ),
        )

        assertThat(texts).contains("Search your foods")
        assertThat(texts).contains("c")
        assertThat(texts).contains("Cucumber is already in this meal.")
        assertThat(texts).contains("Bread")
        assertThat(texts).contains("Not now")
        assertThat(texts).doesNotContain("Log it")
        assertThat(texts).doesNotContain("Leave it alone")

        render.click("Bread")
        assertThat(pickedToAdd).isEqualTo(slicedBread.id)
    }

    @Test
    fun `a search in the adjuster that finds nothing says so`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components, finding = "tahini"),
            ),
        )

        assertThat(texts).contains("Nothing matches “tahini”.")
    }

    /** The foods tab's own question, with the calories before it goes in, and its own buttons. */
    @Test
    fun `a food picked to put in asks how much, and puts it in`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(
                    asDefined = salad,
                    rows = salad.components,
                    finding = "",
                    adding = Choosing(index = -1, food = slicedBread, countedAs = CountedAs.UNITS, amount = "2"),
                ),
            ),
        )

        assertThat(texts).contains("How much")
        // Two slices at 80 kcal each.
        assertThat(texts).contains("160 kcal")
        assertThat(texts).contains("Not this one")
        assertThat(texts).doesNotContain("Log it")
        assertThat(render.isEnabled("Put it in")).isTrue()

        render.click("Put it in")
        assertThat(putIn).isTrue()
    }

    /** A half-built meal is offered like a finished one, and says what it is. */
    @Test
    fun `a meal with nothing in it yet says so`() {
        val texts = draw(
            RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(SavedMeal(id = 1, name = "Salad"))),
        )

        assertThat(texts.any { it.startsWith("Nothing in it yet") }).isTrue()
    }

    /**
     * A component whose food no longer knows what it is counted in cannot be costed, and the total
     * would quietly be short. Said rather than hidden.
     */
    @Test
    fun `a meal that can no longer cost itself says so`() {
        val bread = aFood(name = "Bread", facts = FoodFacts(per100g = aPer100g(250.0))).copy(id = 9)
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(
                    SavedMeal(
                        id = 1,
                        name = "Salad",
                        // Counted in slices by a food that only knows what 100 g are worth.
                        components = listOf(
                            MealComponent(id = 1, food = bread, amount = 2.0, countedAs = CountedAs.UNITS),
                        ),
                    ),
                ),
            ),
        )

        assertThat(texts.any { it.contains("no longer knows what it is counted in") }).isTrue()
        // Its amount and unit are known even though its worth is not, and the unit is the app's own
        // fallback word, so it takes the plural like any other part (#27).
        assertThat(texts).contains("Bread — 2 portions · ?")
    }

    // --- "2 portion" on a meal's parts (D37, #27) -------------------------------------------------

    /**
     * A part stores an amount and what it is counted in, and no words: the words are built here, so
     * they are the app's own by construction and take the plural directly. Every other unit is drawn
     * as it always was — the app does not know the plural of a word it did not choose.
     */
    @Test
    fun `a saved meal's part of two portions reads as portions`() {
        val meal = SavedMeal(
            id = 1,
            name = "Stew night",
            components = listOf(
                MealComponent(20, stew, 2.0, CountedAs.UNITS, position = 0),
                MealComponent(21, slicedBread, 2.0, CountedAs.UNITS, position = 1),
                MealComponent(22, weighedCucumber, 100.0, CountedAs.GRAMS, position = 2),
            ),
        )

        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(meal)))

        assertThat(texts).contains("Leftover stew — 2 portions · 600 kcal")
        assertThat(texts).contains("Bread — 2 slice · 160 kcal")
        assertThat(texts).contains("Cucumber — 100 g · 16 kcal")
        assertThat(texts.none { it.contains("2 portion ·") }).isTrue()
    }

    @Test
    fun `a saved meal's part of one portion reads as one portion`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(stewNight(1.0))))

        assertThat(texts).contains("Leftover stew — 1 portion · 300 kcal")
        assertThat(texts.none { it.contains("1 portions") }).isTrue()
    }

    /** Just for today changes the amount only; the part is drawn by the same rule as the list. */
    @Test
    fun `a part adjusted to two portions reads as portions`() {
        val asDefined = stewNight(1.0)
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(asDefined),
                adjusting = Adjusting(
                    asDefined = asDefined,
                    rows = listOf(asDefined.components.single().copy(amount = 2.0)),
                ),
            ),
        )

        assertThat(texts).contains("Leftover stew — 2 portions · 600 kcal")
        assertThat(texts.none { it.contains("2 portion ·") }).isTrue()
    }

    /** More on one portion makes a part-portion, which takes the plural, never the singular. */
    @Test
    fun `a part made more in the adjuster reads as part-portions`() {
        val asDefined = stewNight(1.0)
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(asDefined),
                adjusting = Adjusting(
                    asDefined = asDefined,
                    rows = listOf(asDefined.components.single().copy(amount = 1.5)),
                ),
            ),
        )

        assertThat(texts).contains("Leftover stew — 1.5 portions · 450 kcal")
    }

    /**
     * The same, reached by pressing + rather than by setting the amount: the test above would still
     * pass if + stopped asking for a step, or asked about the wrong part. The screen holds no state
     * of its own, so the step is put back the way the view model puts it back, and drawn again.
     */
    @Test
    fun `pressing plus on one portion reads as portions`() {
        val asDefined = stewNight(1.0)
        fun adjusted(rows: List<MealComponent>) = RepeatUiState(
            tab = RepeatTab.MEALS,
            meals = listOf(asDefined),
            adjusting = Adjusting(asDefined = asDefined, rows = rows),
        )
        var asked: Pair<Long, Int>? = null

        val before = draw(adjusted(asDefined.components), onStepComponent = { id, by -> asked = id to by })
        assertThat(before).contains("Leftover stew — 1 portion · 300 kcal")
        render.click("+")

        val (id, by) = checkNotNull(asked) { "+ asked for no step" }
        assertThat(by).isEqualTo(1)
        val after = draw(
            adjusted(asDefined.components.map { if (it.id == id) it.copy(amount = it.amount + by) else it }),
        )
        assertThat(after).contains("Leftover stew — 2 portions · 600 kcal")
    }

    // --- Just for today takes a typed amount (D53 §6) --------------------------------------------

    /** The three proportions are gone: each part is a box holding the meal's own number. */
    @Test
    fun `just for today offers a typed amount, not less, as it was or more`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components),
            ),
        )

        // The boxes: 100 (g of cucumber) and 1 (spoon of oil), in text fields, each with its unit
        // beside it — typed, not chosen from proportions.
        assertThat(render.fieldTexts()).containsAtLeast("100", "1").inOrder()
        assertThat(texts).containsAtLeast("g", "spoon")
        // − and + for the counted part only.
        assertThat(texts.count { it == "+" }).isEqualTo(1)
        assertThat(texts.count { it == "−" }).isEqualTo(1)
    }

    /**
     * Each part's box, its − and +, and its Remove are drawn once per part; a screen reader heard
     * every copy by the same word (public issue #3). Each now says which part it is for.
     */
    @Test
    fun `just for today names the part each box and button is for`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components),
            ),
        )

        assertThat(texts.count { it == "Remove" }).isEqualTo(2)
        for (name in listOf("Cucumber", "Olive oil")) {
            assertThat(render.describedCount("Remove $name")).isEqualTo(1)
            assertThat(render.describedCount("How much of $name")).isEqualTo(1)
            assertThat(render.describedIsField("How much of $name")).isTrue()
        }
        // − and + only for the counted part.
        assertThat(render.describedCount("One less of Olive oil")).isEqualTo(1)
        assertThat(render.describedCount("One more of Olive oil")).isEqualTo(1)
    }

    /** A blank box leaves Log it off and names the part that needs an amount. */
    @Test
    fun `a part with no amount is named and nothing can be logged`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components, typed = mapOf(11L to "")),
            ),
        )

        assertThat(texts).contains("Say how much Olive oil was to save this.")
        assertThat(render.isEnabled("Log it")).isFalse()
    }

    @Test
    fun `a part past the ceiling says so under its box`() {
        val salad = salad()
        val texts = draw(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                meals = listOf(salad),
                adjusting = Adjusting(asDefined = salad, rows = salad.components, typed = mapOf(11L to "101")),
            ),
        )

        assertThat(texts).contains("At most 100 at a time.")
        assertThat(render.isEnabled("Log it")).isFalse()
    }

    /** The meals tab is his own meals now, and offers the way to make one. */
    @Test
    fun `the meals tab offers building one`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, meals = listOf(salad())))

        assertThat(texts).contains("Build a meal")
    }

    /**
     * **The case that matters, and the one the test above does not cover.** The meals list is empty
     * until he builds a meal, so this is what he sees EVERY time until he does — and it used to
     * return before the button that starts one, offering to describe a meal to the model instead.
     * The builder was unreachable: the only way to get a meal was to already have one.
     */
    @Test
    fun `an empty meals tab offers building one, which is the only way to get a first meal`() {
        val texts = draw(RepeatUiState(tab = RepeatTab.MEALS, foods = someFoods(), meals = emptyList()))

        assertThat(texts).contains("Build a meal")
        assertThat(texts.any { it.startsWith("No meals yet") }).isTrue()
    }

    /** Describing still belongs to the foods side, where a miss really does mean a new food. */
    @Test
    fun `an empty foods tab still offers describing`() {
        val texts = draw(
            RepeatUiState(tab = RepeatTab.FOODS, foods = emptyList(), meals = listOf(salad())),
        )

        assertThat(texts).contains("Describe a meal")
    }

    /**
     * The list is the owner's foods now, each one once, saying what it knows about itself rather
     * than what one past morning happened to be.
     */
    @Test
    fun `my foods lists the saved foods, each saying what it knows`() {
        val texts = draw(
            RepeatUiState(
                foods = listOf(
                    aFood(name = "יוגורט", facts = FoodFacts(per100g = aPer100g(kcal = 72.0))),
                    aFood(name = "לחם", facts = FoodFacts(perUnit = aPerUnit("slice", 80.0))),
                ),
            ),
        )

        assertThat(texts).contains("יוגורט")
        assertThat(texts).contains("72 kcal per 100 g")
        assertThat(texts).contains("לחם")
        assertThat(texts).contains("80 kcal per slice")
    }

    /**
     * A food may now have two things to say about itself rather than one of two possible things.
     * Both are shown, as separate lines: joining a Hebrew name to a Latin figure lets the
     * bidirectional algorithm reorder them.
     */
    @Test
    fun `a food that knows both ways says both`() {
        val texts = draw(
            RepeatUiState(
                foods = listOf(
                    aFood(
                        name = "Protein bar",
                        facts = FoodFacts(
                            per100g = aPer100g(kcal = 422.0),
                            perUnit = aPerUnit("bar", 190.0),
                        ),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("422 kcal per 100 g")
        assertThat(texts).contains("190 kcal per bar")
    }

    @Test
    fun `both lists are offered by name`() {
        val texts = draw(RepeatUiState(foods = someFoods()))

        assertThat(texts).contains("My foods")
        assertThat(texts).contains("My meals")
    }

    @Test
    fun `before anything is logged there are no tabs to offer`() {
        val texts = draw(RepeatUiState())

        assertThat(texts.any { it.startsWith("Nothing to repeat yet") }).isTrue()
        assertThat(texts).doesNotContain("My foods")
    }

    private fun someFoods() = listOf(aFood(name = "Hummus"))

    /** Picking a food asks how much, because a food has no amount of its own to repeat. */
    @Test
    fun `picking a food asks how much of it`() {
        val texts = draw(
            RepeatUiState(
                foods = someFoods(),
                choosing = Choosing(index = 0, food = someFoods().single(), countedAs = CountedAs.GRAMS),
            ),
        )

        assertThat(texts).contains("How much")
        assertThat(texts).contains("Log it")
    }

    /**
     * A food renamed in the editor so the search no longer finds it keeps its question open, at no
     * row of the list. It is drawn above the list, and every row the search does find is drawn as
     * itself — never replaced by a question about a different food.
     */
    @Test
    fun `a question about a food the search no longer finds is drawn above the list, not in a row`() {
        val porridge = aFood(name = "Porridge")
        val texts = draw(
            RepeatUiState(
                query = "rice",
                foods = listOf(aFood(name = "Rice cake")),
                choosing = Choosing(index = -1, food = porridge, countedAs = CountedAs.GRAMS),
            ),
        )

        assertThat(texts).contains("How much")
        assertThat(texts).contains("Porridge")
        assertThat(texts).contains("Rice cake")
        assertThat(render.isDrawnBefore("Porridge", "Rice cake")).isTrue()
    }

    /**
     * **Nothing is guessed, and the owner is told so where the field would be.** A way of counting
     * the food does not support is shown with its reason rather than quietly missing, because a
     * field that is simply absent looks like a fault in the app.
     */
    @Test
    fun `a way of counting that is not available says why`() {
        val food = aFood(name = "Bread", facts = FoodFacts(perUnit = aPerUnit("slice", 80.0)))

        val texts = draw(
            RepeatUiState(
                foods = listOf(food),
                choosing = Choosing(index = 0, food = food, countedAs = CountedAs.UNITS),
            ),
        )

        assertThat(texts).contains("Weigh it: nothing knows what one slice weighs")
    }

    /**
     * A food with no named portion: counting is off, it looks off, the reason names the option it
     * belongs to, and the way to fix it is offered on the spot rather than three screens away.
     */
    @Test
    fun `a food with no portion offers to give it one, for that food`() {
        val rice = aFood(name = "Rice", facts = FoodFacts(per100g = aPer100g())).copy(id = 7)

        val texts = draw(
            RepeatUiState(
                foods = listOf(rice),
                choosing = Choosing(index = 0, food = rice, countedAs = CountedAs.GRAMS),
            ),
        )

        assertThat(render.isEnabled("Count portion")).isFalse()
        assertThat(render.isEnabled("Weigh it")).isTrue()
        assertThat(texts).contains("Count portion: nothing has said what one of this is")
        assertThat(render.isDrawnBefore("Count portion:", "How much")).isTrue()

        render.click("Give this a portion")

        assertThat(portionFor).isEqualTo(7L)
    }

    /** A food that already has a portion has nothing to be given, so nothing offers to give it. */
    @Test
    fun `a food with a portion is not offered one`() {
        val bread = aFood(
            name = "Bread",
            facts = FoodFacts(per100g = aPer100g(), perUnit = aPerUnit("slice", 80.0), gramsPerUnit = weighing(30.0)),
        )

        val texts = draw(
            RepeatUiState(
                foods = listOf(bread),
                choosing = Choosing(index = 0, food = bread, countedAs = CountedAs.GRAMS),
            ),
        )

        assertThat(render.isEnabled("Count slice")).isTrue()
        assertThat(texts).doesNotContain("Give this a portion")
    }

    /** He sees the number before it lands on the record rather than afterwards. */
    @Test
    fun `the calories are shown before the food is logged`() {
        val food = aFood(name = "Yoghurt", facts = FoodFacts(per100g = aPer100g(kcal = 72.0)))

        val texts = draw(
            RepeatUiState(
                foods = listOf(food),
                choosing = Choosing(
                    index = 0,
                    food = food,
                    countedAs = CountedAs.GRAMS,
                    amount = "200",
                ),
            ),
        )

        assertThat(texts).contains("144 kcal")
    }

    /**
     * "Infinity" as the amount showed a 2,147,483,647-kcal line — or, with a zero figure, threw
     * while drawing. Past its ceiling (D42, issue #32) the box keeps what he typed, the ceiling is
     * named under it, and there is no total to agree to. Something counted is capped at a count,
     * named without a unit, since a count's unit takes its plural on the screen.
     */
    @Test
    fun `an amount past its ceiling is refused under the box`() {
        val yoghurt = aFood(name = "Yoghurt", facts = FoodFacts(per100g = aPer100g(kcal = 72.0)))

        val weighed = draw(
            RepeatUiState(
                foods = listOf(yoghurt),
                choosing = Choosing(
                    index = 0,
                    food = yoghurt,
                    countedAs = CountedAs.GRAMS,
                    amount = "Infinity",
                ),
            ),
        )

        assertThat(weighed).contains("Infinity")
        assertThat(weighed.filter { it == "At most 5000 g at a time." }).hasSize(1)
        assertThat(weighed.indexOf("At most 5000 g at a time."))
            .isGreaterThan(weighed.indexOf("How much"))
        assertThat(weighed.indexOf("At most 5000 g at a time.")).isLessThan(weighed.indexOf("Log it"))
        assertThat(weighed.none { Regex("""\d+ kcal""").matches(it) }).isTrue()

        val bar = aFood(name = "Protein bar", facts = FoodFacts(perUnit = aPerUnit("bar", 190.0)))
        val counted = draw(
            RepeatUiState(
                foods = listOf(bar),
                choosing = Choosing(index = 0, food = bar, countedAs = CountedAs.UNITS, amount = "101"),
            ),
        )

        assertThat(counted).contains("101")
        assertThat(counted.filter { it == "At most 100 at a time." }).hasSize(1)
        assertThat(counted).doesNotContain("At most 5000 g at a time.")

        val believable = draw(
            RepeatUiState(
                foods = listOf(yoghurt),
                choosing = Choosing(index = 0, food = yoghurt, countedAs = CountedAs.GRAMS, amount = "200"),
            ),
        )
        assertThat(believable).contains("144 kcal")
        assertThat(believable.none { it.startsWith("At most") }).isTrue()
    }

    /**
     * Shown, never reconciled. The app cannot know which of the three facts is wrong, and choosing
     * one would be the silent guess the whole model exists to avoid.
     */
    @Test
    fun `a food whose own facts disagree says so`() {
        val texts = draw(
            RepeatUiState(
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

    /** A food that knows only what a portion of it is worth: 1 is 300 kcal, 2 is 600, 1.5 is 450. */
    private val stew =
        aFood("Leftover stew", FoodFacts(perUnit = aPerUnit(FoodFacts.PORTION, 300.0))).copy(id = 3)
    private val slicedBread =
        aFood("Bread", FoodFacts(perUnit = aPerUnit("slice", 80.0))).copy(id = 4)
    private val weighedCucumber = aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)

    /** One part, the stew, counted in the app's own portion. */
    private fun stewNight(portions: Double) = SavedMeal(
        id = 1,
        name = "Stew night",
        components = listOf(
            MealComponent(id = 20, food = stew, amount = portions, countedAs = CountedAs.UNITS),
        ),
    )

    /** A salad he built: two foods, counted the way each of them knows. */
    private fun salad(): SavedMeal {
        val cucumber = aFood(
            name = "Cucumber",
            facts = FoodFacts(per100g = aPer100g(16.0)),
        ).copy(id = 1)
        val oil = aFood(
            name = "Olive oil",
            facts = FoodFacts(perUnit = aPerUnit("spoon", 119.0)),
        ).copy(id = 2)
        return SavedMeal(
            id = 1,
            name = "Vegetable salad",
            components = listOf(
                MealComponent(id = 10, food = cucumber, amount = 100.0, countedAs = CountedAs.GRAMS, position = 0),
                MealComponent(id = 11, food = oil, amount = 1.0, countedAs = CountedAs.UNITS, position = 1),
            ),
        )
    }

    private fun draw(
        state: RepeatUiState,
        onSetComponentAmount: (Long, String) -> Unit = { _, _ -> },
        onStepComponent: (Long, Int) -> Unit = { _, _ -> },
    ): List<String> = render.texts {
        RepeatScreen(
            state = state,
            onDescribe = { words -> describedWith = words },
            onManageFoods = {},
            onGivePortion = { foodId -> portionFor = foodId },
            onRepeat = {},
            onBuildMeal = {},
            onEditMeal = {},
            onPickFood = {},
            onCountAs = {},
            onSetAmount = {},
            onCancelChoosing = {},
            onLogChosen = {},
            onShowTab = { tab -> shownTab = tab },
            onSearch = {},
            onBeginAdjusting = {},
            onSetComponentAmount = onSetComponentAmount,
            onStepComponent = onStepComponent,
            onRemoveComponent = {},
            onCancelAdjusting = {},
            onLogAdjusted = {},
            onBeginAddingToMeal = { beganAdding = true },
            onSearchToAdd = {},
            onStopAddingToMeal = {},
            onPickToAdd = { foodId -> pickedToAdd = foodId },
            onCountAddedAs = {},
            onSetAddedAmount = {},
            onDropPicked = {},
            onPutItIn = { putIn = true },
            onBack = {},
        )
    }

    private companion object {
        const val REPEAT_NOTE =
            "Tap a meal and it goes on the day you are on, as you built it. Tap a food and it asks " +
                "how much first. Nothing is sent anywhere."
    }
}
