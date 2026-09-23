package com.metaself.app.ui.screen.setup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import org.junit.jupiter.api.Test

class SetupFormStateTest {

    private val complete = SetupFormState(
        heightCm = "180",
        birthYear = "1980",
        sex = Sex.MALE,
        weightKg = "80",
        activity = ActivityLevel.MODERATE,
        direction = GoalDirection.LOSE,
        kgPerWeek = 0.5,
    )

    @Test
    fun `a complete form has no errors and becomes a profile`() {
        assertThat(complete.errors(TEST_YEAR)).isEmpty()
        assertThat(complete.toProfile(TEST_YEAR)).isEqualTo(aProfile())
    }

    @Test
    fun `an empty form cannot become a profile`() {
        assertThat(SetupFormState().toProfile(TEST_YEAR)).isNull()
    }

    @Test
    fun `height outside human range is rejected`() {
        assertThat(complete.copy(heightCm = "60").errors(TEST_YEAR))
            .containsKey(SetupField.HEIGHT)
        assertThat(complete.copy(heightCm = "260").errors(TEST_YEAR))
            .containsKey(SetupField.HEIGHT)
    }

    @Test
    fun `height that is not a number is rejected`() {
        assertThat(complete.copy(heightCm = "one eighty").errors(TEST_YEAR))
            .containsKey(SetupField.HEIGHT)
    }

    @Test
    fun `a year of birth in the future is rejected`() {
        assertThat(complete.copy(birthYear = "2030").errors(TEST_YEAR))
            .containsKey(SetupField.BIRTH_YEAR)
    }

    @Test
    fun `a year of birth implying an implausible age is rejected`() {
        assertThat(complete.copy(birthYear = "1900").errors(TEST_YEAR))
            .containsKey(SetupField.BIRTH_YEAR)
    }

    @Test
    fun `weight accepts a decimal`() {
        assertThat(complete.copy(weightKg = "80.5").errors(TEST_YEAR)).isEmpty()
        assertThat(complete.copy(weightKg = "80.5").toProfile(TEST_YEAR)?.weightKg)
            .isEqualTo(80.5)
    }

    @Test
    fun `weight outside a plausible range is rejected`() {
        assertThat(complete.copy(weightKg = "12").errors(TEST_YEAR))
            .containsKey(SetupField.WEIGHT)
        assertThat(complete.copy(weightKg = "500").errors(TEST_YEAR))
            .containsKey(SetupField.WEIGHT)
    }

    @Test
    fun `a rate is required when losing but not when holding`() {
        assertThat(complete.copy(kgPerWeek = null).errors(TEST_YEAR))
            .containsKey(SetupField.RATE)
        assertThat(
            complete.copy(direction = GoalDirection.HOLD, kgPerWeek = null).errors(TEST_YEAR),
        ).isEmpty()
    }

    @Test
    fun `holding discards a rate left behind by switching away from losing`() {
        val holding = complete.copy(direction = GoalDirection.HOLD, kgPerWeek = 0.5)
        assertThat(holding.toProfile(TEST_YEAR)?.goal).isEqualTo(Goal.hold())
    }

    @Test
    fun `a form can be built from an existing profile for editing`() {
        val form = SetupFormState.from(aProfile(weightKg = 80.5))
        assertThat(form.weightKg).isEqualTo("80.5")
        assertThat(form.toProfile(TEST_YEAR)).isEqualTo(aProfile(weightKg = 80.5))
    }

    /** A rate with no destination is still a goal, and was the only kind this app had. */
    @Test
    fun `a goal weight is optional`() {
        assertThat(complete.copy(targetKg = "").errors(TEST_YEAR)).isEmpty()
        assertThat(complete.copy(targetKg = "").toProfile(TEST_YEAR)!!.goal.targetKg).isNull()
    }

    @Test
    fun `a goal weight is carried onto the profile`() {
        val profile = complete.copy(targetKg = "72.5").toProfile(TEST_YEAR)!!

        assertThat(profile.goal.targetKg).isEqualTo(72.5)
    }

    /**
     * Losing down to a weight above the one he is at is not a goal he can work towards, and the app
     * would announce his arrival the moment he opened it.
     */
    @Test
    fun `losing weight towards a heavier target is refused`() {
        val errors = complete.copy(weightKg = "80", targetKg = "85").errors(TEST_YEAR)

        assertThat(errors).containsKey(SetupField.TARGET)
        assertThat(errors[SetupField.TARGET]).contains("below")
    }

    @Test
    fun `gaining weight towards a lighter target is refused`() {
        val errors = complete
            .copy(direction = GoalDirection.GAIN, weightKg = "80", targetKg = "75")
            .errors(TEST_YEAR)

        assertThat(errors).containsKey(SetupField.TARGET)
        assertThat(errors[SetupField.TARGET]).contains("above")
    }

    @Test
    fun `a goal weight that is not a weight is refused`() {
        assertThat(complete.copy(targetKg = "7").errors(TEST_YEAR)).containsKey(SetupField.TARGET)
    }

    /** Switching to holding must not leave a destination behind in the form's own state. */
    @Test
    fun `holding weight produces no target`() {
        val held = complete.copy(direction = GoalDirection.HOLD, kgPerWeek = null, targetKg = "")

        assertThat(held.toProfile(TEST_YEAR)!!.goal).isEqualTo(Goal.hold())
    }

    @Test
    fun `editing an existing goal shows the target already set`() {
        val form = SetupFormState.from(aProfile(goal = Goal.lose(0.5, targetKg = 75.0)))

        assertThat(form.targetKg).isEqualTo("75.0")
    }

    /**
     * "NaN" passed both range checks — every comparison with it is false — so a not-a-number weight
     * was saved into the profile, and a not-a-number target crashed Save inside the goal (issue
     * #32). Each is refused under its own box with the range it already names.
     */
    @Test
    fun `NaN is refused as a weight and as a target`() {
        val range = "A weight in kilograms, between 30.0 and 350.0."

        val weight = complete.copy(weightKg = "NaN")
        assertThat(weight.errors(TEST_YEAR)[SetupField.WEIGHT]).isEqualTo(range)
        assertThat(weight.toProfile(TEST_YEAR)).isNull()

        val target = complete.copy(targetKg = "NaN")
        assertThat(target.errors(TEST_YEAR)[SetupField.TARGET]).isEqualTo(range)
        assertThat(target.toProfile(TEST_YEAR)).isNull()

        assertThat(complete.copy(targetKg = "Infinity").errors(TEST_YEAR)[SetupField.TARGET])
            .isEqualTo(range)
        assertThat(complete.copy(weightKg = "Infinity").errors(TEST_YEAR)[SetupField.WEIGHT])
            .isEqualTo(range)
    }
}
