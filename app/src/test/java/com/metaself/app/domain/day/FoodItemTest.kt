package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FoodItemTest {

    @Test
    fun `a typed item is simply the owner's number`() {
        val item = anItem(source = Source.TYPED, confidence = null)
        assertThat(item.source).isEqualTo(Source.TYPED)
        assertThat(item.confidence).isNull()
    }

    @Test
    fun `a typed item cannot carry a confidence, because it is not a guess`() {
        try {
            anItem(source = Source.TYPED, confidence = Confidence.HIGH)
            throw AssertionError("expected a typed item with a confidence to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("typed")
        }
    }

    @Test
    fun `an estimate cannot be stored without saying how sure it was`() {
        try {
            anItem(source = Source.AI_ESTIMATE, confidence = null)
            throw AssertionError("expected an estimate with no confidence to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("estimate")
        }
    }

    @Test
    fun `an estimate carries its confidence`() {
        val item = anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)
        assertThat(item.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `a repeated meal may or may not remember how sure the original was`() {
        assertThat(anItem(source = Source.REPEATED, confidence = null).confidence).isNull()
        assertThat(anItem(source = Source.REPEATED, confidence = Confidence.MEDIUM).confidence)
            .isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `a name is required, because a nameless thing eaten is not a record`() {
        try {
            anItem(name = "   ")
            throw AssertionError("expected a blank name to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("name")
        }
    }

    @Test
    fun `numbers cannot be negative`() {
        try {
            anItem(kcal = -1)
            throw AssertionError("expected a negative calorie count to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("negative")
        }
    }
}
