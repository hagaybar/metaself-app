package com.metaself.app.domain.movement

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A workout being typed in (D76), as the sheet holds it: the fields as typed, and what they add up to.
 *
 * The text is kept exactly as typed, never reformatted under the owner's thumb; what it means is
 * worked out here. Only [kind] and [minutes] are required. [effort] is never null: it starts on
 * Moderate and cannot be cleared (activity spec §8 item 4), so [MetEstimate] is never asked to price
 * a typed workout without one.
 *
 * @property distanceKm kept when the kind changes to one without a distance, so changing back finds
 *   it; used only while [takesDistance].
 * @property ownEnergy the owner chose "set it yourself": [energyKcal] is his figure (TYPED) and no
 *   estimate is made.
 */
data class WorkoutDraft(
    val kind: WorkoutKind? = null,
    val minutes: String = "",
    val distanceKm: String = "",
    val effort: Effort = Effort.MODERATE,
    val ownEnergy: Boolean = false,
    val energyKcal: String = "",
    val note: String = "",
) {
    val takesDistance: Boolean get() = kind in DISTANCE_KINDS

    /** Whole minutes from one to a day's worth; null when blank or anything else. */
    val minutesValue: Int? get() = minutes.trim().toIntOrNull()?.takeIf { it in 1..MINUTES_IN_A_DAY }

    val minutesProblem: Boolean get() = minutes.isNotBlank() && minutesValue == null

    /**
     * Metres, when the kind takes a distance and a positive one up to [MAX_DISTANCE_KM] was typed; a
     * comma is a decimal point. Plain digits only: "1e3" would be read as a thousand kilometres by
     * [BigDecimal], and is not taken.
     */
    val distanceM: Int?
        get() {
            if (!takesDistance) return null
            val typed = distanceKm.trim().replace(',', '.')
            if (!PLAIN_DECIMAL.matches(typed)) return null
            val km = typed.toBigDecimalOrNull() ?: return null
            if (km > BigDecimal(MAX_DISTANCE_KM)) return null
            val metres = km.movePointRight(3).setScale(0, RoundingMode.HALF_UP)
            if (metres.signum() <= 0) return null
            return metres.toInt()
        }

    val distanceProblem: Boolean get() = takesDistance && distanceKm.isNotBlank() && distanceM == null

    /** The owner's own figure, when he chose to give one: a whole number of kcal, 0 to [MAX_OWN_KCAL]. */
    val ownKcal: Int?
        get() = if (ownEnergy) energyKcal.trim().toIntOrNull()?.takeIf { it in 0..MAX_OWN_KCAL } else null

    val energyProblem: Boolean get() = ownEnergy && energyKcal.isNotBlank() && ownKcal == null

    /** Seconds per kilometre for a run with both minutes and a distance, shown live (D76; runs only, D78). */
    val paceSecondsPerKm: Int?
        get() {
            if (kind != WorkoutKind.RUN) return null
            val minutes = minutesValue ?: return null
            val metres = distanceM ?: return null
            return paceOf(minutes, metres)
        }

    /** Whether the estimate follows the pace rather than the felt effort ([MetEstimate.pricedByPace]). */
    val pricedByPace: Boolean
        get() = kind != null && minutesValue != null && MetEstimate.pricedByPace(kind, distanceM)

    /** What the MET table says it cost on [weightKg]; null without a kind, minutes or a weight. */
    fun estimateKcal(weightKg: Double?): Int? {
        val kind = kind ?: return null
        val minutes = minutesValue ?: return null
        val weight = weightKg ?: return null
        return MetEstimate.netKcal(kind, effort, minutes, distanceM, weight)
    }

    val canSave: Boolean
        get() = kind != null && minutesValue != null && !distanceProblem && (!ownEnergy || ownKcal != null)

    /**
     * The workout this draft saves as, or null while it cannot be saved. Its energy is the owner's
     * figure (TYPED) when he gave one; else the estimate (MET_ESTIMATE); else, with no weight to
     * estimate from, nothing (NONE) — never a guess (D4).
     */
    fun toWorkout(id: Long, epochDay: Long, startedAtMillis: Long, weightKg: Double?): Workout? {
        if (!canSave) return null
        val kind = kind ?: return null
        val minutes = minutesValue ?: return null
        val estimate = estimateKcal(weightKg)
        val (kcal, source) = when {
            ownEnergy -> ownKcal to EnergySource.TYPED
            estimate != null -> estimate to EnergySource.MET_ESTIMATE
            else -> null to EnergySource.NONE
        }
        return Workout(
            id = id,
            epochDay = epochDay,
            startedAtMillis = startedAtMillis,
            durationMinutes = minutes,
            kind = kind,
            title = null,
            distanceM = distanceM,
            energyKcal = kcal,
            energySource = source,
            effort = effort,
            source = WorkoutSource.TYPED,
            hidden = false,
            note = note.trim().takeIf { it.isNotEmpty() },
        )
    }

    companion object {

        /** The kinds the sheet offers, in its order (D76). */
        val KINDS = listOf(
            WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE,
            WorkoutKind.SWIM, WorkoutKind.STRENGTH, WorkoutKind.OTHER,
        )

        private val DISTANCE_KINDS = setOf(WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE, WorkoutKind.SWIM)

        /** 24 × 60: no session is longer than the day it is filed on. */
        const val MINUTES_IN_A_DAY = 1_440

        /**
         * The largest figure of his own the sheet takes. A choice, not a measurement: a sanity
         * ceiling, far above any session a person can do in a day, that stops a slipped zero or two
         * becoming the day's movement.
         */
        const val MAX_OWN_KCAL = 20_000

        /** The longest distance the sheet takes, in km. A choice, not a measurement, as [MAX_OWN_KCAL]. */
        const val MAX_DISTANCE_KM = 1_000

        /** Digits with at most one decimal point: no sign and no exponent. */
        private val PLAIN_DECIMAL = Regex("""\d+(\.\d*)?|\.\d+""")

        /** A typed workout, as the sheet opens it to be changed. */
        fun from(workout: Workout): WorkoutDraft = WorkoutDraft(
            kind = workout.kind,
            minutes = workout.durationMinutes.toString(),
            distanceKm = workout.distanceM
                ?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }
                .orEmpty(),
            effort = workout.effort ?: Effort.MODERATE,
            ownEnergy = workout.energySource == EnergySource.TYPED,
            energyKcal = workout.energyKcal
                ?.takeIf { workout.energySource == EnergySource.TYPED }
                ?.toString()
                .orEmpty(),
            note = workout.note.orEmpty(),
        )

        /** [epochDay] at the clock time of [nowMillis] in [zone]: where a typed workout starts (D76). */
        fun startOn(epochDay: Long, nowMillis: Long, zone: ZoneId): Long =
            LocalDate.ofEpochDay(epochDay)
                .atTime(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
    }
}
