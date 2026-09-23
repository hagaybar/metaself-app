package com.metaself.app.domain.profile

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProfileTest {

    @Test
    fun `age is whole years from the year of birth`() {
        assertThat(aProfile(birthYear = 1980).ageYears(TEST_YEAR)).isEqualTo(46)
    }

    @Test
    fun `the activity factors are the standard multipliers`() {
        assertThat(ActivityLevel.SEDENTARY.factor).isEqualTo(1.2)
        assertThat(ActivityLevel.LIGHT.factor).isEqualTo(1.375)
        assertThat(ActivityLevel.MODERATE.factor).isEqualTo(1.55)
        assertThat(ActivityLevel.ACTIVE.factor).isEqualTo(1.725)
        assertThat(ActivityLevel.VERY_ACTIVE.factor).isEqualTo(1.9)
    }

    @Test
    fun `holding weight carries no rate`() {
        assertThat(Goal.hold().kgPerWeek).isEqualTo(0.0)
        assertThat(Goal.hold().direction).isEqualTo(GoalDirection.HOLD)
    }

    @Test
    fun `a rate is a magnitude, never a negative number`() {
        try {
            Goal.lose(-0.5)
            throw AssertionError("expected a rejected rate")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("magnitude")
        }
    }

    @Test
    fun `the offered rates are the four the form shows`() {
        assertThat(Goal.OFFERED_RATES_KG_PER_WEEK)
            .containsExactly(0.25, 0.5, 0.75, 1.0).inOrder()
    }
}
