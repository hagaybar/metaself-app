package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
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
}
