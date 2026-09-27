package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** The stored rows, as the Movement screen reads them. Every figure is invented. */
class MovementRecordTest {

    @Test
    fun `a stored day keeps every figure and where it came from`() {
        val stored = HealthDayEntity(
            epochDay = 20_699, computedAtMillis = 0,
            steps = 9_000, stepsSource = "CORRECTED",
            distanceM = 8_000, distanceSource = "TOTAL",
            activeKcal = 410, activeKcalSource = "TOTAL",
            restingHeartRate = 58, restingHeartRateSource = "READ",
            hrvMs = 42.0, hrvSource = "COMPUTED",
            oxygenPct = 97.0, oxygenSource = "COMPUTED",
            respiratoryRate = 14.0, respiratoryRateSource = "COMPUTED",
            sleepMinutes = 430, deepMinutes = 80, lightMinutes = 255, remMinutes = 95, sleepSource = "COMPUTED",
        )

        assertThat(stored.toHealthDay()).isEqualTo(
            HealthDay(
                epochDay = 20_699,
                steps = 9_000, stepsSource = FigureSource.CORRECTED,
                distanceM = 8_000,
                activeKcal = 410, activeKcalSource = FigureSource.TOTAL,
                restingHeartRate = 58, hrvMs = 42.0, oxygenPct = 97.0, respiratoryRate = 14.0,
                sleepMinutes = 430, deepMinutes = 80, remMinutes = 95, lightMinutes = 255,
            ),
        )
    }

    @Test
    fun `a day with nothing recorded reads as nothing, not zeros`() {
        assertThat(HealthDayEntity(epochDay = 20_699, computedAtMillis = 0).toHealthDay())
            .isEqualTo(HealthDay(epochDay = 20_699))
    }

    @Test
    fun `a stored workout keeps whether it is hidden, and its heart rate`() {
        val workout = aWorkout(hidden = true, avgHeartRate = 142).toWorkout()

        assertThat(workout.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(workout.title).isEqualTo("Running")
        assertThat(workout.durationMinutes).isEqualTo(32)
        assertThat(workout.distanceM).isEqualTo(6_200)
        assertThat(workout.hidden).isTrue()
        assertThat(workout.avgHeartRate).isEqualTo(142)
    }

    @Test
    fun `a stored name this version does not know never breaks the read`() {
        val workout = aWorkout(kind = "SKATEBOARD", energySource = "GUESSED", effort = "BRUTAL", source = "BEAMED")
            .toWorkout()

        assertThat(workout.kind).isEqualTo(WorkoutKind.UNRECOGNISED)
        assertThat(workout.energySource).isEqualTo(EnergySource.NONE)
        assertThat(workout.effort).isNull()
        assertThat(workout.source).isEqualTo(WorkoutSource.SYNCED)
    }

    private fun aWorkout(
        kind: String = "RUN",
        energySource: String = "NONE",
        effort: String? = null,
        source: String = "SYNCED",
        hidden: Boolean = false,
        avgHeartRate: Int? = null,
    ) = WorkoutEntity(
        id = 7, epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 32,
        kind = kind, title = "Running", distanceM = 6_200, energyKcal = null,
        energySource = energySource, effort = effort, source = source,
        origin = "com.example.band", originId = "session-1",
        hidden = hidden, note = null, avgHeartRate = avgHeartRate,
    )
}
