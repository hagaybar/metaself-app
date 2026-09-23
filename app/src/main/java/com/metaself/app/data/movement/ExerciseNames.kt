package com.metaself.app.data.movement

import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * What to call a workout on screen.
 *
 * Health Connect identifies an exercise by a number. A handful of the commonest are named here, and
 * everything else falls back to the title the recording app gave it, or to the plain word
 * "Exercise".
 *
 * Falling back rather than listing all ninety types is deliberate: an unnamed session still shows,
 * still counts, and still says how long it went on. A missing name costs a word; a missing session
 * would cost the thing itself.
 */
object ExerciseNames {

    fun of(exerciseType: Int, title: String?): String = KNOWN[exerciseType]
        ?: title?.takeIf { it.isNotBlank() }
        ?: "Exercise"

    private val KNOWN: Map<Int, String> = mapOf(
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "Walking",
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "Running",
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "Running",
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "Hiking",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Cycling",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "Cycling",
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "Swimming",
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "Swimming",
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "Weights",
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING to "Weights",
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA to "Yoga",
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES to "Pilates",
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to "Rowing",
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL to "Elliptical",
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING to "Stairs",
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING to "Dancing",
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS to "Tennis",
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL to "Basketball",
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN to "Football",
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER to "Football",
    )
}
