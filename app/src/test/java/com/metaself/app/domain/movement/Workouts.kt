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
