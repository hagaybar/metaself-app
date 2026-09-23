package com.metaself.app.ui.portion

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.weighing
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.domain.food.SavedMeals
import com.metaself.app.domain.portion.Portions
import org.junit.jupiter.api.Test

/**
 * The one rule for a count of the app's own "portion", for every screen that draws one (D37, #27).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two annotations look
 * identical at the call site and the wrong one produces a test that silently never runs.
 */
class PortionWordingTest {

    // --- The app's own "portion", counted (D37) ---------------------------------------------------

    /**
     * "Portion" is the app's own word for one of a food when nothing said what one is, so it is the
     * one unit the app knows the plural of. Asked of the row's unit and amount, and only when the
     * stored words are the app's own form of them — "2 portion", which every row logged so far
     * carries.
     */
    @Test
    fun `a row counted in the app's own portions gives its amount`() {
        assertThat(PortionWording.countedInPortions(2.0, "portion", "2 portion")).isEqualTo(2.0)
        assertThat(PortionWording.countedInPortions(2.0, " Portion ", "2 Portion")).isEqualTo(2.0)
    }

    /**
     * A unit the model or the owner named is not the app's to pluralise — it cannot know the plural
     * of a word it did not choose, least of all a Hebrew one — and a row with no amount has nothing
     * to count.
     */
    @Test
    fun `a unit someone else named, or no amount, is not the app's own portion`() {
        assertThat(PortionWording.countedInPortions(2.0, "slice", "2 slice")).isNull()
        assertThat(PortionWording.countedInPortions(0.0, "portion", null)).isNull()
        assertThat(PortionWording.countedInPortions(0.0, "", null)).isNull()
    }

    /**
     * The plural is built from the amount and unit, so it may only replace words that say nothing
     * more than they do. Words in any other form — recovered from older text, or ever written some
     * other way — are what the row says, and are drawn as stored (D5); the app's own "2 portion"
     * still becomes the plural.
     */
    @Test
    fun `a portion row whose words are not the app's own form keeps its words`() {
        assertThat(PortionWording.countedInPortions(2.0, "portion", "2 portion (large)")).isNull()
        assertThat(PortionWording.countedInPortions(1.0, "portion", "a portion")).isNull()
        assertThat(PortionWording.countedInPortions(2.0, "portion", null)).isNull()
        assertThat(PortionWording.countedInPortions(2.0, "portion", "2 portion")).isEqualTo(2.0)
    }

    /**
     * Android chooses a plural form from a whole number only. A part-portion is sent as 0, which
     * English reads with the "other" form — "1.5 portions" — and which is the one choice that must
     * never land on "one". Hebrew's own rule for fractions is for the translation (#9) to revisit.
     */
    @Test
    fun `the plural is chosen by the whole number, and a part-portion by the other form`() {
        assertThat(PortionWording.pluralQuantity(1.0)).isEqualTo(1)
        assertThat(PortionWording.pluralQuantity(2.0)).isEqualTo(2)
        assertThat(PortionWording.pluralQuantity(1.5)).isEqualTo(0)
        assertThat(PortionWording.pluralQuantity(0.5)).isEqualTo(0)
    }

    // --- A part of a meal he built: no words stored, only numbers (#27) ---------------------------

    /**
     * A meal's part stores an amount and what it is counted in, and no words at all. There is
     * nothing that could say more than the numbers, so the question is asked of the numbers alone —
     * comparing against words the app has just built itself would be a check that can only pass.
     */
    @Test
    fun `a part in the app's own portion is counted from its numbers alone`() {
        assertThat(PortionWording.inAppsOwnPortion(2.0, "portion")).isEqualTo(2.0)
        assertThat(PortionWording.inAppsOwnPortion(1.5, " Portion ")).isEqualTo(1.5)
        assertThat(PortionWording.inAppsOwnPortion(2.0, "slice")).isNull()
        assertThat(PortionWording.inAppsOwnPortion(100.0, "g")).isNull()
        assertThat(PortionWording.inAppsOwnPortion(0.0, "portion")).isNull()
    }

    /**
     * The part on Add something must read as the day row it becomes when the meal is logged. The
     * unit the screen draws and the unit logging puts on the row are worked out in two places, so
     * this pins them together for each way a part can be counted: in the food's own named unit, in
     * grams, and in the app's fallback "portion" for a food that knows only 100 g and a weight.
     *
     * The mixed foods are pinned too, because they are where logging's order of preference decides
     * the unit: a food that knows both 100 g and one of it is counted in its own unit and weighed in
     * grams, and a food known by the piece with a weight is still weighed in grams. A change to that
     * order in logging alone must fail here rather than leave the part reading one unit and the row
     * another.
     */
    @Test
    fun `a part reads in the unit it will be logged in`() {
        val rice = aFood(
            "Rice",
            FoodFacts(per100g = aPer100g(100.0), gramsPerUnit = weighing(150.0)),
        ).copy(id = 5)
        val cases = listOf(
            MealComponent(20, stew, 2.0, CountedAs.UNITS) to FoodFacts.PORTION,
            MealComponent(21, bread, 2.0, CountedAs.UNITS) to "slice",
            MealComponent(22, cucumber, 100.0, CountedAs.GRAMS) to "g",
            MealComponent(23, rice, 2.0, CountedAs.UNITS) to FoodFacts.PORTION,
            MealComponent(25, granola, 2.0, CountedAs.UNITS) to "bar",
            MealComponent(26, granola, 40.0, CountedAs.GRAMS) to "g",
            MealComponent(27, bagel, 90.0, CountedAs.GRAMS) to "g",
        )

        cases.forEach { (component, unit) ->
            assertThat(PortionWording.unitOf(component)).isEqualTo(unit)

            val logged = SavedMeals.toLoggableItems(
                SavedMeal(id = 1, name = "M", components = listOf(component)),
            ).single()
            assertThat(Portions.words(component.amount, PortionWording.unitOf(component)))
                .isEqualTo(logged.portion)
        }
    }

    /**
     * The amount and unit are known even when the worth is not, so a part that cannot be costed
     * still reads in a unit — the app's fallback word. Logging leaves such a part out, so there is
     * no row to compare it with.
     */
    @Test
    fun `a part that cannot be costed still has its unit`() {
        val breadByWeightOnly =
            aFood("Bread", FoodFacts(per100g = aPer100g(250.0))).copy(id = 9)
        val counted = MealComponent(24, breadByWeightOnly, 2.0, CountedAs.UNITS)

        assertThat(counted.cannotBeCosted).isTrue()
        assertThat(PortionWording.unitOf(counted)).isEqualTo(FoodFacts.PORTION)
    }

    private companion object {
        /** A food that knows only what a portion of it is worth. */
        val stew = aFood("Leftover stew", FoodFacts(perUnit = aPerUnit(FoodFacts.PORTION, 300.0)))
            .copy(id = 3)
        val bread = aFood("Bread", FoodFacts(perUnit = aPerUnit("slice", 80.0))).copy(id = 4)
        val cucumber = aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)

        /** A food that knows both what 100 g of it and what one bar of it are worth. */
        val granola = aFood(
            "Granola bar",
            FoodFacts(per100g = aPer100g(450.0), perUnit = aPerUnit("bar", 190.0)),
        ).copy(id = 6)

        /** A food known by the piece, with what one weighs, and nothing per 100 g. */
        val bagel = aFood(
            "Bagel",
            FoodFacts(perUnit = aPerUnit("bagel", 270.0), gramsPerUnit = weighing(90.0)),
        ).copy(id = 7)
    }
}
