package com.metaself.app.ui.screen.repeat

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.SavedMeal
import org.junit.jupiter.api.Test

/**
 * Pure state, so JUnit 5 and Truth — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two
 * annotations look identical at the call site and the wrong one produces a test that never runs.
 */
class RepeatUiStateTest {

    /**
     * The sentence that says nothing matched is about the list in front, and stays per-tab. The
     * OFFER to describe hangs on both lists missing: offering it for something the other tab holds
     * would invite a duplicate of a food the owner already has.
     */
    @Test
    fun `nothing matched anywhere only when both lists are empty`() {
        assertThat(
            RepeatUiState(query = "shakshuka", foods = emptyList(), meals = emptyList())
                .nothingMatchedEither,
        ).isTrue()

        assertThat(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                query = "hummus",
                foods = someFoods(),
                meals = emptyList(),
            ).nothingMatchedEither,
        ).isFalse()

        assertThat(
            RepeatUiState(query = "   ", foods = emptyList(), meals = emptyList())
                .nothingMatchedEither,
        ).isFalse()
    }

    /**
     * A miss on the list in front while the other list holds a match: the other list is named, so
     * the screen can offer it. Nothing is named when this list matched, when neither did, or when
     * nothing was searched for.
     */
    @Test
    fun `a miss here names the other list when that one matched`() {
        assertThat(
            RepeatUiState(tab = RepeatTab.MEALS, query = "hummus", foods = someFoods())
                .matchesOnOtherTab,
        ).isEqualTo(RepeatTab.FOODS)

        assertThat(
            RepeatUiState(tab = RepeatTab.FOODS, query = "salad", meals = listOf(aMeal()))
                .matchesOnOtherTab,
        ).isEqualTo(RepeatTab.MEALS)

        assertThat(
            RepeatUiState(tab = RepeatTab.FOODS, query = "hummus", foods = someFoods())
                .matchesOnOtherTab,
        ).isNull()

        assertThat(RepeatUiState(tab = RepeatTab.MEALS, query = "fish").matchesOnOtherTab).isNull()

        assertThat(
            RepeatUiState(tab = RepeatTab.MEALS, query = "", foods = someFoods()).matchesOnOtherTab,
        ).isNull()
    }

    /**
     * The third dead end: nothing searched for, the list in front empty, the other list not. With a
     * query typed this case belongs to the searched-and-found-nothing sentence instead, so the
     * property must stay out of it.
     */
    @Test
    fun `a list with nothing on it is recognised without a search`() {
        assertThat(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                query = "",
                foods = someFoods(),
                meals = emptyList(),
            ).thisTabIsEmpty,
        ).isTrue()

        assertThat(
            RepeatUiState(tab = RepeatTab.FOODS, query = "", foods = someFoods()).thisTabIsEmpty,
        ).isFalse()

        assertThat(
            RepeatUiState(
                tab = RepeatTab.MEALS,
                query = "hummus",
                foods = someFoods(),
                meals = emptyList(),
            ).thisTabIsEmpty,
        ).isFalse()
    }

    private fun someFoods() = listOf(aFood(name = "Hummus"))

    private fun aMeal() = SavedMeal(id = 1, name = "Salad", components = emptyList())

    // --- An amount has a ceiling (D42, issue #32) ------------------------------------------------

    /** No fat at all: the food on which "Infinity" crashed the screen, since 0 × ∞ is no number. */
    private val cucumber = aFood(
        name = "Cucumber",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(16.0, 0.7, 3.6, 0.0),
                Provenance(Source.TYPED, null, setAtMillis = 0),
            ),
        ),
    )

    private val bar = aFood(name = "Protein bar", facts = FoodFacts(perUnit = aPerUnit("bar", 190.0)))

    /**
     * "Infinity" typed as the amount of a food with a zero figure threw while the screen was
     * drawing — the preview is worked out on every keystroke. Past its ceiling the amount is no
     * amount: no preview, nothing to log, and too much is what the box will say. A comma is still a
     * decimal point. Exactly at the ceiling is an amount like any other.
     */
    @Test
    fun `an amount past its ceiling logs nothing and does not throw`() {
        listOf("Infinity", "1e300", "5000,5").forEach { typed ->
            val choosing = Choosing(index = 0, food = cucumber, countedAs = CountedAs.GRAMS, amount = typed)

            assertThat(choosing.amountOrNull).isNull()
            assertThat(choosing.preview).isNull()
            assertThat(choosing.canLog).isFalse()
            assertThat(choosing.amountTooMuch).isTrue()
            assertThat(choosing.most).isEqualTo(5_000.0)
        }

        val atTheCeiling = Choosing(index = 0, food = cucumber, countedAs = CountedAs.GRAMS, amount = "5000")
        assertThat(atTheCeiling.amountTooMuch).isFalse()
        assertThat(atTheCeiling.preview!!.kcal).isEqualTo(800)

        val tooMany = Choosing(index = 0, food = bar, countedAs = CountedAs.UNITS, amount = "101")
        assertThat(tooMany.amountOrNull).isNull()
        assertThat(tooMany.canLog).isFalse()
        assertThat(tooMany.amountTooMuch).isTrue()
        assertThat(tooMany.most).isEqualTo(100.0)

        val hundred = Choosing(index = 0, food = bar, countedAs = CountedAs.UNITS, amount = "100")
        assertThat(hundred.amountTooMuch).isFalse()
        assertThat(hundred.preview!!.kcal).isEqualTo(19_000)

        // The quiet cases stay quiet: a blank, a zero, a word or a half-typed number just leaves
        // the button off, as it always has.
        listOf("", "0", "0.", "a lot", "NaN").forEach { typed ->
            val choosing = Choosing(index = 0, food = cucumber, countedAs = CountedAs.GRAMS, amount = typed)

            assertThat(choosing.amountTooMuch).isFalse()
            assertThat(choosing.canLog).isFalse()
        }
    }
}
