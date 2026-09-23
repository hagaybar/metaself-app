package com.metaself.app.data.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.ImpossibleFigures.Group
import org.junit.jupiter.api.Test

/**
 * Which of a stored food's number groups hold a figure today's rule would refuse (issue #7).
 *
 * The rule is D42's, from `BelievableAmount`, applied per group with that group's ceilings. Only a
 * group holding such a figure is named; everything else about the food is left alone. Invented
 * figures throughout.
 */
class ImpossibleFiguresTest {

    @Test
    fun `a food whose figures are all believable has nothing to clear`() {
        assertThat(ImpossibleFigures.of(aFoodRow())).isEmpty()
    }

    @Test
    fun `an infinite figure per 100 g names that group only`() {
        val food = aFoodRow().copy(kcalPer100g = Double.POSITIVE_INFINITY)

        assertThat(ImpossibleFigures.of(food)).containsExactly(Group.PER_100G)
    }

    /** "1e12" was a number to the old food form: finite, and past every ceiling. */
    @Test
    fun `a finite figure past its per-100 g ceiling names that group`() {
        assertThat(ImpossibleFigures.of(aFoodRow().copy(fatPer100g = 1e12)))
            .containsExactly(Group.PER_100G)
        assertThat(ImpossibleFigures.of(aFoodRow().copy(proteinPer100g = 110.5)))
            .containsExactly(Group.PER_100G)
    }

    /** A ceiling is a value he can type, so a food holding exactly one keeps it. */
    @Test
    fun `a figure exactly at its ceiling is kept`() {
        val food = aFoodRow().copy(
            kcalPer100g = 1000.0,
            fatPer100g = 110.0,
            kcalPerUnit = 5000.0,
            carbsPerUnit = 500.0,
            gramsPerUnit = 5000.0,
        )

        assertThat(ImpossibleFigures.of(food)).isEmpty()
    }

    /**
     * The per-unit ceilings are per one of something, not per 100 g: a 1500 kcal tray is believable
     * though no 100 g of anything is.
     */
    @Test
    fun `the per-unit group is judged by the per-unit ceilings`() {
        assertThat(ImpossibleFigures.of(aFoodRow().copy(kcalPerUnit = 1500.0))).isEmpty()
        assertThat(ImpossibleFigures.of(aFoodRow().copy(kcalPerUnit = 5000.5)))
            .containsExactly(Group.PER_UNIT)
        assertThat(ImpossibleFigures.of(aFoodRow().copy(fatPerUnit = Double.POSITIVE_INFINITY)))
            .containsExactly(Group.PER_UNIT)
    }

    @Test
    fun `an impossible weight of one names only the weight`() {
        assertThat(ImpossibleFigures.of(aFoodRow().copy(gramsPerUnit = 1e9)))
            .containsExactly(Group.GRAMS_PER_UNIT)
    }

    @Test
    fun `a negative figure is no quantity of food either`() {
        assertThat(ImpossibleFigures.of(aFoodRow().copy(carbsPer100g = -3.0)))
            .containsExactly(Group.PER_100G)
        assertThat(ImpossibleFigures.of(aFoodRow().copy(kcalPerUnit = Double.NEGATIVE_INFINITY)))
            .containsExactly(Group.PER_UNIT)
    }

    @Test
    fun `every group can fail at once`() {
        val food = aFoodRow().copy(
            kcalPer100g = Double.POSITIVE_INFINITY,
            proteinPerUnit = 9000.0,
            gramsPerUnit = Double.POSITIVE_INFINITY,
        )

        assertThat(ImpossibleFigures.of(food))
            .containsExactly(Group.PER_100G, Group.PER_UNIT, Group.GRAMS_PER_UNIT)
    }

    /**
     * A blank is "not known" (D4), never impossible. A food that knows only one group is not made
     * to lose it for the blanks in the others.
     */
    @Test
    fun `blank figures are never impossible`() {
        val onlyPer100g = aFoodRow().copy(
            unitName = null,
            kcalPerUnit = null,
            proteinPerUnit = null,
            carbsPerUnit = null,
            fatPerUnit = null,
            perUnitSource = null,
            gramsPerUnit = null,
            gramsPerUnitSource = null,
        )

        assertThat(ImpossibleFigures.of(onlyPer100g)).isEmpty()
    }

    /** Half a group is unreadable already; it is named only when what it does hold is impossible. */
    @Test
    fun `a half-filled group is named only for an impossible figure it holds`() {
        assertThat(ImpossibleFigures.of(aFoodRow().copy(proteinPer100g = null))).isEmpty()
        assertThat(
            ImpossibleFigures.of(
                aFoodRow().copy(proteinPer100g = null, kcalPer100g = Double.POSITIVE_INFINITY),
            ),
        ).containsExactly(Group.PER_100G)
    }

    /** Invented, believable figures in all three groups, so each test breaks exactly one thing. */
    private fun aFoodRow() = FoodEntity(
        id = 1,
        createdAtMillis = 1_000,
        updatedAtMillis = 2_000,
        kcalPer100g = 422.0,
        proteinPer100g = 33.0,
        carbsPer100g = 38.0,
        fatPer100g = 14.0,
        per100gSource = "LABEL",
        per100gSourceRank = 3,
        per100gSetAtMillis = 1_000,
        unitName = "bar",
        kcalPerUnit = 190.0,
        proteinPerUnit = 15.0,
        carbsPerUnit = 17.0,
        fatPerUnit = 6.0,
        perUnitSource = "TYPED",
        perUnitSourceRank = 2,
        perUnitSetAtMillis = 1_000,
        gramsPerUnit = 45.0,
        gramsPerUnitSource = "TYPED",
        gramsPerUnitSourceRank = 2,
        gramsPerUnitSetAtMillis = 1_000,
    )
}
