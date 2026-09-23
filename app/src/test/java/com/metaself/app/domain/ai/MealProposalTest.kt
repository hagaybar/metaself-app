package com.metaself.app.domain.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

class MealProposalTest {

    @Test
    fun `a proposal is a list of components, never a single total`() {
        assertThat(aProposal().items).hasSize(2)
    }

    @Test
    fun `a proposal with nothing in it is not a proposal`() {
        try {
            MealProposal(items = emptyList(), note = null)
            throw AssertionError("expected an empty proposal to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("empty")
        }
    }

    /** 250 per 100 g at 200 g: 500 kcal · P 36 · C 0 · F 40, an estimate either way (D53 §3). */
    @Test
    fun `an item to log starts as the estimate, at the model's amount`() {
        val item = aProposedItem().toItemToLog()

        assertThat(item.amountText).isEqualTo("200")
        assertThat(item.unit).isEqualTo("g")
        assertThat(item.worth)
            .isEqualTo(Worth.Estimated(aProposedItem().rate, Confidence.MEDIUM))
        assertThat(item.foodId).isNull()
        val row = item.toFoodItem()!!
        assertThat(listOf(row.kcal, row.proteinG, row.carbsG, row.fatG))
            .containsExactly(500, 36, 0, 40).inOrder()
        assertThat(row.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(row.confidence).isEqualTo(Confidence.MEDIUM)
    }

    /** The detail goes into the row's portion words, beside the amount (D53, what does not change). */
    @Test
    fun `the detail is kept beside the amount`() {
        assertThat(aBun().toItemToLog().toFoodItem()!!.portion).isEqualTo("1 bun (sesame, toasted)")
    }

    /**
     * The box holds the model's amount as it was stated: a quarter is not silently made 0.3 by
     * the one-decimal formatting the day's words use.
     */
    @Test
    fun `the model's amount goes into the box unrounded`() {
        assertThat(aProposedItem(amount = 0.25, unit = "cup").toItemToLog().amountText)
            .isEqualTo("0.25")
    }
}
