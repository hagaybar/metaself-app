package com.metaself.app.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WorkoutKindsTest {

    @Test
    fun `each family of exercise lands on its kind`() {
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)).isEqualTo("RUN")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)).isEqualTo("RUN")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_WALKING)).isEqualTo("WALK")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_HIKING)).isEqualTo("WALK")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_BIKING)).isEqualTo("CYCLE")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY)).isEqualTo("CYCLE")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL)).isEqualTo("SWIM")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER)).isEqualTo("SWIM")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)).isEqualTo("STRENGTH")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING)).isEqualTo("STRENGTH")
    }

    @Test
    fun `anything else is other, never a run`() {
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_YOGA)).isEqualTo("OTHER")
        assertThat(WorkoutKinds.of(-1)).isEqualTo("OTHER")
    }
}
