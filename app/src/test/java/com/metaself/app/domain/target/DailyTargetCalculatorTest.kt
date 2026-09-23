package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import org.junit.jupiter.api.Test

class DailyTargetCalculatorTest {

    @Test
    fun `a normal day is resting burn times the activity factor`() {
        // 1700 * 1.55 = 2635 -> 2640 at a step of ten
        val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)
        assertThat(target.restingBurnKcal).isEqualTo(1700)
        assertThat(target.maintenanceKcal).isEqualTo(2640)
    }

    @Test
    fun `losing half a kilogram a week costs 550 kcal a day`() {
        val target = DailyTargetCalculator.of(aProfile(goal = Goal.lose(0.5)), TEST_YEAR)
        assertThat(target.goalShiftKcal).isEqualTo(-550)
    }

    @Test
    fun `the target is a normal day plus the shift`() {
        // 2635 - 550 = 2085 -> 2090
        val target = DailyTargetCalculator.of(aProfile(goal = Goal.lose(0.5)), TEST_YEAR)
        assertThat(target.kcal).isEqualTo(2090)
        assertThat(target.floorApplied).isFalse()
    }

    @Test
    fun `holding weight leaves the target at a normal day`() {
        val target = DailyTargetCalculator.of(aProfile(goal = Goal.hold()), TEST_YEAR)
        assertThat(target.goalShiftKcal).isEqualTo(0)
        assertThat(target.kcal).isEqualTo(2640)
    }

    @Test
    fun `gaining adds calories rather than removing them`() {
        val target = DailyTargetCalculator.of(aProfile(goal = Goal.gain(0.25)), TEST_YEAR)
        assertThat(target.goalShiftKcal).isEqualTo(275)
        assertThat(target.kcal).isEqualTo(2910)
    }

    @Test
    fun `a rate that breaches the floor is held at the floor, and says so`() {
        // A small, sedentary body: resting burn 1320, a normal day 1584, less 1100 = 484
        val steep = aProfile(
            sex = Sex.FEMALE,
            weightKg = 65.0,
            heightCm = 165,
            birthYear = 1986,
            activity = ActivityLevel.SEDENTARY,
            goal = Goal.lose(1.0),
        )
        val target = DailyTargetCalculator.of(steep, TEST_YEAR)
        assertThat(target.floorApplied).isTrue()
        assertThat(target.kcal).isEqualTo(target.floorKcal)
        assertThat(target.requestedKcal).isLessThan(target.floorKcal)
    }

    @Test
    fun `the owner may overrule the floor, and then gets the number he asked for`() {
        val steep = aProfile(
            sex = Sex.FEMALE,
            weightKg = 65.0,
            heightCm = 165,
            birthYear = 1986,
            activity = ActivityLevel.SEDENTARY,
            goal = Goal.lose(1.0),
            allowBelowFloor = true,
        )
        val target = DailyTargetCalculator.of(steep, TEST_YEAR)
        assertThat(target.floorApplied).isFalse()
        assertThat(target.belowFloorByChoice).isTrue()
        assertThat(target.kcal).isEqualTo(target.requestedKcal)
    }

    @Test
    fun `overruling the floor changes nothing when the floor was never reached`() {
        val target = DailyTargetCalculator.of(aProfile(allowBelowFloor = true), TEST_YEAR)
        assertThat(target.belowFloorByChoice).isFalse()
        assertThat(target.kcal).isEqualTo(2090)
    }

    @Test
    fun `the macros come from the target actually proposed, not the one requested`() {
        val steep = aProfile(
            sex = Sex.FEMALE,
            weightKg = 65.0,
            heightCm = 165,
            birthYear = 1986,
            activity = ActivityLevel.SEDENTARY,
            goal = Goal.lose(1.0),
        )
        val target = DailyTargetCalculator.of(steep, TEST_YEAR)
        assertThat(target.macros).isEqualTo(Macros.derive(target.kcal, 65.0))
    }
}
