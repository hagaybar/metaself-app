package com.metaself.app.domain.movement

import com.metaself.app.domain.day.TEST_EPOCH_DAY

/**
 * A workout the owner typed, for tests that care about one field at a time. Every figure is invented.
 * The default energy is what the sheet would estimate for it: 45 minutes of moderate strength work on
 * `aProfile()`'s 80 kg is (3.5 − 1) × 80 × 0.75 = 150 kcal by [MetEstimate].
 */
fun aTypedWorkout(
    id: Long = 1,
    epochDay: Long = TEST_EPOCH_DAY,
    kind: WorkoutKind = WorkoutKind.STRENGTH,
    minutes: Int = 45,
    distanceM: Int? = null,
    energyKcal: Int? = 150,
    energySource: EnergySource = EnergySource.MET_ESTIMATE,
    effort: Effort = Effort.MODERATE,
    hidden: Boolean = false,
    note: String? = null,
    startedAtMillis: Long = 0,
) = Workout(
    id = id, epochDay = epochDay, startedAtMillis = startedAtMillis, durationMinutes = minutes,
    kind = kind, title = null, distanceM = distanceM, energyKcal = energyKcal,
    energySource = energySource, effort = effort, source = WorkoutSource.TYPED,
    hidden = hidden, note = note,
)

/** An invented instant for session tests: [minute] minutes after a round base. */
fun at(minute: Int): Long = 1_000_000_000L + minute * 60_000L

/**
 * A session copied from another app, for tests that care about its time and one or two figures. Every
 * figure is invented. It starts at [from] minutes after the base of [at] and lasts [minutes].
 */
fun aSyncedWorkout(
    id: Long,
    from: Int = 0,
    minutes: Int = 60,
    kind: WorkoutKind = WorkoutKind.WALK,
    title: String? = null,
    distanceM: Int? = null,
    distanceSource: WorkoutFigureSource? = null,
    energyKcal: Int? = null,
    energySource: EnergySource = EnergySource.NONE,
    avgHeartRate: Int? = null,
    hidden: Boolean = false,
    counted: Boolean = true,
    ownDistance: Boolean = false,
    steps: Int? = null,
    stepsSource: WorkoutFigureSource? = null,
) = Workout(
    id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = at(from), durationMinutes = minutes,
    kind = kind, title = title, distanceM = distanceM, energyKcal = energyKcal,
    energySource = energySource, effort = null, source = WorkoutSource.SYNCED,
    hidden = hidden, note = null, avgHeartRate = avgHeartRate, counted = counted,
    distanceSource = distanceSource, steps = steps, stepsSource = stepsSource, ownDistance = ownDistance,
)
