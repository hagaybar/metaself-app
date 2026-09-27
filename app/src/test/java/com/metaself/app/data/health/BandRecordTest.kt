package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/** Stored rows, as the band report reads them (D80). Every figure is invented. */
class BandRecordTest {

    @Test
    fun `a count of a kind this version knows becomes an arrival, and one it does not is left out`() {
        assertThat(ReadingCount("STEPS", BAND, TEST_EPOCH_DAY, 10).toArrival())
            .isEqualTo(Arrival(HealthKind.STEPS, BAND, TEST_EPOCH_DAY, 10))
        assertThat(ReadingCount("SKIN_TEMPERATURE", BAND, TEST_EPOCH_DAY, 10).toArrival()).isNull()
    }

    @Test
    fun `a night is one sleep arrival on its waking day`() {
        val night = SleepSessionEntity(epochDay = TEST_EPOCH_DAY, startMillis = 0, endMillis = 1, origin = BAND, recordId = "n1", title = null)

        assertThat(night.toArrival()).isEqualTo(Arrival(HealthKind.SLEEP, BAND, TEST_EPOCH_DAY, 1))
    }

    @Test
    fun `a copied workout says which details it carries, and only the band's calories count`() {
        val band = aWorkout(distanceM = 5_000, energyKcal = 300, energySource = "BAND", avgHeartRate = 120, title = "Walk").toArrived()
        val estimated = aWorkout(energyKcal = 300, energySource = "MET_ESTIMATE", title = " ").toArrived()

        assertThat(band.typed).isFalse()
        assertThat(band.origin).isEqualTo(BAND)
        assertThat(band.kind).isEqualTo(WorkoutKind.WALK)
        assertThat(listOf(band.hasDistance, band.hasCalories, band.hasHeartRate, band.hasTitle)).containsExactly(true, true, true, true)
        assertThat(listOf(estimated.hasDistance, estimated.hasCalories, estimated.hasHeartRate, estimated.hasTitle))
            .containsExactly(false, false, false, false)
    }

    @Test
    fun `a typed workout is typed`() {
        assertThat(aWorkout(source = "TYPED", origin = null).toArrived().typed).isTrue()
    }

    @Test
    fun `a stored day says which figures it has`() {
        val day = HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 0, steps = 9_000, hrvMs = 40.0, sleepMinutes = 420, workoutCount = 1)

        assertThat(day.toCoverage().figures)
            .containsExactly(DayFigure.STEPS, DayFigure.HRV, DayFigure.SLEEP, DayFigure.WORKOUTS)
        assertThat(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 0).toCoverage().figures).isEmpty()
    }

    private fun aWorkout(
        source: String = "SYNCED",
        origin: String? = BAND,
        distanceM: Int? = null,
        energyKcal: Int? = null,
        energySource: String = "NONE",
        avgHeartRate: Int? = null,
        title: String? = null,
    ) = WorkoutEntity(
        epochDay = TEST_EPOCH_DAY, startedAtMillis = 0, durationMinutes = 30, kind = "WALK", title = title,
        distanceM = distanceM, energyKcal = energyKcal, energySource = energySource, effort = null,
        source = source, origin = origin, originId = origin?.let { "s1" }, note = null, avgHeartRate = avgHeartRate,
    )

    private companion object {
        const val BAND = "com.example.band"
    }
}
