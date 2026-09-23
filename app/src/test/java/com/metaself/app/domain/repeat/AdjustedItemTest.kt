package com.metaself.app.domain.repeat

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

class AdjustedItemTest {

    private val twoSlices = FoodItem(
        name = "Pizza",
        portion = "2 slice",
        portionAmount = 2.0,
        portionUnit = "slice",
        kcal = 570,
        proteinG = 24,
        carbsG = 68,
        fatG = 22,
        source = Source.AI_ESTIMATE,
        confidence = Confidence.MEDIUM,
    )

    private val risotto = twoSlices.copy(
        name = "Risotto",
        portion = "~280 g",
        portionAmount = 280.0,
        portionUnit = "g",
        kcal = 600,
        proteinG = 18,
        carbsG = 80,
        fatG = 22,
    )

    /** Two portions made one. */
    @Test
    fun `one slice instead of two halves everything`() {
        val one = twoSlices.withAmount(1.0)

        assertThat(one.portion).isEqualTo("1 slice")
        assertThat(one.kcal).isEqualTo(285)
        assertThat(one.proteinG).isEqualTo(12)
        assertThat(one.carbsG).isEqualTo(34)
        assertThat(one.fatG).isEqualTo(11)
    }

    @Test
    fun `half a dish`() {
        val half = risotto.scaledBy(0.5)

        assertThat(half.portion).isEqualTo("140 g")
        assertThat(half.kcal).isEqualTo(300)
    }

    /** Scaling from the item as logged, every time, so "as logged" is exactly where it started. */
    @Test
    fun `going back arrives at the original and not near it`() {
        assertThat(twoSlices.scaledBy(1.0)).isEqualTo(twoSlices)
        assertThat(twoSlices.withAmount(3.0).let { twoSlices.withAmount(2.0) }).isEqualTo(twoSlices)
    }

    /** An amount of nothing is not a portion: the item is left as it was, not scaled to zero. */
    @Test
    fun `an amount of nothing leaves the item as it was`() {
        assertThat(twoSlices.withAmount(0.0)).isEqualTo(twoSlices)
        assertThat(twoSlices.withAmount(-4.0)).isEqualTo(twoSlices)
    }

    /**
     * Everything logged before the portion's numbers were kept, and anything the model could not put
     * a number to. It must come back untouched rather than scaled by a guess.
     */
    @Test
    fun `an item with no recorded amount cannot be adjusted at all`() {
        val old = twoSlices.copy(portion = "2 slice", portionAmount = 0.0, portionUnit = "")

        assertThat(old.canBeAdjusted()).isFalse()
        assertThat(old.withAmount(1.0)).isEqualTo(old)
        assertThat(old.scaledBy(0.5)).isEqualTo(old)
    }

    /** Adjusting changes the amount, never where the numbers came from (D4). */
    @Test
    fun `the source and the confidence are untouched`() {
        val one = twoSlices.withAmount(1.0)

        assertThat(one.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(one.confidence).isEqualTo(Confidence.MEDIUM)
    }
}
