package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import java.util.Locale
import org.junit.jupiter.api.Test

/**
 * How a food's calories are printed, wherever they are printed (issue #13, D45).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. Only the one rule the
 * food list and the "this food's figures changed" sentence now share is pinned here: they round
 * and group identically, so one figure cannot read 15 in the list and 15.4 in the sentence, and a
 * device locale cannot make the two sentences beside each other on the day screen separate
 * thousands two different ways.
 */
class FoodWordingTest {

    @Test
    fun `a figure is printed as a whole number`() {
        assertThat(FoodWording.grouped(15.4)).isEqualTo("15")
        assertThat(FoodWording.grouped(15.6)).isEqualTo("16")
        assertThat(FoodWording.grouped(999.4)).isEqualTo("999")
    }

    @Test
    fun `four digits are grouped the same way whatever the device is set to`() {
        val was = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertThat(FoodWording.grouped(1_234.0)).isEqualTo("1,234")
        } finally {
            Locale.setDefault(was)
        }
    }

    // --- The summary line (D55 §1) --------------------------------------------------------------
    //
    // The foods and figures are D55's invented examples. A list of parts, never one string: a
    // Hebrew brand and a Latin figure in one string may be reordered by the bidi algorithm.

    private fun label() = Provenance(Source.LABEL, null, setAtMillis = 0)

    private fun typed() = Provenance(Source.TYPED, null, setAtMillis = 0)

    private val greekYoghurt = Food(
        name = "Greek yoghurt",
        facts = FoodFacts(
            per100g = PerHundredGrams(Nutrients(100.0, 8.0, 4.0, 5.0), label()),
            perUnit = PerUnit("cup", Nutrients(150.0, 12.0, 6.0, 7.5), label()),
            gramsPerUnit = GramsPerUnit(150.0, typed()),
        ),
    )

    @Test
    fun `a food knowing both ways of counting is summed up per 100 g, with what one weighs`() {
        assertThat(FoodWording.summary(greekYoghurt))
            .containsExactly("No brand", "100 kcal per 100 g", "one cup is 150 g").inOrder()
    }

    @Test
    fun `a branded food whose portion has no name weighs one portion`() {
        val biscuit = Food(
            name = "Oat biscuit",
            brand = "Examplebrand",
            facts = FoodFacts(
                per100g = PerHundredGrams(Nutrients(450.0, 8.0, 60.0, 20.0), label()),
                gramsPerUnit = GramsPerUnit(12.0, typed()),
            ),
        )

        assertThat(FoodWording.summary(biscuit))
            .containsExactly("Examplebrand", "450 kcal per 100 g", "one portion is 12 g").inOrder()
    }

    @Test
    fun `a food knowing only per 100 g says nothing about weight`() {
        val soup = Food(
            name = "Lentil soup",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(90.0, 5.0, 13.0, 2.0),
                    Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 0),
                ),
            ),
        )

        assertThat(FoodWording.summary(soup))
            .containsExactly("No brand", "90 kcal per 100 g").inOrder()
    }

    @Test
    fun `a food knowing only per one is summed up per one`() {
        val bun = Food(
            name = "Hamburger bun",
            facts = FoodFacts(perUnit = PerUnit("bun", Nutrients(150.0, 5.0, 28.0, 2.0), typed())),
            hidden = true,
        )

        assertThat(FoodWording.summary(bun)).containsExactly("No brand", "150 kcal per bun").inOrder()
    }

    /** Invented: a bun said to weigh 60 g, to show per one and a weight together. */
    @Test
    fun `a food knowing per one and a weight gives both`() {
        val bun = Food(
            name = "Hamburger bun",
            facts = FoodFacts(
                perUnit = PerUnit("bun", Nutrients(150.0, 5.0, 28.0, 2.0), typed()),
                gramsPerUnit = GramsPerUnit(60.0, typed()),
            ),
        )

        assertThat(FoodWording.summary(bun))
            .containsExactly("No brand", "150 kcal per bun", "one bun is 60 g").inOrder()
    }

    @Test
    fun `the brand that means none is never printed`() {
        for (none in listOf("NA", "na", "N/A", "N.A.")) {
            val parts = FoodWording.summary(greekYoghurt.copy(brand = none))
            assertThat(parts.first()).isEqualTo("No brand")
            assertThat(parts.none { it.contains(none) }).isTrue()
        }
    }

    /** Invented: a tray worth 1,204 kcal, to show the summary groups as every figure is grouped. */
    @Test
    fun `a thousand-calorie figure is grouped in the summary`() {
        val tray = Food(
            name = "Lasagne tray",
            facts = FoodFacts(perUnit = PerUnit("tray", Nutrients(1_204.0, 60.0, 110.0, 55.0), typed())),
        )

        assertThat(FoodWording.summary(tray)).contains("${FoodWording.grouped(1_204.0)} kcal per tray")
        assertThat(FoodWording.summary(tray)).contains("1,204 kcal per tray")
    }

    /** D56. Invented: a carton's 57 kcal per 100 ml, stored per ml as D53 §3 stores it. */
    @Test
    fun `a food counted in ml says what 100 ml of it are worth`() {
        val drink = Food(
            name = "Oat drink",
            facts = FoodFacts(perUnit = PerUnit("ml", Nutrients(0.57, 0.029, 0.047, 0.036), label())),
        )

        assertThat(FoodWording.summary(drink)).contains("57 kcal per 100 ml")
        assertThat(FoodWording.whatItKnows(drink)).containsExactly("57 kcal per 100 ml")
    }
}
