package com.metaself.app.ui.screen.mealbuilder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.food.FormReview
import com.metaself.app.ui.food.Review
import com.metaself.app.ui.food.ReviewActions
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Building a meal.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`. The two annotations
 * look identical at the call site and the wrong one produces a test that silently never runs.
 */
@RunWith(RobolectricTestRunner::class)
class MealBuilderScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val cucumber =
        aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)
    private val oil =
        aFood("Olive oil", FoodFacts(perUnit = aPerUnit("spoon", 119.0))).copy(id = 2)

    /** A food that knows only what a portion of it is worth: 1 is 300 kcal, 2 is 600. */
    private val stew =
        aFood("Leftover stew", FoodFacts(perUnit = aPerUnit(FoodFacts.PORTION, 300.0))).copy(id = 3)

    private fun stewNight(portions: Double) = SavedMeal(
        id = 1,
        name = "Stew night",
        components = listOf(MealComponent(20, stew, portions, CountedAs.UNITS)),
    )

    private fun salad() = SavedMeal(
        id = 1,
        name = "Vegetable salad",
        components = listOf(
            MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
        ),
    )

    /**
     * A meal is only something he built AND named. The app never invents a name, which is the whole
     * reason the derived list this replaces refused to label anything it offered.
     */
    @Test
    fun `a new meal asks for a name before anything else`() {
        val texts = draw(MealBuilderUiState())

        assertThat(texts.any { it.contains("Give it a name") }).isTrue()
        assertThat(texts.any { it.contains("will not invent one") }).isTrue()
    }

    /**
     * What is IN the meal is written as he goes, so leaving is not losing it. A food picked but not
     * yet put in — waiting for an amount, or opened for adding — lives only on this screen and is
     * gone when he leaves, and the heading used to say "Everything here is kept", which the agent
     * walk found untrue. The words change to what is true rather than the screen starting to
     * keep an amount-less part the record cannot hold (D37).
     */
    @Test
    fun `the heading says what is kept and what is not`() {
        val texts = draw(MealBuilderUiState(meal = salad()))

        assertThat(texts).contains(
            "Nothing to save. What is in the meal is kept as you go, so you can leave and come " +
                "back. A food you have picked but not put in yet is not in the meal when you come " +
                "back.",
        )
        assertThat(texts.none { it.contains("Everything here is kept") }).isTrue()
    }

    @Test
    fun `a meal with nothing in it yet says so rather than looking broken`() {
        val texts = draw(MealBuilderUiState(meal = SavedMeal(id = 1, name = "Salad")))

        assertThat(texts.any { it.startsWith("Nothing in it yet") }).isTrue()
    }

    @Test
    fun `what is in it shows how much, and what the meal comes to`() {
        val texts = draw(MealBuilderUiState(meal = salad()))

        assertThat(texts).contains("Cucumber")
        assertThat(texts).contains("100 g · 16 kcal")
        assertThat(texts).contains("16 kcal")
    }

    // --- "2 portion" on the builder's parts (D37, #27) -------------------------------------------

    /**
     * A part stores an amount and what it is counted in, and no words: they are built here, in the
     * app's own form by construction, so a count of its own "portion" takes the plural directly.
     */
    @Test
    fun `a part of two portions reads as portions`() {
        val texts = draw(MealBuilderUiState(meal = stewNight(2.0)))

        assertThat(texts).contains("2 portions · 600 kcal")
        assertThat(texts.none { it == "2 portion · 600 kcal" }).isTrue()
    }

    @Test
    fun `a part of one portion reads as one portion`() {
        val texts = draw(MealBuilderUiState(meal = stewNight(1.0)))

        assertThat(texts).contains("1 portion · 300 kcal")
        assertThat(texts.none { it.contains("1 portions") }).isTrue()
    }

    /** The order is his, so it can be changed. */
    @Test
    fun `the things in it can be moved`() {
        val texts = draw(MealBuilderUiState(meal = salad()))

        assertThat(texts).contains("Up")
        assertThat(texts).contains("Down")
    }

    // --- Every repeated control names its part (public issue #3) ------------------------------

    private fun twoParts() = SavedMeal(
        id = 1,
        name = "Vegetable salad",
        components = listOf(
            MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
            MealComponent(11, oil, 1.0, CountedAs.UNITS, position = 1),
        ),
    )

    /**
     * Up, Down and Remove are drawn once per part, and a screen reader heard every copy by the same
     * word. Each now says which part it moves or takes out; the words on screen are unchanged.
     */
    @Test
    fun `up, down and remove on each part say which part`() {
        val texts = draw(MealBuilderUiState(meal = twoParts()))

        assertThat(texts.count { it == "Up" }).isEqualTo(2)
        for (name in listOf("Cucumber", "Olive oil")) {
            assertThat(render.describedCount("Move $name up")).isEqualTo(1)
            assertThat(render.describedCount("Move $name down")).isEqualTo(1)
            assertThat(render.describedCount("Remove $name")).isEqualTo(1)
        }
    }

    /** The same shape for two foods brought in at once, each waiting for an amount. */
    @Test
    fun `each waiting food's box and buttons say which food`() {
        val texts = draw(
            MealBuilderUiState(
                meal = SavedMeal(id = 1, name = "Vegetable salad"),
                pending = listOf(
                    Pending(food = cucumber, countedAs = CountedAs.GRAMS),
                    Pending(food = stew, countedAs = CountedAs.UNITS),
                ),
            ),
        )

        assertThat(texts.count { it == "Put it in" }).isEqualTo(2)
        for (name in listOf("Cucumber", "Leftover stew")) {
            assertThat(render.describedCount("How much of $name")).isEqualTo(1)
            assertThat(render.describedIsField("How much of $name")).isTrue()
            assertThat(render.describedCount("Put $name in")).isEqualTo(1)
            assertThat(render.describedCount("Leave $name out")).isEqualTo(1)
            assertThat(render.describedCount("Weigh it, for $name")).isEqualTo(1)
        }
    }

    /**
     * The food made on the spot draws its four labels twice, once per group, and the walk that found
     * this could not aim at a single one of the eight boxes. Each box now has a name of its own.
     */
    @Test
    fun `making a food on the spot names each figure box by its group`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                making = MakingFood(form = FoodForm(name = "Lentil soup", unitName = "bowl")),
            ),
        )

        assertThat(texts.count { it == "Calories" }).isEqualTo(2)
        for (label in listOf("Calories", "Protein (g)", "Carbs (g)", "Fat (g)")) {
            for (name in listOf("$label per 100 g", "$label per bowl")) {
                assertWithMessage(name).that(render.describedCount(name)).isEqualTo(1)
                assertWithMessage(name).that(render.describedIsField(name)).isTrue()
            }
        }
    }

    /** D56: a unit box naming the millilitre makes the per-one group per 100 ml, heading and boxes. */
    @Test
    fun `a food made on the spot in ml is worth per 100 ml`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                making = MakingFood(form = FoodForm(name = "Oat drink", unitName = "ml")),
            ),
        )

        assertThat(texts).contains("What 100 ml of it are worth")
        assertThat(texts).doesNotContain("What one of it is worth")
        assertThat(render.describedCount("Calories per 100 ml")).isEqualTo(1)
    }

    // --- Counting it in ml in one tap (public issue #5) -----------------------------------------

    @Test
    fun `a food made on the spot offers counting it in ml while its per one boxes are empty`() {
        val empty = draw(MealBuilderUiState(meal = salad(), making = MakingFood()))
        assertThat(empty).contains(COUNT_IN_ML)
        assertThat(empty).contains(UNIT_LABEL)

        val bar = draw(
            MealBuilderUiState(meal = salad(), making = MakingFood(form = FoodForm(name = "Oat bar", unitName = "bar"))),
        )
        assertThat(bar).contains(COUNT_IN_ML)
    }

    @Test
    fun `pressing it on the spot names ml and the group becomes per 100 ml`() {
        render.texts {
            var form by remember { mutableStateOf(FoodForm(name = "Oat drink", kcalPer100g = "57")) }
            drawing(MealBuilderUiState(meal = salad(), making = MakingFood(form = form)), onSetNewFood = { form = it })
        }

        render.click(COUNT_IN_ML)
        val after = render.textsAgain()

        assertThat(after).contains("What 100 ml of it are worth")
        assertThat(render.fieldTexts()).containsAtLeast("Oat drink", "57", "ml").inOrder()
        assertThat(after).doesNotContain(COUNT_IN_ML)
    }

    @Test
    fun `a food made on the spot already in ml is not offered it`() {
        val texts = draw(
            MealBuilderUiState(meal = salad(), making = MakingFood(form = FoodForm(name = "Oat drink", unitName = "ml"))),
        )

        assertThat(texts).doesNotContain(COUNT_IN_ML)
    }

    /** A figure typed per bar would silently become per 100 ml, so the switch stands aside. */
    @Test
    fun `a food made on the spot with a per one figure typed is not offered it`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                making = MakingFood(form = FoodForm(name = "Oat bar", unitName = "bar", kcalPerUnit = "190")),
            ),
        )

        assertThat(texts).doesNotContain(COUNT_IN_ML)
        assertThat(texts).contains(UNIT_LABEL)
    }

    @Test
    fun `a food made on the spot with no unit named yet has its boxes said per one`() {
        draw(MealBuilderUiState(meal = salad(), making = MakingFood()))

        assertThat(render.describedCount("Calories per one")).isEqualTo(1)
    }

    /**
     * Going away to the foods manager to make a missing food and back again would lose the meal
     * being built.
     */
    @Test
    fun `a food not on his list can be made without leaving the meal`() {
        val texts = draw(MealBuilderUiState(meal = salad()))

        assertThat(texts).contains("Make a food that is not on your list")
    }

    /**
     * The food made on the spot goes through the same form as My foods and keeps decimals the same
     * way, so it says the same true sentence, before anything is typed — never "whole grams", which
     * is the entry editor's rule and would be false here (issue #18, D38).
     */
    @Test
    fun `making a food on the spot says decimals are kept, before anything is typed`() {
        val texts = draw(MealBuilderUiState(meal = salad(), making = MakingFood()))

        assertThat(texts).contains(DECIMALS_KEPT)
        assertThat(texts.indexOf(DECIMALS_KEPT))
            .isLessThan(texts.indexOf("What 100 g of it are worth"))
        assertThat(texts.none { it.contains("whole grams") }).isTrue()
    }

    /**
     * *Make a food* offers the review once it has a name, and a filled group's suggestion goes into
     * its boxes, pending, with its reason once and a Clear under each — the same pieces as the
     * food's page (D54 §12.6); Make it and Cancel give way to Accept changes and make it and Cancel
     * suggestions. Figures invented.
     */
    @Test
    fun `making a food on the spot offers a review, and its suggestions wait in the boxes`() {
        val nameless = draw(MealBuilderUiState(meal = salad(), making = MakingFood()))
        assertThat(nameless).doesNotContain("Review the figures")

        val filled = Suggestion(Nutrients(60.0, 4.0, 9.0, 1.0), Confidence.LOW, true, emptyList(), "A reason.")
        val start = FoodForm(name = "Lentil soup", unitName = "bowl")
        val (form, reviewing) = FormReview().asked(start).answered(FoodReview(filled, null, null, emptyList()), null, start)
        val texts = draw(MealBuilderUiState(meal = salad(), making = MakingFood(form = form, reviewing = reviewing)))

        assertThat(render.fieldsSaid("suggested by the review, not accepted"))
            .containsExactly("60", "4", "9", "1").inOrder()
        assertThat(texts.count { it == "A reason." }).isEqualTo(1)
        assertThat(texts.count { it == "Clear" }).isEqualTo(4)
        assertThat(texts).contains("Accept changes and make it")
        assertThat(texts).contains("Cancel suggestions")
        assertThat(texts).doesNotContain("Make it")
        assertThat(texts).doesNotContain("Review the figures")
        assertThat(texts.none { "→" in it || it == "Apply these changes" || it == "Undo" }).isTrue()
    }

    @Test
    fun `a new food's review outcome is said under its button, a failure included`() {
        fun drawn(review: Review) = draw(
            MealBuilderUiState(
                meal = salad(),
                making = MakingFood(
                    form = FoodForm(name = "Lentil soup", unitName = "bowl"),
                    reviewing = FormReview(review = review),
                ),
            ),
        )
        val sends = "Sends this food's name"

        listOf(
            Review.Shown(FoodReview(null, null, null, emptyList()), nothingSuggested = true) to
                "Reviewed: no changes suggested.",
            Review.Shown(FoodReview(null, null, null, listOf(com.metaself.app.domain.ai.ReviewItem.PER_UNIT)), unusable = true) to
                "The model's answer arrived, but its suggestions could not be used.",
            Review.Failed(EstimateResult.Unreachable()) to
                "Could not reach the model. Type the numbers instead — your words are still here.",
        ).forEach { (review, line) ->
            val texts = drawn(review)

            assertWithMessage(line).that(texts.count { it == line }).isEqualTo(1)
            assertWithMessage(line).that(render.isDrawnBefore("Review the figures", line)).isTrue()
            assertWithMessage(line).that(render.isDrawnBefore(sends, line)).isTrue()
        }
    }

    @Test
    fun `putting something in asks how much and shows what it comes to`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = oil, countedAs = CountedAs.UNITS, amount = "2"),
            ),
        )

        assertThat(texts).contains("Olive oil")
        assertThat(texts).contains("How much")
        assertThat(texts).contains("238 kcal")
    }

    /**
     * The same rule the logging screen follows: a way of counting the food does not support is shown
     * with its reason rather than quietly missing.
     */
    @Test
    fun `a way of counting that is not available says why`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = oil, countedAs = CountedAs.UNITS),
            ),
        )

        assertThat(texts).contains("Weigh it: nothing knows what one spoon weighs")
    }

    /** The same food twice in one meal is an editing accident, so it is not offered again. */
    @Test
    fun `a food already in the meal is still shown as part of it`() {
        val texts = draw(MealBuilderUiState(meal = salad(), candidates = listOf(oil)))

        assertThat(texts).contains("Cucumber")
        assertThat(texts).contains("Olive oil")
    }

    /**
     * A meal built with a typo in its name was permanent until this existed, and so was a meal built
     * by accident. Renaming retitles every day it was eaten, exactly as renaming a food does.
     */
    @Test
    fun `a built meal can be renamed and deleted`() {
        val texts = draw(MealBuilderUiState(meal = salad()))

        assertThat(texts).contains("Vegetable salad")
        assertThat(texts).contains("Delete this meal")
    }

    @Test
    fun `a name another meal already holds is refused with the reason`() {
        val texts = draw(
            MealBuilderUiState(refusal = "You already have a meal called “Vegetable salad”."),
        )

        assertThat(texts.any { it.contains("already have a meal called") }).isTrue()
    }

    // --- Foods chosen in the list, waiting here for an amount -----------------------------------

    /**
     * D4, drawn: a number put into the box for him is a number he cannot tell from his own the
     * moment he saves it. So the row shows the food and an empty field, and the last assertion is
     * the one that matters — **nothing numeric is on screen at all**, in a meal with nothing in it
     * yet, because there is nothing honest to put there.
     */
    @Test
    fun `a food chosen in the list waits here with nothing filled in`() {
        val texts = draw(
            MealBuilderUiState(
                meal = SavedMeal(id = 1, name = "Vegetable salad"),
                pending = listOf(Pending(food = cucumber, countedAs = CountedAs.GRAMS)),
            ),
        )

        assertThat(texts).contains("Cucumber")
        assertThat(texts.any { it.contains("waiting for an amount") }).isTrue()
        assertThat(texts.any { it.contains("Nothing is filled in for you") }).isTrue()
        assertThat(texts.none { it.trim().replace(',', '.').toDoubleOrNull() != null }).isTrue()
    }

    /**
     * The same rule the rest of this screen follows: a way of counting the food does not support is
     * shown with its reason rather than quietly missing. Olive oil knows what one spoon of it is
     * worth and nothing else, so weighing it is not on offer and the row says why.
     */
    @Test
    fun `a way of counting a waiting food does not support is drawn with its reason`() {
        val texts = draw(
            MealBuilderUiState(
                meal = SavedMeal(id = 1, name = "Vegetable salad"),
                pending = listOf(Pending(food = oil, countedAs = CountedAs.GRAMS)),
            ),
        )

        assertThat(texts).contains("Weigh it: nothing knows what one spoon weighs")
    }

    /**
     * The row goes in when he says so. Typing is one character at a time, so a row that joined the
     * meal the instant what was typed could be costed joined it at the first digit — and took its
     * own box off the screen with the rest of the number still to come.
     */
    @Test
    fun `a waiting food is put in by hand, not by the act of typing`() {
        val texts = draw(
            MealBuilderUiState(
                meal = SavedMeal(id = 1, name = "Vegetable salad"),
                pending = listOf(
                    Pending(food = cucumber, countedAs = CountedAs.GRAMS, amount = "100"),
                ),
            ),
        )

        assertThat(texts).contains("Put it in")
        // The running total is under the box while he types, which is what the waiting row is for.
        assertThat(texts).contains("16 kcal")
    }

    /**
     * 6 kg of cucumber used to cost itself (960 kcal) and could be put in. Past its ceiling (D42,
     * issue #32) the row says the ceiling under the box and, having no total, still says it is
     * waiting for an amount — which it is.
     */
    @Test
    fun `a waiting food past 5000 g says so`() {
        val texts = draw(
            MealBuilderUiState(
                meal = SavedMeal(id = 1, name = "Vegetable salad"),
                pending = listOf(
                    Pending(food = cucumber, countedAs = CountedAs.GRAMS, amount = "6000"),
                ),
            ),
        )

        assertThat(texts).contains("6000")
        assertThat(texts.filter { it == "At most 5000 g at a time." }).hasSize(1)
        assertThat(texts.indexOf("At most 5000 g at a time.")).isGreaterThan(texts.indexOf("How much"))
        assertThat(texts.indexOf("At most 5000 g at a time.")).isLessThan(texts.indexOf("Put it in"))
        assertThat(texts).contains("waiting for an amount")
        assertThat(texts).doesNotContain("960 kcal")
    }

    /** The box for putting a food in has the same ceiling, and says so the same way. */
    @Test
    fun `a food being put in past its ceiling says so`() {
        val weighed = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = cucumber, countedAs = CountedAs.GRAMS, amount = "Infinity"),
            ),
        )
        assertThat(weighed).contains("Infinity")
        assertThat(weighed.filter { it == "At most 5000 g at a time." }).hasSize(1)
        // The meal's own total is the only calorie line: no preview is worked out for the refused
        // amount, so nothing on screen offers a figure he cannot log.
        assertThat(weighed.filter { Regex("""\d+ kcal""").matches(it) }).containsExactly("16 kcal")

        val counted = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = oil, countedAs = CountedAs.UNITS, amount = "101"),
            ),
        )
        assertThat(counted.filter { it == "At most 100 at a time." }).hasSize(1)
    }

    /** What is already in the meal is not disturbed by what is still waiting beside it. */
    @Test
    fun `what is waiting is drawn beside what is already in the meal`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                pending = listOf(Pending(food = oil, countedAs = CountedAs.UNITS)),
            ),
        )

        assertThat(texts).contains("Cucumber")
        assertThat(texts).contains("100 g · 16 kcal")
        assertThat(texts).contains("Olive oil")
    }

    // --- Searching what is already here (D41, issue #14) ------------------------------------------

    private val tomato = aFood("Tomato").copy(id = 4)
    private val pickle = aFood("Cucumber pickle").copy(id = 5)
    private val milk = aFood("Milk").copy(id = 6, brand = "Dairyco")

    /**
     * The agent walk: Cucumber drawn a few lines up, `Cuc` typed, and the screen said nothing
     * matched. A meal holds a food once, so it is not offered — it is named instead.
     */
    @Test
    fun `a search finding only what is in the meal says so, never nothing matches`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                query = "Cuc",
                candidates = emptyList(),
                alreadyIn = listOf(cucumber),
            ),
        )

        assertThat(texts).contains("Cucumber is already in this meal.")
        assertThat(texts.none { it.contains("Nothing matches") }).isTrue()
    }

    /**
     * Plural by count, from resources, and any number of names joined the same way. The states are
     * drawn directly — the screen prints the lists it is given and does not search them again.
     */
    @Test
    fun `several foods already in are named together, in the plural`() {
        val two = draw(
            MealBuilderUiState(meal = salad(), query = "o", alreadyIn = listOf(cucumber, oil)),
        )
        assertThat(two).contains("Cucumber and Olive oil are already in this meal.")

        val three = draw(
            MealBuilderUiState(
                meal = salad(),
                query = "o",
                alreadyIn = listOf(cucumber, oil, tomato),
            ),
        )
        assertThat(three).contains("Cucumber, Olive oil and Tomato are already in this meal.")
    }

    @Test
    fun `a search with no match at all still says nothing matches`() {
        val texts = draw(MealBuilderUiState(meal = salad(), query = "Tahini"))

        assertThat(texts).contains("Nothing matches “Tahini”.")
        assertThat(texts.none { it.contains("already") }).isTrue()
    }

    /**
     * Every food the search found is accounted for: the new one as a row to tap, the one already in
     * by name. Dropping the Cucumber silently because the pickle was offered would be the same lie.
     */
    @Test
    fun `a new food is offered and the one already in is named`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                query = "cucumber",
                candidates = listOf(pickle),
                alreadyIn = listOf(cucumber),
            ),
        )

        assertThat(texts).contains("Cucumber pickle")
        assertThat(texts).contains("Cucumber is already in this meal.")
        assertThat(texts.none { it.contains("Nothing matches") }).isTrue()
    }

    /** Waiting for an amount is not in the meal (D37), so it is never said to be. */
    @Test
    fun `a waiting food the search found is said to be waiting`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                pending = listOf(Pending(food = oil, countedAs = CountedAs.UNITS)),
                query = "oil",
                alreadyWaiting = listOf(oil),
            ),
        )

        assertThat(texts).contains("Olive oil is already here, waiting for an amount.")
        assertThat(texts.none { it.contains("already in this meal") }).isTrue()
        assertThat(texts.none { it.contains("Nothing matches") }).isTrue()
    }

    /**
     * The screen prints the lists it is given and does not search them again, so this pins only its
     * half: with a food in the meal and one waiting on screen, an empty search box draws both rows
     * and neither sentence — not "already here", and not "Nothing matches" for an empty search. The
     * other half, that an empty search gives empty lists, is the view model's guard and is tested
     * there (`nothing is named as already in before he types`).
     */
    @Test
    fun `an empty search box draws the rows and no sentence about them`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                pending = listOf(Pending(food = oil, countedAs = CountedAs.UNITS)),
                query = "",
            ),
        )

        assertThat(texts).containsAtLeast("Cucumber", "Olive oil")
        assertThat(texts.none { it.contains("already") }).isTrue()
        assertThat(texts.none { it.contains("Nothing matches") }).isTrue()
    }

    /**
     * One search finding a food in the meal and another waiting for an amount: both sentences, each
     * saying what the food is, and no "Nothing matches".
     */
    @Test
    fun `a food in the meal and one waiting are both named by one search`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                pending = listOf(Pending(food = pickle, countedAs = CountedAs.GRAMS)),
                query = "cucumber",
                alreadyIn = listOf(cucumber),
                alreadyWaiting = listOf(pickle),
            ),
        )

        assertThat(texts).contains("Cucumber is already in this meal.")
        assertThat(texts).contains("Cucumber pickle is already here, waiting for an amount.")
        assertThat(texts.none { it.contains("Nothing matches") }).isTrue()
    }

    /**
     * Found by its brand, a food in the meal has to show the brand on its row and in the sentence,
     * or `Dairyco` would say "Milk is already in this meal" beside a row reading only `Milk`.
     */
    @Test
    fun `a branded food already here shows its brand on the row and in the sentence`() {
        val inMeal = draw(
            MealBuilderUiState(
                meal = SavedMeal(
                    id = 1,
                    name = "Breakfast",
                    components = listOf(MealComponent(10, milk, 200.0, CountedAs.GRAMS)),
                ),
                query = "dairyco",
                alreadyIn = listOf(milk),
            ),
        )
        assertThat(inMeal).containsAtLeast("Milk", "Dairyco")
        assertThat(inMeal).contains("Milk (Dairyco) is already in this meal.")

        val waiting = draw(
            MealBuilderUiState(
                meal = salad(),
                pending = listOf(Pending(food = milk, countedAs = CountedAs.GRAMS)),
                query = "dairyco",
                alreadyWaiting = listOf(milk),
            ),
        )
        assertThat(waiting).containsAtLeast("Milk", "Dairyco")
        assertThat(waiting).contains("Milk (Dairyco) is already here, waiting for an amount.")
    }

    /**
     * Two foods may share a name and differ only in brand; the sentence tells them apart by it, and
     * a food with no brand keeps its bare name — `NA` is never said.
     */
    @Test
    fun `two foods of one name are told apart by brand in the sentence`() {
        val meadowco = aFood("Milk").copy(id = 7, brand = "Meadowco")
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                query = "milk",
                alreadyIn = listOf(milk, meadowco, aFood("Milk").copy(id = 8)),
            ),
        )

        assertThat(texts).contains("Milk (Dairyco), Milk (Meadowco) and Milk are already in this meal.")
        assertThat(texts.none { it == "NA" || it.contains("(NA)") }).isTrue()
    }

    /**
     * A food offered because its brand matched has to show the brand, or `Dairyco` offers a row
     * reading only `Milk` — a match on text he cannot see. `NA` is the brand of food with no brand
     * and is never printed.
     */
    @Test
    fun `a food offered to put in shows its brand, never NA`() {
        val branded = draw(
            MealBuilderUiState(meal = salad(), query = "dairyco", candidates = listOf(milk)),
        )
        assertThat(branded).contains("Milk")
        assertThat(branded).contains("Dairyco")

        val unbranded = draw(
            MealBuilderUiState(meal = salad(), query = "o", candidates = listOf(aFood("Tomato"))),
        )
        assertThat(unbranded).contains("Tomato")
        assertThat(unbranded.none { it == "NA" }).isTrue()
    }

    // --- issue #13 (D45): the door that types figures into an existing food ---------------------

    /** The one sentence both tests are about: drawn when it is there, absent when it is not. */
    private val said =
        "“Olive oil” now counts 900 kcal per 100 g, where it counted 880, " +
            "because you have just typed different numbers for it."

    /**
     * *Make a food* can replace a figure the food already held, and this panel is the only surface
     * that screen has — so the line sits in it, under the food's name and above the amount box, and
     * has **no dismiss of its own**: it goes when the panel does.
     */
    @Test
    fun `a food just retaught says so inside the how much panel, with no button`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = oil, countedAs = CountedAs.UNITS, retaughtNotice = said),
            ),
        )

        assertThat(texts).contains(said)
        assertThat(texts.indexOf("Olive oil")).isLessThan(texts.indexOf(said))
        assertThat(texts.indexOf(said)).isLessThan(texts.indexOf("How much"))
        assertThat(texts).doesNotContain("Got it")
    }

    /** With nothing to say the panel is exactly the panel it always was. */
    @Test
    fun `a food that was not retaught draws nothing extra`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = oil, countedAs = CountedAs.UNITS),
            ),
        )

        // The same sentence the positive test asserts is drawn, so the two cannot drift apart and
        // a regression that drew it with different leading words would still be caught.
        assertThat(texts).doesNotContain(said)
        assertThat(texts.none { it.startsWith("“Olive oil” now") }).isTrue()
    }

    /**
     * Delete sat between the search box and the results — in the path a thumb travels over and
     * over while building a meal. It asks before acting, so nothing was ever lost to it; it was
     * simply in the worst place on the screen. It belongs after everything he came here to do.
     */
    @Test
    fun `delete this meal is out of the search path, below the results`() {
        draw(MealBuilderUiState(meal = salad(), query = "o", candidates = listOf(oil)))

        assertThat(render.isDrawnBefore("Search your foods", "Delete this meal")).isTrue()
        assertThat(render.isDrawnBefore("Make a food", "Delete this meal")).isTrue()
        assertThat(render.isDrawnBefore("Olive oil", "Delete this meal")).isTrue()
    }

    @Test
    fun `an action that failed says so where a refusal would`() {
        val texts = draw(
            MealBuilderUiState(
                meal = SavedMeal(id = 1, name = "Vegetable salad"),
                failed = ActionRefused.COULD_NOT_OPEN,
            ),
        )

        assertThat(texts).contains("That couldn't be opened. What went wrong is under Settings → Recent problems.")
        assertThat(texts).contains("All right")
    }

    // --- A part already in the meal gets its amount changed (D53 §7, #4) ------------------------

    @Test
    fun `tapping a part's row opens it to change its amount`() {
        draw(MealBuilderUiState(meal = salad()))

        render.click("Cucumber")

        assertThat(changedPart).isEqualTo(10L)
    }

    /** Up, Down and Remove stay on the row, and are not the row's tap. */
    @Test
    fun `the part's own buttons are still its own`() {
        val texts = draw(MealBuilderUiState(meal = salad()))

        assertThat(texts).containsAtLeast("Up", "Down", "Remove")
        render.click("Remove")
        assertThat(changedPart).isNull()
    }

    @Test
    fun `the panel for a part already in says change it, not put it in`() {
        val texts = draw(
            MealBuilderUiState(
                meal = salad(),
                adding = Adding(food = cucumber, countedAs = CountedAs.GRAMS, amount = "100", changing = 10),
            ),
        )

        assertThat(texts).contains("Change it")
        assertThat(texts).doesNotContain("Put it in")
        assertThat(texts).contains("100")
    }

    /** D41's sentence names the food, and now leads to its part. */
    @Test
    fun `a food named as already in can be opened from the sentence`() {
        val texts = draw(
            MealBuilderUiState(meal = salad(), query = "cucum", alreadyIn = listOf(cucumber)),
        )

        assertThat(texts).contains("Cucumber is already in this meal.")
        render.click("Change how much Cucumber")
        assertThat(changedFood).isEqualTo(cucumber.id)
    }

    private fun draw(state: MealBuilderUiState): List<String> = render.texts { drawing(state) }

    /** The part whose row was tapped, and the food whose name in the already-in sentence was. */
    private var changedPart: Long? = null
    private var changedFood: Long? = null

    @androidx.compose.runtime.Composable
    private fun drawing(state: MealBuilderUiState, onSetNewFood: (FoodForm) -> Unit = {}) {
        MealBuilderScreen(
            state = state,
            onSetName = {},
            onName = {},
            onRename = {},
            onSearch = {},
            onBeginAdding = {},
            onCountAs = {},
            onSetAmount = {},
            onConfirmAdding = {},
            onCancelAdding = {},
            onSetPendingAmount = { _, _ -> },
            onCountPendingAs = { _, _ -> },
            onConfirmPending = {},
            onDropPending = {},
            onRemove = {},
            onMove = { _, _ -> },
            onChangePart = { changedPart = it },
            onChangeFood = { changedFood = it },
            onBeginCreatingFood = {},
            onSetNewFood = onSetNewFood,
            onCreateFood = {},
            onCancelCreatingFood = {},
            newFoodReview = ReviewActions.NONE,
            onDelete = {},
            onDismissRefusal = {},
            onBack = {},
        )
    }

    private companion object {

        /** `R.string.foods_count_in_ml`. */
        const val COUNT_IN_ML = "Count it in ml"

        /** `R.string.foods_field_unit`, shared with My foods' editor. */
        const val UNIT_LABEL = "One what? A slice, an egg, or ml"

        /**
         * Shared by the two food forms: this one and My foods' editor. The packet-label form has its
         * own sentence, "…for this packet…" (D38), because "on this food" is not true there.
         */
        const val DECIMALS_KEPT =
            "Numbers here can have a decimal point: 0.5 g is kept as 0.5 g on this food."
    }
}
