package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * D12b and D60: one number per day, the largest of three readings, never their sum.
 *
 * A band records a long walk twice — as steps and as calories — and adding them would pay for that
 * walk twice, which is the error the whole surplus-only design exists to prevent. A workout typed
 * by hand is the third reading, for the same reason.
 */
class ActivityEnergyTest {

    /** The canonical body from `aProfile()`, so every figure below is a round one. */
    private val weightKg = 80.0

    private fun day(steps: Int, activeKcal: Int? = null, typedKcal: Int = 0) = ActivityEnergy.of(
        DayMovement(epochDay = 1, steps = steps, activeKcal = activeKcal, typedWorkoutsKcal = typedKcal),
        weightKg,
    )

    @Test
    fun `with no band the steps are the whole story`() {
        val walked = day(steps = 10_000)

        assertThat(walked.kcal).isEqualTo(300)
        assertThat(walked.source).isEqualTo(MovementSource.STEPS)
    }

    /** The same walk, reported twice. It is worth what it is worth, not twice that. */
    @Test
    fun `a walk reported by both readings counts once`() {
        val walked = day(steps = 10_000, activeKcal = 340)

        assertThat(walked.kcal).isEqualTo(340)
        assertThat(walked.kcal).isNotEqualTo(300 + 340)
    }

    @Test
    fun `the larger reading wins, whichever it is`() {
        assertThat(day(steps = 10_000, activeKcal = 100).kcal).isEqualTo(300)
        assertThat(day(steps = 10_000, activeKcal = 900).kcal).isEqualTo(900)
    }

    /** A swim moves no steps at all, so the band's figure is the only thing that can carry it. */
    @Test
    fun `a swim is carried entirely by the band`() {
        val swim = day(steps = 900, activeKcal = 520)

        assertThat(swim.kcal).isEqualTo(520)
        assertThat(swim.source).isEqualTo(MovementSource.ACTIVE_CALORIES)
    }

    @Test
    fun `a day with nothing at all is worth nothing`() {
        assertThat(day(steps = 0).kcal).isEqualTo(0)
    }

    /**
     * The usual day is now measured in the same currency, so a person with no band gets exactly the
     * behaviour they had before any of this existed.
     */
    @Test
    fun `the usual day is measured in the same currency`() {
        val history = (1..30).map { DayMovement(epochDay = 20_699L - it, steps = 5_200) }

        val normal = NormalDay.energyKcal(history, todayEpochDay = 20_699L, weightKg = weightKg)

        assertThat(normal).isEqualTo(day(steps = 5_200).kcal)
    }

    /** A band-reported day raises the usual, so routine exercise earns nothing either. */
    @Test
    fun `routine exercise raises the usual day and stops earning`() {
        val withDailySwim = (1..30).map {
            DayMovement(epochDay = 20_699L - it, steps = 3_000, activeKcal = 500)
        }

        val normal = NormalDay.energyKcal(withDailySwim, 20_699L, weightKg)!!

        assertThat(normal).isEqualTo(500)
        val anotherSwim = MovementCredit.of(day(3_000, 500), normal, capKcal = 275)
        assertThat(anotherSwim.kcal).isEqualTo(0)
    }

    /** D60: a run typed by hand on a day the phone also counted its steps is captured once. */
    @Test
    fun `a typed workout is a third reading, not an addition`() {
        val ran = day(steps = 10_000, typedKcal = 350)

        assertThat(ran.kcal).isEqualTo(350)
        assertThat(ran.source).isEqualTo(MovementSource.TYPED_WORKOUT)
        assertThat(ran.kcal).isNotEqualTo(300 + 350)
    }

    @Test
    fun `the band still wins when it is the largest of the three`() {
        val swamAndRan = day(steps = 10_000, activeKcal = 340, typedKcal = 320)

        assertThat(swamAndRan.kcal).isEqualTo(340)
        assertThat(swamAndRan.source).isEqualTo(MovementSource.ACTIVE_CALORIES)
    }

    /** A tie falls to the least-estimated reading, which is the step count. */
    @Test
    fun `a tie falls to the steps`() {
        assertThat(day(steps = 10_000, typedKcal = 300).source).isEqualTo(MovementSource.STEPS)
    }

    @Test
    fun `a day with nothing typed behaves exactly as before`() {
        assertThat(day(steps = 10_000, activeKcal = 340)).isEqualTo(
            ActivityEnergy.of(DayMovement(epochDay = 1, steps = 10_000, activeKcal = 340), weightKg),
        )
    }
}
