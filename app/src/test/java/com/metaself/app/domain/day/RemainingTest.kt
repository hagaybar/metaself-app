package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.DailyTargetCalculator
import org.junit.jupiter.api.Test

class RemainingTest {

    /** The step 2 fixture body: a target of 2,090 kcal, 145 g protein, 230 g carbs, 65 g fat. */
    private val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)

    @Test
    fun `with nothing logged, the whole target is left`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)
        assertThat(remaining.kcal).isEqualTo(2090)
        assertThat(remaining.proteinG).isEqualTo(145)
        assertThat(remaining.carbsG).isEqualTo(230)
        assertThat(remaining.fatG).isEqualTo(65)
        assertThat(remaining.overTarget).isFalse()
    }

    @Test
    fun `what has been eaten comes off what is left`() {
        val eaten = DayTotals(kcal = 600, proteinG = 40, carbsG = 50, fatG = 25)
        val remaining = Remaining.of(target, eaten)
        assertThat(remaining.kcal).isEqualTo(1490)
        assertThat(remaining.proteinG).isEqualTo(105)
    }

    @Test
    fun `eating exactly the target is not over it`() {
        val remaining = Remaining.of(target, DayTotals(2090, 0, 0, 0))
        assertThat(remaining.kcal).isEqualTo(0)
        assertThat(remaining.overTarget).isFalse()
    }

    @Test
    fun `going past the target says so, and says by how much`() {
        val remaining = Remaining.of(target, DayTotals(2300, 0, 0, 0))
        assertThat(remaining.overTarget).isTrue()
        assertThat(remaining.kcal).isEqualTo(-210)
    }

    @Test
    fun `a macro can be past its share without the day being over target`() {
        val remaining = Remaining.of(target, DayTotals(kcal = 500, proteinG = 200, carbsG = 0, fatG = 0))
        assertThat(remaining.proteinG).isEqualTo(-55)
        assertThat(remaining.overTarget).isFalse()
    }
}
