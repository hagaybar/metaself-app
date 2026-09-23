package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import org.junit.jupiter.api.Test

class MovementCreditTest {

    /** The canonical body from `aProfile()`, so every figure below is a round one. */
    private val weightKg = 80.0
    private val normal = 5_200
    private val cap = MovementCap.MOST_KCAL

    /** The usual day's energy, as the app computes it: 5,200 steps at this weight. */
    private val normalEnergy =
        ActivityEnergy.of(DayMovement(epochDay = 0, steps = normal), weightKg).kcal

    private fun credit(steps: Int, capKcal: Int = cap, activeKcal: Int? = null) =
        MovementCredit.of(
            today = ActivityEnergy.of(
                DayMovement(epochDay = 1, steps = steps, activeKcal = activeKcal),
                weightKg,
            ),
            normalEnergyKcal = normalEnergy,
            capKcal = capKcal,
        )

    /** D12: the target already pays for a normal day, so a normal day earns nothing. */
    @Test
    fun `an ordinary day is worth nothing`() {
        assertThat(credit(4_000).kcal).isEqualTo(0)
        assertThat(credit(5_200).kcal).isEqualTo(0)
    }

    @Test
    fun `fewer steps than usual is nothing, never a debt`() {
        val quiet = credit(1_000)

        assertThat(quiet.kcal).isEqualTo(0)
        assertThat(quiet.extraKcal).isEqualTo(0)
    }

    @Test
    fun `a slightly busy day is worth almost nothing`() {
        // 5,500 steps is 165 kcal, 9 above the usual day's 156, of which three quarters is 7.
        assertThat(credit(5_500).kcal).isEqualTo(7)
    }

    @Test
    fun `ten thousand steps is worth a little over a hundred`() {
        val busy = credit(10_000)

        // 10,000 steps is 300 kcal, 144 above the usual day's 156, of which three quarters is 108.
        assertThat(busy.energyKcal).isEqualTo(300)
        assertThat(busy.extraKcal).isEqualTo(144)
        assertThat(busy.kcal).isEqualTo(108)
    }

    @Test
    fun `a long walk is worth a snack`() {
        // 15,000 steps is 450 kcal, 294 above the usual day, of which three quarters is 220.5,
        // rounded to 221 — still short of the 300 ceiling, so nothing is held back.
        assertThat(credit(15_000).kcal).isEqualTo(221)
    }

    /**
     * D12b: a band records a walk twice, as steps and as calories. Only the larger is counted, or
     * the same walk would be paid for twice.
     */
    @Test
    fun `a walk the band also reported is counted once, not twice`() {
        // 10,000 steps is worth 300; the band says 340 for the same day.
        val bothReadings = credit(10_000, activeKcal = 340)

        assertThat(bothReadings.energyKcal).isEqualTo(340)
        assertThat(bothReadings.source).isEqualTo(MovementSource.ACTIVE_CALORIES)
        // Not 300 + 340.
        assertThat(bothReadings.extraKcal).isEqualTo(340 - normalEnergy)
    }

    /** A swim moves no steps at all, so the band's figure is the only thing carrying it. */
    @Test
    fun `a swim is credited even though it moved no steps`() {
        val swim = credit(steps = 900, activeKcal = 520)

        assertThat(swim.source).isEqualTo(MovementSource.ACTIVE_CALORIES)
        assertThat(swim.kcal).isGreaterThan(0)
    }

    @Test
    fun `with no band the steps are the whole story, as before`() {
        assertThat(credit(10_000).source).isEqualTo(MovementSource.STEPS)
    }

    @Test
    fun `an implausible day is capped, and says it was`() {
        val absurd = credit(25_000)

        assertThat(absurd.kcal).isEqualTo(cap)
        assertThat(absurd.capped).isTrue()
    }

    @Test
    fun `a day within the cap does not claim to have been capped`() {
        assertThat(credit(10_000).capped).isFalse()
    }

    /** For a future version that credits swimming: the credit knows what produced it. */
    @Test
    fun `a credit records where it came from`() {
        assertThat(credit(10_000).source).isEqualTo(MovementSource.STEPS)
    }

    /** A big walking day must never cancel the diet — the binding reason the cap exists. */
    @Test
    fun `the cap is half a day's deficit`() {
        // Half a kilogram a week is 550 kcal a day, so at most 275 comes back.
        assertThat(MovementCap.forGoal(Goal.lose(0.5))).isEqualTo(275)
    }

    @Test
    fun `a gentler goal gets a gentler cap, without anybody remembering to change it`() {
        // A quarter of a kilogram a week is 275 kcal a day, so at most 138 comes back — where a
        // flat 300 would have erased the whole day's deficit and turned a walk into a rest day.
        assertThat(MovementCap.forGoal(Goal.lose(0.25))).isEqualTo(138)
    }

    @Test
    fun `a steeper goal is still held to the ceiling`() {
        assertThat(MovementCap.forGoal(Goal.lose(1.0))).isEqualTo(MovementCap.MOST_KCAL)
    }

    /** Holding weight has no deficit to protect: burn more, eat more. */
    @Test
    fun `holding weight gets the flat ceiling`() {
        assertThat(MovementCap.forGoal(Goal.hold())).isEqualTo(MovementCap.MOST_KCAL)
        assertThat(MovementCap.forGoal(Goal.gain(0.25))).isEqualTo(MovementCap.MOST_KCAL)
    }
}
