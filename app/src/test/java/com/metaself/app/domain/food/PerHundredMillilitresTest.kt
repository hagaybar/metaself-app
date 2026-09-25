package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * D56: a food counted in millilitres states its per-one worth per 100 ml on screen, and keeps it per
 * one ml in storage. The figures below are invented; each is picked to catch a float artefact.
 */
class PerHundredMillilitresTest {

    @Test
    fun `every spelling of the millilitre applies, and nothing else does`() {
        listOf("ml", "ML", " ml ", "ml.", "millilitres", "milliliter", "מ\"ל", "מ״ל", "מיליליטר")
            .forEach { assertThat(PerHundredMillilitres.applies(it)).isTrue() }
        listOf("", "glass", "g", "l", "cl", "100 ml", "bar").forEach {
            assertThat(PerHundredMillilitres.applies(it)).isFalse()
        }
        assertThat(PerHundredMillilitres.applies(null)).isFalse()
    }

    @Test
    fun `shown is a decimal point shift, with no float artefact`() {
        // 0.57 * 100 is 56.99999999999999 in floating point, and 0.036 * 100 is 3.5999999999999996.
        assertThat(0.57 * 100).isNotEqualTo(57.0)
        assertThat(PerHundredMillilitres.shown(0.57)).isEqualTo(57.0)
        assertThat(PerHundredMillilitres.shown(0.036)).isEqualTo(3.6)
        assertThat(PerHundredMillilitres.shown(0.64)).isEqualTo(64.0)
        assertThat(PerHundredMillilitres.shown(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `a stored figure carrying a double's last-digit noise is shown clean`() {
        // What 2.9 / 100 leaves in a double: 0.028999999999999998.
        val noisy = 2.9 / 100
        assertThat(noisy.toString()).isNotEqualTo("0.029")
        assertThat(PerHundredMillilitres.shown(noisy)).isEqualTo(2.9)
    }

    @Test
    fun `stored is the shift the other way, to the nearest double of the decimal`() {
        // 2.9 / 100 is 0.028999999999999998 and 1.1 / 100 is 0.011000000000000001 in floating point.
        assertThat(2.9 / 100).isNotEqualTo(0.029)
        assertThat(PerHundredMillilitres.stored(2.9)).isEqualTo(0.029)
        assertThat(PerHundredMillilitres.stored(1.1)).isEqualTo(0.011)
        assertThat(PerHundredMillilitres.stored(3.3)).isEqualTo(0.033)
        assertThat(PerHundredMillilitres.stored(64.0)).isEqualTo(0.64)
        assertThat(PerHundredMillilitres.stored(0.5)).isEqualTo(0.005)
    }

    @Test
    fun `all four figures shift together`() {
        val perMl = Nutrients(0.57, 0.029, 0.047, 0.036)
        val per100 = PerHundredMillilitres.shown(perMl)
        assertThat(per100).isEqualTo(Nutrients(57.0, 2.9, 4.7, 3.6))
        assertThat(PerHundredMillilitres.stored(per100)).isEqualTo(perMl)
    }

    @Test
    fun `the group is per 100 ml for a millilitre, and per the unit as typed otherwise`() {
        assertThat(PerHundredMillilitres.per("ml")).isEqualTo("100 ml")
        assertThat(PerHundredMillilitres.per("millilitres")).isEqualTo("100 ml")
        assertThat(PerHundredMillilitres.per(" glass ")).isEqualTo("glass")
    }
}
