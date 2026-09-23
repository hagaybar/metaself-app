package com.metaself.app.domain.ai

import com.google.common.truth.Truth.assertThat
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

    @Test
    fun `the whole proposal adds up, so the owner can see the meal as well as its parts`() {
        assertThat(aProposal().totalKcal).isEqualTo(685)
    }

    @Test
    fun `an accepted item is an AI estimate, and keeps the confidence the model gave it`() {
        val item = aProposedItem(confidence = Confidence.LOW).toFoodItem()

        assertThat(item.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(item.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `an accepted item carries the assumed portion, because that is what makes it arguable`() {
        assertThat(aProposedItem(portion = "~280 g").toFoodItem().portion).isEqualTo("~280 g")
    }
}
