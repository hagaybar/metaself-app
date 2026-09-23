package com.metaself.app.data.profile

import com.metaself.app.domain.goal.GoalArrival

/**
 * The arrival's storage format, as a pure function both ways.
 *
 * Beside the profile and the target revision in the same preferences store, for the same reason
 * they are: it is one record with no queries. Every value is a string, so that a decimal is written
 * and read the same way on a phone set to any locale.
 */
object GoalArrivalCodec {

    const val KEY_TARGET_KG = "goal_arrival_target_kg"
    const val KEY_EPOCH_DAY = "goal_arrival_epoch_day"

    fun encode(arrival: GoalArrival): Map<String, String> = mapOf(
        KEY_TARGET_KG to arrival.targetKg.toString(),
        KEY_EPOCH_DAY to arrival.epochDay.toString(),
    )

    /** Null for a store that has none, or one written half way. Never an exception. */
    fun decode(values: Map<String, String>): GoalArrival? {
        val targetKg = values[KEY_TARGET_KG]?.toDoubleOrNull() ?: return null
        val epochDay = values[KEY_EPOCH_DAY]?.toLongOrNull() ?: return null
        return GoalArrival(targetKg = targetKg, epochDay = epochDay)
    }
}
