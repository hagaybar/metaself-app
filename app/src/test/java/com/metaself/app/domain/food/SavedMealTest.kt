package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * A meal the owner built, and what logging it puts on the record.
 *
 * Two things are being protected. A meal has **no stored total**: what it is worth is the sum over
 * its parts as they stand, so correcting a food moves the definition and moves nothing already
 * logged. And **nothing learns**: whether a logging differed from the meal is answered once, at the
 * moment of logging, and is never counted or turned into an offer to change the meal.
 */
class SavedMealTest {

    private val cucumber = Food(
        id = 1,
        name = "Cucumber",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(16.0, 0.7, 3.6, 0.1),
                Provenance(Source.TYPED, null, 0),
            ),
        ),
    )

    private val oil = Food(
        id = 2,
        name = "Olive oil",
        facts = FoodFacts(
            perUnit = PerUnit(
                "spoon",
                Nutrients(119.0, 0.0, 0.0, 13.5),
                Provenance(Source.TYPED, null, 0),
            ),
        ),
    )

    private fun salad() = SavedMeal(
        id = 1,
        name = "Vegetable salad",
        components = listOf(
            MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
            MealComponent(11, oil, 1.0, CountedAs.UNITS, position = 1),
        ),
    )

    // --- What it is worth ---------------------------------------------------------------------------

    @Test
    fun `a meal is worth the sum of its parts as they stand`() {
        assertThat(salad().kcal).isEqualTo(135)
    }

    /**
     * A meal's totals move as the foods in it are corrected, and that is the design rather than a
     * bug: what a day's row shows was frozen when it was logged, and what the definition shows is
     * today's sum over today's foods.
     */
    @Test
    fun `correcting a food moves what the meal is worth`() {
        val corrected = cucumber.copy(
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(20.0, 0.7, 3.6, 0.1),
                    Provenance(Source.TYPED, null, 0),
                ),
            ),
        )

        val after = salad().copy(
            components = salad().components.map {
                if (it.food.id == 1L) it.copy(food = corrected) else it
            },
        )

        assertThat(after.kcal).isEqualTo(139)
    }

    @Test
    fun `a meal with nothing in it yet is a meal he has not finished`() {
        val started = SavedMeal(id = 1, name = "Salad")

        assertThat(started.isEmpty).isTrue()
        assertThat(started.kcal).isEqualTo(0)
    }

    @Test
    fun `a meal the owner built has a name he gave it`() {
        assertThrows<IllegalArgumentException> { SavedMeal(name = "  ") }
    }

    @Test
    fun `a meal cannot contain none of something`() {
        assertThrows<IllegalArgumentException> {
            MealComponent(food = cucumber, amount = 0.0, countedAs = CountedAs.GRAMS)
        }
    }

    /**
     * The one residue of the old guard column: a component counting a food in units whose per-unit
     * numbers have gone cannot cost itself. Said rather than quietly counted as nothing.
     */
    @Test
    fun `a component whose food no longer knows its unit says so`() {
        val breadWeighedOnly = Food(
            id = 3,
            name = "Bread",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(250.0, 8.0, 45.0, 3.0),
                    Provenance(Source.TYPED, null, 0),
                ),
            ),
        )
        val meal = SavedMeal(
            id = 1,
            name = "Sandwich",
            components = listOf(MealComponent(1, breadWeighedOnly, 2.0, CountedAs.UNITS)),
        )

        assertThat(meal.components.single().cannotBeCosted).isTrue()
        assertThat(meal.incomplete).isTrue()
    }

    // --- What logging it puts on the record ------------------------------------------------------------

    @Test
    fun `logging a meal freezes each part's numbers onto its own row`() {
        val items = SavedMeals.toLoggableItems(salad())

        assertThat(items.map { it.name }).containsExactly("Cucumber", "Olive oil").inOrder()
        assertThat(items.first().kcal).isEqualTo(16)
        assertThat(items.first().portion).isEqualTo("100 g")
        assertThat(items.last().kcal).isEqualTo(119)
        assertThat(items.last().portion).isEqualTo("1 spoon")
    }

    @Test
    fun `every logged row points at the food it came from`() {
        assertThat(SavedMeals.toLoggableItems(salad()).map { it.foodId })
            .containsExactly(1L, 2L)
    }

    @Test
    fun `the parts are logged in the order he arranged them`() {
        val reversed = salad().copy(
            components = listOf(
                salad().components[0].copy(position = 1),
                salad().components[1].copy(position = 0),
            ),
        )

        assertThat(SavedMeals.toLoggableItems(reversed).map { it.name })
            .containsExactly("Olive oil", "Cucumber").inOrder()
    }

    /**
     * A zero on the record would claim he ate something worth nothing, which is a different and
     * worse falsehood than a row that is not there.
     */
    @Test
    fun `a part that cannot be costed is left off rather than logged as nothing`() {
        val breadWeighedOnly = Food(
            id = 3,
            name = "Bread",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(250.0, 8.0, 45.0, 3.0),
                    Provenance(Source.TYPED, null, 0),
                ),
            ),
        )
        val meal = salad().copy(
            components = salad().components +
                MealComponent(12, breadWeighedOnly, 2.0, CountedAs.UNITS, position = 2),
        )

        assertThat(SavedMeals.toLoggableItems(meal).map { it.name })
            .containsExactly("Cucumber", "Olive oil")
    }

    // --- Whether it was adjusted ---------------------------------------------------------------------------

    @Test
    fun `a meal logged as it stands was not adjusted`() {
        assertThat(SavedMeals.wasAdjusted(salad(), salad().components)).isFalse()
    }

    @Test
    fun `a different amount is an adjustment`() {
        val doubled = salad().components.map {
            if (it.id == 10L) it.copy(amount = 200.0) else it
        }

        assertThat(SavedMeals.wasAdjusted(salad(), doubled)).isTrue()
    }

    @Test
    fun `something dropped is an adjustment`() {
        assertThat(SavedMeals.wasAdjusted(salad(), salad().components.take(1))).isTrue()
    }

    /** Decision 15: an adjustment may ADD as well as remove and rescale. */
    @Test
    fun `something added that is not in the meal at all is an adjustment`() {
        val withBread = salad().components + MealComponent(
            id = -1,
            food = Food(
                id = 4,
                name = "Bread",
                facts = FoodFacts(
                    per100g = PerHundredGrams(
                        Nutrients(250.0, 8.0, 45.0, 3.0),
                        Provenance(Source.TYPED, null, 0),
                    ),
                ),
            ),
            amount = 60.0,
            countedAs = CountedAs.GRAMS,
            position = 2,
        )

        assertThat(SavedMeals.wasAdjusted(salad(), withBread)).isTrue()
    }

    @Test
    fun `counting the same food a different way is an adjustment`() {
        val bothWays = cucumber.copy(
            facts = cucumber.facts.copy(
                perUnit = PerUnit(
                    "whole",
                    Nutrients(45.0, 2.0, 10.0, 0.3),
                    Provenance(Source.TYPED, null, 0),
                ),
            ),
        )
        val meal = salad().copy(
            components = listOf(MealComponent(10, bothWays, 1.0, CountedAs.GRAMS)),
        )
        val countedInstead = listOf(MealComponent(10, bothWays, 1.0, CountedAs.UNITS))

        assertThat(SavedMeals.wasAdjusted(meal, countedInstead)).isTrue()
    }
}
