package com.metaself.app.ui.screen.mealbuilder

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import org.junit.jupiter.api.Test

/**
 * The meal builder's two amount boxes — a food being added, and a food waiting for an amount — and
 * the ceiling they share with every other amount box (D42, issue #32).
 *
 * Pure state, so JUnit 5 and Truth — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two
 * annotations look identical at the call site and the wrong one produces a test that never runs.
 */
class MealBuilderUiStateTest {

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

    /** D56: counted in ml, a measured amount, up to 5000 ml. Invented 57 kcal per 100 ml. */
    @Test
    fun `an amount of a food counted in ml goes in up to 5000 ml`() {
        val oatDrink = aFood(
            name = "Oat drink",
            facts = FoodFacts(
                perUnit = PerUnit("ml", Nutrients(0.57, 0.029, 0.047, 0.036), Provenance(Source.LABEL, null, 0)),
            ),
        )

        val pending = Pending(food = oatDrink, countedAs = CountedAs.UNITS, amount = "200")
        assertThat(pending.most).isEqualTo(5_000.0)
        assertThat(pending.canAdd).isTrue()
        assertThat(pending.preview!!.kcal).isEqualTo(114)
        assertThat(Adding(food = oatDrink, countedAs = CountedAs.UNITS, amount = "5000").amountTooMuch).isFalse()
        assertThat(Adding(food = oatDrink, countedAs = CountedAs.UNITS, amount = "5001").amountTooMuch).isTrue()
    }

    /**
     * Before D42 an infinite amount threw while the preview was drawn (a zero figure), or went into
     * the meal (none). Past its ceiling it is no amount: no preview, cannot be put in, and too much
     * is what the box will say. Exactly at the ceiling it goes in like any other. [Pending] answers
     * through [Adding], so both are held to the same numbers.
     */
    @Test
    fun `an amount past its ceiling cannot be put in`() {
        listOf("Infinity", "1e300", "5000,5").forEach { typed ->
            val adding = Adding(food = cucumber, countedAs = CountedAs.GRAMS, amount = typed)
            val pending = Pending(food = cucumber, countedAs = CountedAs.GRAMS, amount = typed)

            assertThat(adding.amountOrNull).isNull()
            assertThat(adding.preview).isNull()
            assertThat(adding.canAdd).isFalse()
            assertThat(adding.amountTooMuch).isTrue()
            assertThat(adding.most).isEqualTo(5_000.0)

            assertThat(pending.amountOrNull).isNull()
            assertThat(pending.preview).isNull()
            assertThat(pending.canAdd).isFalse()
            assertThat(pending.amountTooMuch).isTrue()
            assertThat(pending.most).isEqualTo(5_000.0)
        }

        val adding = Adding(food = cucumber, countedAs = CountedAs.GRAMS, amount = "5000")
        assertThat(adding.amountTooMuch).isFalse()
        assertThat(adding.preview!!.kcal).isEqualTo(800)
        val pending = Pending(food = cucumber, countedAs = CountedAs.GRAMS, amount = "5000")
        assertThat(pending.amountTooMuch).isFalse()
        assertThat(pending.canAdd).isTrue()

        val tooMany = Pending(food = bar, countedAs = CountedAs.UNITS, amount = "101")
        assertThat(tooMany.canAdd).isFalse()
        assertThat(tooMany.amountTooMuch).isTrue()
        assertThat(tooMany.most).isEqualTo(100.0)
        assertThat(Adding(food = bar, countedAs = CountedAs.UNITS, amount = "101").amountTooMuch)
            .isTrue()

        val hundred = Adding(food = bar, countedAs = CountedAs.UNITS, amount = "100")
        assertThat(hundred.amountTooMuch).isFalse()
        assertThat(hundred.preview!!.kcal).isEqualTo(19_000)

        // The quiet cases stay quiet: a blank, a zero, a word or a half-typed number just leaves
        // the button off, as it always has.
        listOf("", "0", "0.", "a lot", "NaN").forEach { typed ->
            val quiet = Pending(food = cucumber, countedAs = CountedAs.GRAMS, amount = typed)

            assertThat(quiet.amountTooMuch).isFalse()
            assertThat(quiet.canAdd).isFalse()
        }
    }
}
