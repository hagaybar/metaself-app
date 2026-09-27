package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/**
 * The typed workouts as the day's third reading (D60, D77). Invented, round figures on `aProfile()`'s
 * 80 kg: 8,000 steps are 8,000 × 0.000375 × 80 = 240 kcal.
 */
class TypedReadingTest {

    @Test
    fun `a day's visible typed workouts are summed, since two sessions are two things done`() {
        val workouts = listOf(
            aTypedWorkout(id = 1, energyKcal = 150),
            aTypedWorkout(id = 2, energyKcal = 100),
            aTypedWorkout(id = 3, energyKcal = 400, hidden = true),
            aTypedWorkout(id = 4, energyKcal = 500).copy(source = WorkoutSource.SYNCED),
            aTypedWorkout(id = 5, energyKcal = null, energySource = EnergySource.NONE),
            aTypedWorkout(id = 6, epochDay = TEST_EPOCH_DAY - 1, energyKcal = 90),
        )

        assertThat(TypedReading.kcalByDay(workouts))
            .containsExactly(TEST_EPOCH_DAY, 250, TEST_EPOCH_DAY - 1, 90)
    }

    @Test
    fun `a day with typed workouts but no energy has no reading`() {
        assertThat(TypedReading.kcalByDay(listOf(aTypedWorkout(energyKcal = null, energySource = EnergySource.NONE))))
            .isEmpty()
    }

    /** Plan design question 13: a past day the phone did not record is not made up. */
    @Test
    fun `typed kcal is laid into the days the phone recorded, and no past day is added`() {
        val history = listOf(DayMovement(TEST_EPOCH_DAY - 1, steps = 8_000), DayMovement(TEST_EPOCH_DAY, steps = 8_000))

        val merged = TypedReading.merge(history, mapOf(TEST_EPOCH_DAY to 300, TEST_EPOCH_DAY - 5 to 200), TEST_EPOCH_DAY)

        assertThat(merged.map { it.epochDay }).containsExactly(TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY).inOrder()
        assertThat(merged.map { it.typedWorkoutsKcal }).containsExactly(0, 300).inOrder()
    }

    /** Before the first step is recorded today, a workout typed today is still today's reading. */
    @Test
    fun `a typed workout counts today before any step is recorded`() {
        val history = listOf(DayMovement(TEST_EPOCH_DAY - 1, steps = 8_000))

        val merged = TypedReading.merge(history, mapOf(TEST_EPOCH_DAY to 300), TEST_EPOCH_DAY)

        val today = merged.single { it.epochDay == TEST_EPOCH_DAY }
        assertThat(today).isEqualTo(DayMovement(TEST_EPOCH_DAY, steps = 0, typedWorkoutsKcal = 300))
        assertThat(ActivityEnergy.of(today, 80.0)).isEqualTo(ActivityEnergy(300, MovementSource.TYPED_WORKOUT))
    }

    @Test
    fun `a stepless today with nothing typed is still absent, and past stepless days are unchanged`() {
        val history = listOf(DayMovement(TEST_EPOCH_DAY - 1, steps = 8_000))

        val merged = TypedReading.merge(history, mapOf(TEST_EPOCH_DAY - 3 to 200), TEST_EPOCH_DAY)

        assertThat(merged.map { it.epochDay }).containsExactly(TEST_EPOCH_DAY - 1)
    }

    /** D60: the largest of three, never their sum. */
    @Test
    fun `a typed workout joins the maximum and is never added to it`() {
        val day = DayMovement(TEST_EPOCH_DAY, steps = 8_000, activeKcal = 400)

        val smaller = TypedReading.merge(listOf(day), mapOf(TEST_EPOCH_DAY to 300), TEST_EPOCH_DAY).single()
        val larger = TypedReading.merge(listOf(day), mapOf(TEST_EPOCH_DAY to 500), TEST_EPOCH_DAY).single()

        assertThat(ActivityEnergy.of(smaller, 80.0)).isEqualTo(ActivityEnergy(400, MovementSource.ACTIVE_CALORIES))
        assertThat(ActivityEnergy.of(larger, 80.0)).isEqualTo(ActivityEnergy(500, MovementSource.TYPED_WORKOUT))
    }
}
