package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FoodRetaught
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.Replaced
import org.junit.jupiter.api.Test

/**
 * The sentence the app says when a food's own figures were replaced (issue #13, D45).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. Shaped like
 * `RevisionWordingTest`: data in, one string out, and the string asserted exactly, because the
 * whole point of the notice is that he can read what changed without opening anything.
 */
class RetaughtWordingTest {

    private fun per100g(
        kcal: Double,
        proteinG: Double = 1.0,
        carbsG: Double = 3.0,
        fatG: Double = 0.1,
        setAtMillis: Long = 1_000,
    ) = PerHundredGrams(
        Nutrients(kcal, proteinG, carbsG, fatG),
        Provenance(Source.TYPED, null, setAtMillis),
    )

    private fun perUnit(unitName: String, kcal: Double, setAtMillis: Long = 1_000) =
        PerUnit(unitName, Nutrients(kcal, 15.0, 17.0, 6.0), Provenance(Source.TYPED, null, setAtMillis))

    private fun weighs(grams: Double, setAtMillis: Long = 1_000) =
        GramsPerUnit(grams, Provenance(Source.TYPED, null, setAtMillis))

    private fun cucumberFrom15To18() = FoodRetaught(
        "Cucumber",
        listOf(Replaced.Per100g(per100g(15.0), per100g(18.0, setAtMillis = 2_000))),
    )

    /**
     * The whole sentence, exactly as the day screen draws it.
     *
     * The assertion is on the exact string rather than on fragments, which is also where "no
     * identifier reaches a sentence" is proved: an id could not appear here without this failing,
     * and `FoodRetaught` has no id field to leak in the first place.
     */
    @Test
    fun `one food and one figure reads as one sentence`() {
        val notice = RetaughtWording.notice(
            listOf(cucumberFrom15To18()),
            RetaughtBecause.JUST_LOGGED,
        )

        assertThat(notice).isEqualTo(
            "“Cucumber” now counts 18 kcal per 100 g, where it counted 15, " +
                "because you have just logged it with different numbers.",
        )
    }

    /**
     * Three facts of one food moved, and he is told once rather than three times — the food named
     * once, the phrases joined into a single sentence.
     */
    @Test
    fun `one food whose three facts all moved is named once, in one sentence`() {
        val notice = RetaughtWording.notice(
            listOf(
                FoodRetaught(
                    "Protein bar",
                    listOf(
                        Replaced.Per100g(per100g(400.0), per100g(420.0, setAtMillis = 2_000)),
                        Replaced.PerOne(perUnit("bar", 190.0), perUnit("bar", 200.0, 2_000)),
                        Replaced.WhatOneWeighs("bar", weighs(45.0), weighs(50.0, 2_000)),
                    ),
                ),
            ),
            RetaughtBecause.JUST_LOGGED,
        )

        assertThat(notice).isEqualTo(
            "“Protein bar” now counts 420 kcal per 100 g, where it counted 400, " +
                "and counts 200 kcal per bar, where it counted 190, " +
                "and says one bar weighs 50 g, where it said 45, " +
                "because you have just logged it with different numbers.",
        )
        assertThat(Regex("Protein bar").findAll(notice!!).count()).isEqualTo(1)
        assertThat(notice.lines()).hasSize(1)
    }

    /** One meal can change two foods. Each gets its own line; neither is folded into the other. */
    @Test
    fun `two foods get one line each`() {
        val notice = RetaughtWording.notice(
            listOf(
                cucumberFrom15To18(),
                FoodRetaught(
                    "Bread",
                    listOf(Replaced.Per100g(per100g(250.0), per100g(265.0, setAtMillis = 2_000))),
                ),
            ),
            RetaughtBecause.JUST_LOGGED,
        )

        assertThat(notice!!.lines()).containsExactly(
            "“Cucumber” now counts 18 kcal per 100 g, where it counted 15, " +
                "because you have just logged it with different numbers.",
            "“Bread” now counts 265 kcal per 100 g, where it counted 250, " +
                "because you have just logged it with different numbers.",
        ).inOrder()
    }

    /** Nothing replaced is nothing to say, and silence is a null rather than an empty line. */
    @Test
    fun `nothing replaced says nothing at all`() {
        assertThat(RetaughtWording.notice(emptyList(), RetaughtBecause.JUST_LOGGED)).isNull()
    }

    /**
     * The figures moved but both round to the same calorie count, so the sentence says what really
     * happened instead of reading "from 15 to 15" — which would look like a defect in the app.
     */
    @Test
    fun `figures that round to the same calories are not reported as a change of calories`() {
        val notice = RetaughtWording.notice(
            listOf(
                FoodRetaught(
                    "Cucumber",
                    listOf(
                        Replaced.Per100g(
                            per100g(15.2, proteinG = 1.0),
                            per100g(15.4, proteinG = 2.0, setAtMillis = 2_000),
                        ),
                    ),
                ),
            ),
            RetaughtBecause.JUST_LOGGED,
        )

        assertThat(notice).isEqualTo(
            "“Cucumber” now counts the same 15 kcal per 100 g from different figures, " +
                "because you have just logged it with different numbers.",
        )
        assertThat(notice).doesNotContain("where it counted 15")
    }

    /**
     * The same replacement, reached by typing the numbers into *make a food* mid-meal: one wording
     * function, one argument, and no second copy of the sentence to drift out of step.
     */
    @Test
    fun `typing the numbers closes the sentence differently from logging`() {
        val notice = RetaughtWording.notice(
            listOf(cucumberFrom15To18()),
            RetaughtBecause.JUST_TYPED,
        )

        assertThat(notice).isEqualTo(
            "“Cucumber” now counts 18 kcal per 100 g, where it counted 15, " +
                "because you have just typed different numbers for it.",
        )
    }
}
