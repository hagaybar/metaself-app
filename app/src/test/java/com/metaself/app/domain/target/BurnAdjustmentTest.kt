package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BurnAdjustmentTest {

    private fun measured(difference: Int) = MeasuredBurn(
        days = 28,
        daysLogged = 28,
        averageLoggedKcal = 2_150,
        trendChangeKg = -1.2,
        fromStoresKcalPerDay = 330,
        measuredKcal = 2_480 + difference,
        formulaKcal = 2_480,
    )

    @Test
    fun `a small difference is taken in full`() {
        assertThat(BurnAdjustment.next(current = 0, measured = measured(-60))).isEqualTo(-60)
    }

    /**
     * Under-logging looks exactly like a slow metabolism, and the correction it invites is to cut
     * the target. Moving a hundred at a time is what makes that spiral survivable.
     */
    @Test
    fun `a large difference is taken a hundred at a time`() {
        assertThat(BurnAdjustment.next(current = 0, measured = measured(-400))).isEqualTo(-100)
        assertThat(BurnAdjustment.next(current = 0, measured = measured(400))).isEqualTo(100)
    }

    @Test
    fun `it accumulates over successive weeks`() {
        var adjustment = 0
        repeat(3) { adjustment = BurnAdjustment.next(adjustment, measured(-400)) }

        assertThat(adjustment).isEqualTo(-300)
    }

    /** Beyond this the profile is wrong, not the metabolism. */
    @Test
    fun `it can never exceed six hundred either way`() {
        var adjustment = 0
        repeat(20) { adjustment = BurnAdjustment.next(adjustment, measured(-1_000)) }
        assertThat(adjustment).isEqualTo(-BurnAdjustment.MAX_TOTAL_KCAL)

        var upward = 0
        repeat(20) { upward = BurnAdjustment.next(upward, measured(1_000)) }
        assertThat(upward).isEqualTo(BurnAdjustment.MAX_TOTAL_KCAL)
    }

    @Test
    fun `with nothing measured it stands still`() {
        assertThat(BurnAdjustment.next(current = -150, measured = null)).isEqualTo(-150)
    }

    @Test
    fun `once the formula and the measurement agree it stops moving`() {
        assertThat(BurnAdjustment.next(current = -160, measured = measured(0))).isEqualTo(-160)
    }
}
