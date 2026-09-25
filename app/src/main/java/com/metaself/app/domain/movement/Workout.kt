package com.metaself.app.domain.movement

import kotlin.math.roundToInt

/**
 * What a workout was. Stored as a string, never an ordinal (the same reason as a food's
 * [com.metaself.app.domain.day.Source]): an ordinal is meaningless the moment somebody reorders
 * the enum.
 */
enum class WorkoutKind {
    RUN, WALK, CYCLE, SWIM, STRENGTH, OTHER,

    /** A value this version does not know. Shown as "Exercise", never counted as a run. */
    UNRECOGNISED;

    companion object {
        fun parse(stored: String?): WorkoutKind =
            entries.firstOrNull { it.name == stored } ?: UNRECOGNISED
    }
}

/** How hard it felt. Only a workout the owner typed has one; a band's session is what it is. */
enum class Effort { EASY, MODERATE, HARD }

/**
 * Where a workout's energy figure came from (D4). A band's own number, a formula's guess from kind
 * and effort, the owner's typed figure, or nothing — and the screen says which.
 */
enum class EnergySource { BAND, MET_ESTIMATE, TYPED, NONE }

/** Whether the band recorded it or the owner did. */
enum class WorkoutSource { SYNCED, TYPED }

/**
 * One workout, as the rest of the app reasons about it.
 *
 * Pure: no Room annotations, no Health Connect types. The entity that stores it arrives in phase 2
 * and maps to and from this.
 */
data class Workout(
    val id: Long,
    val epochDay: Long,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    val kind: WorkoutKind,
    val title: String?,
    val distanceM: Int?,
    val energyKcal: Int?,
    val energySource: EnergySource,
    val effort: Effort?,
    val source: WorkoutSource,
    val hidden: Boolean,
    val note: String?,
) {
    /** Seconds per kilometre — "5:30 /km" on screen — or null without a distance. */
    val paceSecondsPerKm: Int?
        get() = distanceM?.takeIf { it > 0 && durationMinutes > 0 }
            ?.let { (durationMinutes * 60.0 / (it / 1000.0)).roundToInt() }
}
