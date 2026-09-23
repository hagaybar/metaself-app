package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MovementTodayTest {

    @Test
    fun `a quiet day fills part of the bar`() {
        val quiet = MovementToday(steps = 2_600, normalSteps = 5_200)

        assertThat(quiet.fractionOfUsual).isWithin(1e-6f).of(0.5f)
        assertThat(quiet.aboveUsual).isFalse()
        assertThat(quiet.extraSteps).isEqualTo(0)
    }

    /**
     * A bar that kept a 25,000-step day in proportion would make every ordinary day look like
     * nothing, which is the opposite of the point.
     */
    @Test
    fun `an enormous day fills the bar and stops`() {
        val huge = MovementToday(steps = 40_000, normalSteps = 5_200)

        assertThat(huge.fractionOfUsual).isEqualTo(1f)
        assertThat(huge.aboveUsual).isTrue()
        assertThat(huge.extraSteps).isEqualTo(34_800)
    }

    @Test
    fun `before a usual day is known there is no bar to draw`() {
        val learning = MovementToday(steps = 7_000, normalSteps = null)

        assertThat(learning.fractionOfUsual).isEqualTo(0f)
        assertThat(learning.aboveUsual).isFalse()
        assertThat(learning.extraSteps).isNull()
    }
}
