package com.metaself.app.domain.amount

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.amount.BelievableAmount.Verdict
import com.metaself.app.domain.food.CountedAs
import org.junit.jupiter.api.Test

/**
 * The one rule every number box asks after parsing (D42, issue #32). Pure, so JUnit 5 and Truth —
 * `org.junit.jupiter.api.Test`, never `org.junit.Test`: the two look identical at the call site and
 * the wrong one gives a test that silently never runs.
 */
class BelievableAmountTest {

    private val everyCeiling = listOf(
        BelievableAmount.KCAL_PER_100G,
        BelievableAmount.MACRO_PER_100G,
        BelievableAmount.KCAL_PER_UNIT,
        BelievableAmount.MACRO_PER_UNIT,
        BelievableAmount.GRAMS,
        BelievableAmount.COUNT,
    )

    /**
     * The edges, for every ceiling: exactly at it is believable, the next double past it is not,
     * and so is every way a paste can spell "too much" — "Infinity", "1e999" (which overflows to
     * the same thing) and "1e300" (finite, absurd). Not-a-number is its own verdict, so a box can
     * say something different about a word than about a slipped finger. Minus zero is zero (D39).
     */
    @Test
    fun `a value at its ceiling is believable and one past it is not`() {
        for (most in everyCeiling) {
            assertThat(BelievableAmount.judge(most, most)).isEqualTo(Verdict.BELIEVABLE)
            assertThat(BelievableAmount.judge(0.0, most)).isEqualTo(Verdict.BELIEVABLE)
            assertThat(BelievableAmount.judge(-0.0, most)).isEqualTo(Verdict.BELIEVABLE)

            assertThat(BelievableAmount.judge(Math.nextUp(most), most)).isEqualTo(Verdict.TOO_MUCH)
            assertThat(BelievableAmount.judge(Double.POSITIVE_INFINITY, most))
                .isEqualTo(Verdict.TOO_MUCH)
            assertThat(BelievableAmount.judge("1e999".toDouble(), most)).isEqualTo(Verdict.TOO_MUCH)
            assertThat(BelievableAmount.judge(1e300, most)).isEqualTo(Verdict.TOO_MUCH)

            assertThat(BelievableAmount.judge(Double.NaN, most)).isEqualTo(Verdict.NOT_A_NUMBER)

            assertThat(BelievableAmount.judge(-0.1, most)).isEqualTo(Verdict.NEGATIVE)
            assertThat(BelievableAmount.judge(Double.NEGATIVE_INFINITY, most))
                .isEqualTo(Verdict.NEGATIVE)

            assertThat(BelievableAmount.isBelievable(most, most)).isTrue()
            assertThat(BelievableAmount.isBelievable(Math.nextUp(most), most)).isFalse()
            assertThat(BelievableAmount.isBelievable(Double.NaN, most)).isFalse()
            assertThat(BelievableAmount.isBelievable(-0.1, most)).isFalse()

            // Too much is only too much: a word or a negative is refused, but not for this reason,
            // which is what lets an amount box stay quiet while "0." is on its way to "0.5".
            assertThat(BelievableAmount.isTooMuch(Math.nextUp(most), most)).isTrue()
            assertThat(BelievableAmount.isTooMuch(Double.POSITIVE_INFINITY, most)).isTrue()
            assertThat(BelievableAmount.isTooMuch(most, most)).isFalse()
            assertThat(BelievableAmount.isTooMuch(Double.NaN, most)).isFalse()
            assertThat(BelievableAmount.isTooMuch(-1.0, most)).isFalse()
        }
    }

    /**
     * Pinned so that changing a ceiling is a visible edit here and to D42, never a quiet tweak of a
     * constant. The entry ceilings are whole numbers because that form reads whole numbers.
     */
    @Test
    fun `the ceilings are the decision's numbers`() {
        assertThat(BelievableAmount.KCAL_PER_100G).isEqualTo(1_000.0)
        assertThat(BelievableAmount.MACRO_PER_100G).isEqualTo(110.0)
        assertThat(BelievableAmount.KCAL_PER_UNIT).isEqualTo(5_000.0)
        assertThat(BelievableAmount.MACRO_PER_UNIT).isEqualTo(500.0)
        assertThat(BelievableAmount.GRAMS).isEqualTo(5_000.0)
        assertThat(BelievableAmount.COUNT).isEqualTo(100.0)
        assertThat(BelievableAmount.ENTRY_KCAL).isEqualTo(10_000)
        assertThat(BelievableAmount.ENTRY_MACRO_G).isEqualTo(1_000)
    }

    /**
     * 5000 of something weighed, 100 of something counted. A logged row names its unit in words,
     * so the split reuses the one list of what counts as a mass — a second list would drift.
     */
    @Test
    fun `an amount eaten is capped by how it is counted`() {
        assertThat(BelievableAmount.amountEaten(CountedAs.GRAMS)).isEqualTo(5_000.0)
        assertThat(BelievableAmount.amountEaten(CountedAs.UNITS)).isEqualTo(100.0)

        assertThat(BelievableAmount.amountIn("g")).isEqualTo(5_000.0)
        assertThat(BelievableAmount.amountIn("grams")).isEqualTo(5_000.0)
        assertThat(BelievableAmount.amountIn("ml")).isEqualTo(5_000.0)
        assertThat(BelievableAmount.amountIn("slice")).isEqualTo(100.0)
        assertThat(BelievableAmount.amountIn("portion")).isEqualTo(100.0)
    }

    /** "At most 1000", never "at most 1000.0" nor "1E+3": the refusal is read by a person. */
    @Test
    fun `a ceiling is written as a person writes it`() {
        assertThat(BelievableAmount.words(1_000.0)).isEqualTo("1000")
        assertThat(BelievableAmount.words(110.0)).isEqualTo("110")
        assertThat(BelievableAmount.words(900.0)).isEqualTo("900")
        assertThat(BelievableAmount.words(5_000.0)).isEqualTo("5000")
        assertThat(BelievableAmount.words(100.0)).isEqualTo("100")
        assertThat(BelievableAmount.words(500.0)).isEqualTo("500")
    }
}
