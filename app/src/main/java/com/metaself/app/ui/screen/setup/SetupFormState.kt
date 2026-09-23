package com.metaself.app.ui.screen.setup

import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.Sex

/** The fields of the setup form, so an error can be attached to the box that caused it. */
enum class SetupField { HEIGHT, BIRTH_YEAR, SEX, WEIGHT, ACTIVITY, DIRECTION, RATE, TARGET }

/**
 * What the setup form currently holds, and whether it makes sense.
 *
 * Numbers are held as the strings the owner typed, not as parsed values: a field being mid-edit is
 * a normal state, and "8" on the way to "80.5" is not an error worth shouting about. Parsing and
 * judging both happen here, purely, so the screen never has to.
 *
 * The ranges are wide on purpose. They exist to catch a slipped finger — a height of 18 or 1800 —
 * not to have an opinion about anybody's body.
 */
data class SetupFormState(
    val heightCm: String = "",
    val birthYear: String = "",
    val sex: Sex? = null,
    val weightKg: String = "",
    val activity: ActivityLevel? = null,
    val direction: GoalDirection? = null,
    val kgPerWeek: Double? = null,
    val targetKg: String = "",
    val allowBelowFloor: Boolean = false,
) {

    fun errors(currentYear: Int): Map<SetupField, String> = buildMap {
        val height = heightCm.trim().toIntOrNull()
        if (height == null || height !in MIN_HEIGHT_CM..MAX_HEIGHT_CM) {
            put(
                SetupField.HEIGHT,
                "A height in centimetres, between $MIN_HEIGHT_CM and $MAX_HEIGHT_CM.",
            )
        }

        val year = birthYear.trim().toIntOrNull()
        val oldestYear = currentYear - MAX_AGE_YEARS
        val youngestYear = currentYear - MIN_AGE_YEARS
        if (year == null || year !in oldestYear..youngestYear) {
            put(SetupField.BIRTH_YEAR, "A year of birth between $oldestYear and $youngestYear.")
        }

        if (sex == null) put(SetupField.SEX, "Needed by the formula, and nothing else.")

        // Finite only: "NaN" passes both comparisons below — every comparison with it is false —
        // and was saved as a weight, or crashed Save inside the goal as a target (issue #32, D42).
        // It is refused with the range the box already names, as "Infinity" always was.
        val weight = weightKg.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        if (weight == null || weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG) {
            put(
                SetupField.WEIGHT,
                "A weight in kilograms, between $MIN_WEIGHT_KG and $MAX_WEIGHT_KG.",
            )
        }

        if (activity == null) put(SetupField.ACTIVITY, "Pick the closest.")

        if (direction == null) {
            put(SetupField.DIRECTION, "Lose, hold or gain.")
        } else if (direction != GoalDirection.HOLD && kgPerWeek == null) {
            put(SetupField.RATE, "How fast?")
        }

        // A target is optional — a rate with no destination is still a goal, and was the only kind
        // this app had. What is not allowed is a destination on the wrong side of where he is: that
        // is not a goal he can work towards, and the app would announce his arrival the moment he
        // opened it.
        val target = targetKg.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        if (targetKg.isNotBlank()) {
            when {
                target == null || target < MIN_WEIGHT_KG || target > MAX_WEIGHT_KG ->
                    put(
                        SetupField.TARGET,
                        "A weight in kilograms, between $MIN_WEIGHT_KG and $MAX_WEIGHT_KG.",
                    )

                weight == null -> Unit

                direction == GoalDirection.LOSE && target >= weight ->
                    put(SetupField.TARGET, "To lose weight, aim below the $weight kg you are now.")

                direction == GoalDirection.GAIN && target <= weight ->
                    put(SetupField.TARGET, "To gain weight, aim above the $weight kg you are now.")
            }
        }
    }

    fun toProfile(currentYear: Int): Profile? {
        if (errors(currentYear).isNotEmpty()) return null
        // Blank is no destination, which is allowed. Holding never has one.
        val target = targetKg.trim().toDoubleOrNull()
        val goal = when (direction) {
            null, GoalDirection.HOLD -> Goal.hold()
            GoalDirection.LOSE -> Goal.lose(kgPerWeek ?: return null, target)
            GoalDirection.GAIN -> Goal.gain(kgPerWeek ?: return null, target)
        }
        return Profile(
            heightCm = heightCm.trim().toInt(),
            birthYear = birthYear.trim().toInt(),
            sex = sex ?: return null,
            weightKg = weightKg.trim().toDouble(),
            activity = activity ?: return null,
            goal = goal,
            allowBelowFloor = allowBelowFloor,
        )
    }

    companion object {
        const val MIN_HEIGHT_CM = 100
        const val MAX_HEIGHT_CM = 250
        const val MIN_AGE_YEARS = 13
        const val MAX_AGE_YEARS = 100
        const val MIN_WEIGHT_KG = 30.0
        const val MAX_WEIGHT_KG = 350.0

        /** Pre-fill the form from a profile already stored, for editing. */
        fun from(profile: Profile): SetupFormState = SetupFormState(
            heightCm = profile.heightCm.toString(),
            birthYear = profile.birthYear.toString(),
            sex = profile.sex,
            weightKg = profile.weightKg.toString(),
            activity = profile.activity,
            direction = profile.goal.direction,
            kgPerWeek = profile.goal.kgPerWeek.takeIf { it > 0.0 },
            targetKg = profile.goal.targetKg?.toString().orEmpty(),
            allowBelowFloor = profile.allowBelowFloor,
        )
    }
}
