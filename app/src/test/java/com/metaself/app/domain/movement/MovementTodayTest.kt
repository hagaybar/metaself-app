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

    /** Public issue #58, invented figures: few steps, but the band's figure beat the usual day. */
    @Test
    fun `on a day the band decided, the rule is filled and marked by the band's figure`() {
        val swim = MovementToday(
            steps = 1_000, normalSteps = 8_000,
            energy = ActivityEnergy(480, MovementSource.ACTIVE_CALORIES), normalEnergyKcal = 240,
        )

        assertThat(swim.decidedByEnergy).isTrue()
        assertThat(swim.fractionOfUsual).isEqualTo(1f)
        assertThat(swim.aboveUsual).isTrue()
    }

    @Test
    fun `on a day a typed workout decided but fell short of usual, the rule is part-filled in kcal`() {
        val short = MovementToday(
            steps = 1_000, normalSteps = 8_000,
            energy = ActivityEnergy(120, MovementSource.TYPED_WORKOUT), normalEnergyKcal = 240,
        )

        assertThat(short.fractionOfUsual).isWithin(1e-6f).of(0.5f)
        assertThat(short.aboveUsual).isFalse()
    }

    /** 4,000 steps on 80 kg are 4,000 × 0.000375 × 80 = 120 kcal: the steps decided. */
    @Test
    fun `on a day the steps decided, the rule is the steps, as before`() {
        val walk = MovementToday(
            steps = 4_000, normalSteps = 8_000,
            energy = ActivityEnergy(120, MovementSource.STEPS), normalEnergyKcal = 240,
        )

        assertThat(walk.fractionOfUsual).isWithin(1e-6f).of(0.5f)
        assertThat(walk.aboveUsual).isFalse()
    }
}
