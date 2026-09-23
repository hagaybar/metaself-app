package com.metaself.app.data.profile

import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.Sex

/**
 * The profile's storage format, as a pure function both ways.
 *
 * Kept separate from the DataStore that holds it so the format itself is testable without a device,
 * a file or a coroutine. Every value is a string: `toString`/`toDouble` are locale-independent,
 * whereas a formatted decimal would write the separator the phone's locale prefers — a comma in much
 * of the world — and fail to read back on a phone that expects a point.
 *
 * [decode] returns null for anything it cannot fully understand — a store that is empty, half
 * written, or holding a value from a future version. The app then behaves as though there were no
 * profile and offers setup, which is recoverable. Throwing would not be.
 */
object ProfileCodec {

    const val KEY_HEIGHT_CM = "height_cm"
    const val KEY_BIRTH_YEAR = "birth_year"
    const val KEY_SEX = "sex"
    const val KEY_WEIGHT_KG = "weight_kg"
    const val KEY_ACTIVITY = "activity"
    const val KEY_GOAL_DIRECTION = "goal_direction"
    const val KEY_GOAL_KG_PER_WEEK = "goal_kg_per_week"
    const val KEY_GOAL_TARGET_KG = "goal_target_kg"
    const val KEY_ALLOW_BELOW_FLOOR = "allow_below_floor"

    fun encode(profile: Profile): Map<String, String> = mapOf(
        KEY_HEIGHT_CM to profile.heightCm.toString(),
        KEY_BIRTH_YEAR to profile.birthYear.toString(),
        KEY_SEX to profile.sex.name,
        KEY_WEIGHT_KG to profile.weightKg.toString(),
        KEY_ACTIVITY to profile.activity.name,
        KEY_GOAL_DIRECTION to profile.goal.direction.name,
        KEY_GOAL_KG_PER_WEEK to profile.goal.kgPerWeek.toString(),
        KEY_ALLOW_BELOW_FLOOR to profile.allowBelowFloor.toString(),
        // Always written, empty when there is no target. The store clears only the keys the codec
        // writes, so a key omitted for a goal that no longer has a destination would leave the old
        // one behind and the app would go on aiming at a weight the owner had cleared.
        KEY_GOAL_TARGET_KG to (profile.goal.targetKg?.toString() ?: ""),
    )

    fun decode(values: Map<String, String>): Profile? {
        val heightCm = values[KEY_HEIGHT_CM]?.toIntOrNull() ?: return null
        val birthYear = values[KEY_BIRTH_YEAR]?.toIntOrNull() ?: return null
        val sex = values[KEY_SEX]?.let { name -> Sex.entries.firstOrNull { it.name == name } }
            ?: return null
        val weightKg = values[KEY_WEIGHT_KG]?.toDoubleOrNull() ?: return null
        val activity = values[KEY_ACTIVITY]
            ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }
            ?: return null
        val direction = values[KEY_GOAL_DIRECTION]
            ?.let { name -> GoalDirection.entries.firstOrNull { it.name == name } }
            ?: return null
        val kgPerWeek = values[KEY_GOAL_KG_PER_WEEK]?.toDoubleOrNull() ?: return null
        val allowBelowFloor = when (values[KEY_ALLOW_BELOW_FLOOR]) {
            "true" -> true
            "false" -> false
            else -> return null
        }

        // Absent, not zero, for every profile written before there were targets. Refusing to
        // decode those would offer the owner setup again and be indistinguishable from data loss.
        // Absent for every profile written before there were targets, and empty for a goal with
        // no destination. Refusing to decode either would offer the owner setup again and be
        // indistinguishable from data loss.
        val targetKg = values[KEY_GOAL_TARGET_KG]?.takeIf { it.isNotBlank() }?.toDoubleOrNull()

        val goal = runCatching { Goal(direction, kgPerWeek, targetKg) }.getOrNull() ?: return null

        return Profile(
            heightCm = heightCm,
            birthYear = birthYear,
            sex = sex,
            weightKg = weightKg,
            activity = activity,
            goal = goal,
            allowBelowFloor = allowBelowFloor,
        )
    }
}
