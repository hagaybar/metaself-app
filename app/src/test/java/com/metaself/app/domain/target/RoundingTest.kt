package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RoundingTest {

    @Test
    fun `rounds up to the nearest step`() {
        assertThat(roundToNearest(2147.0, step = 10)).isEqualTo(2150)
    }

    @Test
    fun `rounds down to the nearest step`() {
        assertThat(roundToNearest(2142.0, step = 10)).isEqualTo(2140)
    }

    @Test
    fun `a negative value rounds by magnitude, not towards zero`() {
        assertThat(roundToNearest(-274.0, step = 5)).isEqualTo(-275)
    }

    @Test
    fun `an exact multiple is left alone`() {
        assertThat(roundToNearest(550.0, step = 5)).isEqualTo(550)
    }
}
