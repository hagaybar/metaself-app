package com.metaself.app.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord

/** Health Connect's exercise type → the `WorkoutKind` name stored. Anything unlisted is OTHER. */
object WorkoutKinds {

    fun of(exerciseType: Int): String = KNOWN[exerciseType] ?: "OTHER"

    private val KNOWN: Map<Int, String> = mapOf(
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "RUN",
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "RUN",
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "WALK",
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "WALK",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "CYCLE",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "CYCLE",
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "SWIM",
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "SWIM",
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "STRENGTH",
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING to "STRENGTH",
    )
}
