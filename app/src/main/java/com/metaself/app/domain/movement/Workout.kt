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
 * and effort, the owner's typed figure, nothing — or a workout file's (D82) — and the screen says which.
 */
enum class EnergySource { BAND, MET_ESTIMATE, TYPED, NONE, FILE }

/**
 * Where a workout's distance or steps came from when it is not Health Connect's total over the
 * session (D82): a workout file, or the owner's typing. Null means Health Connect's, as before D82.
 */
enum class WorkoutFigureSource {
    FILE, TYPED;

    companion object {
        fun parse(stored: String?): WorkoutFigureSource? = entries.firstOrNull { it.name == stored }
    }
}

/** Whether the band recorded it or the owner did. */
enum class WorkoutSource { SYNCED, TYPED }

/** Seconds per kilometre for [minutes] over [metres]. One formula for a stored workout and a draft. */
internal fun paceOf(minutes: Int, metres: Int): Int =
    (minutes * 60.0 / (metres / 1000.0)).roundToInt()

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
    /**
     * The session's average heart rate, worked out by this app from the readings inside it (D70);
     * null when there were none. Defaulted, so every workout built before the Movement screen still compiles.
     */
    val avgHeartRate: Int? = null,
    /**
     * False for a walk from an app whose walks the owner switched off (D81, [CountedWorkouts]): it
     * stays stored but is counted nowhere, as a hidden one is. Worked out as it is read, never stored.
     */
    val counted: Boolean = true,
    /**
     * Where [distanceM] came from when a workout file is involved (D82): FILE, or TYPED over a file's.
     * Null is a synced session's Health Connect total, or a typed workout's typed distance.
     */
    val distanceSource: WorkoutFigureSource? = null,
    /** Steps in the session: only ever a workout file's (D82). */
    val steps: Int? = null,
    val stepsSource: WorkoutFigureSource? = null,
    /** The session's highest heart rate, worked out from its readings (D70); null with none. */
    val maxHeartRate: Int? = null,
    /** Seconds in heart-rate zones 1 to 5 (D70); null with no readings. */
    val zoneSeconds: List<Int>? = null,
    /** What the zones were measured against, as stored: ESTIMATED (220 − age) until an observed maximum exists. */
    val zoneMaxSource: String? = null,
    /**
     * D92: every witness of this session as stored, lead first — this workout's own row among them —
     * when overlapping workouts were read as one session ([SessionWitnesses]); empty for a session
     * recorded once. Worked out as the record is read, never stored.
     */
    val witnesses: List<Workout> = emptyList(),
    /** D92: another witness's distance, when it differs from [distanceM] by more than 15 %. */
    val otherDistance: OtherDistance? = null,
    /** D81: its own app wrote a distance reading during it. Worked out as it is read, never stored. */
    val ownDistance: Boolean = false,
    /**
     * D92: the minutes [distanceM] was measured over, when another witness than the lead gave it; null
     * when the distance is the session's own. The pace is worked over these, never the lead's.
     */
    val distanceMinutes: Int? = null,
    /**
     * D92: the owner's splits between this session and one it still overlaps — what "Put back
     * together" removes. Empty when it was never split, or no longer overlaps what it was split from.
     */
    val splits: List<SessionSplit> = emptyList(),
) {
    /** D92: how many other workouts recorded this session — "also recorded by 2 more". */
    val alsoRecordedBy: Int get() = (witnesses.size - 1).coerceAtLeast(0)

    /** D92: the ids of every stored workout this session is made of; its own alone when recorded once. */
    val witnessIds: List<Long> get() = witnesses.map { it.id }.ifEmpty { listOf(id) }

    /** D92: the lead as it is stored, before any other witness lent it a figure; itself when recorded once. */
    val asStored: Workout get() = witnesses.firstOrNull() ?: this

    /** A file gave it any figure (D82) — how a second import of the same file finds it again. */
    val fromFile: Boolean
        get() = distanceSource == WorkoutFigureSource.FILE || stepsSource == WorkoutFigureSource.FILE ||
            energySource == EnergySource.FILE

    /**
     * Seconds per kilometre — "5:30 /km" on screen — or null without a distance. Over the minutes the
     * distance was measured in (D92: another witness's, when it lent the distance).
     */
    val paceSecondsPerKm: Int?
        get() {
            val minutes = distanceMinutes ?: durationMinutes
            return distanceM?.takeIf { it > 0 && minutes > 0 }?.let { paceOf(minutes, it) }
        }
}
