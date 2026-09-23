package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MacroSplitTest {

    @Test
    fun `protein is one point eight grams per kilogram, rounded to five`() {
        // 80 * 1.8 = 144 -> 145
        assertThat(Macros.derive(targetKcal = 2150, weightKg = 80.0).proteinG).isEqualTo(145)
    }

    @Test
    fun `fat is zero point eight grams per kilogram, rounded to five`() {
        // 80 * 0.8 = 64 -> 65
        assertThat(Macros.derive(targetKcal = 2150, weightKg = 80.0).fatG).isEqualTo(65)
    }

    @Test
    fun `carbohydrate is what the calories leave over`() {
        // 2150 - (145 * 4) - (65 * 9) = 2150 - 580 - 585 = 985 kcal -> 246.25 g -> 245
        assertThat(Macros.derive(targetKcal = 2150, weightKg = 80.0).carbsG).isEqualTo(245)
    }

    @Test
    fun `carbohydrate never goes negative when protein and fat already fill the target`() {
        // 120 kg: protein 215 g (860 kcal), fat 95 g (855 kcal) = 1715 kcal against a 1500 target
        val split = Macros.derive(targetKcal = 1500, weightKg = 120.0)
        assertThat(split.carbsG).isEqualTo(0)
    }

    @Test
    fun `the three macros account for the target within one rounding step`() {
        val split = Macros.derive(targetKcal = 2150, weightKg = 80.0)
        val accounted = split.proteinG * 4 + split.carbsG * 4 + split.fatG * 9
        assertThat(accounted).isIn(2150 - 25..2150 + 25)
    }
}
