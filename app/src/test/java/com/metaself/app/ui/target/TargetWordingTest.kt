package com.metaself.app.ui.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.DailyTargetCalculator
import org.junit.jupiter.api.Test

class TargetWordingTest {

    private val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)

    @Test
    fun `the first line is what the body burns doing nothing`() {
        val first = TargetWording.arithmetic(target, aProfile()).first()
        assertThat(first.heading).isEqualTo("Resting burn: 1,700 kcal")
        assertThat(first.detail).contains("doing nothing")
    }

    @Test
    fun `the second line names the activity multiplier it used`() {
        val second = TargetWording.arithmetic(target, aProfile())[1]
        assertThat(second.heading).isEqualTo("A normal day: 2,640 kcal")
        assertThat(second.detail).contains("1.55")
        assertThat(second.detail).contains("moderately active")
    }

    @Test
    fun `the third line states the cost of the rate asked for`() {
        val third = TargetWording.arithmetic(target, aProfile())[2]
        assertThat(third.heading).isEqualTo("To lose 0.5 kg a week: −550 kcal")
        assertThat(third.detail).contains("7,700")
        assertThat(third.detail).contains("about")
    }

    @Test
    fun `the last line is the target itself`() {
        val last = TargetWording.arithmetic(target, aProfile()).last()
        assertThat(last.heading).isEqualTo("Your daily target: 2,090 kcal")
    }

    @Test
    fun `holding weight says so instead of naming a rate`() {
        val holding = aProfile(goal = Goal.hold())
        val lines = TargetWording.arithmetic(DailyTargetCalculator.of(holding, TEST_YEAR), holding)
        assertThat(lines[2].heading).isEqualTo("To hold your weight: no change")
    }

    @Test
    fun `a target held at the floor explains the floor in place of the total`() {
        val steep = aProfile(
            sex = Sex.FEMALE,
            weightKg = 65.0,
            heightCm = 165,
            birthYear = 1986,
            activity = ActivityLevel.SEDENTARY,
            goal = Goal.lose(1.0),
        )
        val lines = TargetWording.arithmetic(DailyTargetCalculator.of(steep, TEST_YEAR), steep)
        assertThat(lines.map { it.heading }.any { it.startsWith("Held at the safe floor") }).isTrue()
    }

    @Test
    fun `a floor the owner overruled is still stated, not hidden`() {
        val steep = aProfile(
            sex = Sex.FEMALE,
            weightKg = 65.0,
            heightCm = 165,
            birthYear = 1986,
            activity = ActivityLevel.SEDENTARY,
            goal = Goal.lose(1.0),
            allowBelowFloor = true,
        )
        val lines = TargetWording.arithmetic(DailyTargetCalculator.of(steep, TEST_YEAR), steep)
        assertThat(lines.map { it.heading }.any { it.startsWith("Below the safe floor, by your") })
            .isTrue()
    }

    @Test
    fun `each macro says where its number came from`() {
        val lines = TargetWording.macros(target)
        assertThat(lines[0].heading).isEqualTo("Protein: 145 g")
        assertThat(lines[0].detail).contains("1.8 g per kg")
        assertThat(lines[1].heading).isEqualTo("Fat: 65 g")
        assertThat(lines[1].detail).contains("floor")
        assertThat(lines[2].heading).isEqualTo("Carbohydrate: 230 g")
        assertThat(lines[2].detail).contains("what is left")
    }
}
