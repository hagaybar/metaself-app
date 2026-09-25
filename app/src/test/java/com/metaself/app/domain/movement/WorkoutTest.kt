package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WorkoutTest {

    private fun run(minutes: Int, distanceM: Int?) = Workout(
        id = 1, epochDay = 20_699, startedAtMillis = 0, durationMinutes = minutes,
        kind = WorkoutKind.RUN, title = null, distanceM = distanceM,
        energyKcal = null, energySource = EnergySource.NONE, effort = null,
        source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    /** 10 km in an hour is six minutes a kilometre. */
    @Test
    fun `pace is minutes per kilometre`() {
        assertThat(run(minutes = 60, distanceM = 10_000).paceSecondsPerKm).isEqualTo(360)
        assertThat(run(minutes = 30, distanceM = 5_000).paceSecondsPerKm).isEqualTo(360)
    }

    @Test
    fun `without a distance there is no pace`() {
        assertThat(run(minutes = 30, distanceM = null).paceSecondsPerKm).isNull()
        assertThat(run(minutes = 30, distanceM = 0).paceSecondsPerKm).isNull()
    }

    /** Stored as strings (D4's pattern); a value nobody recognises reads back as such, never crashes. */
    @Test
    fun `an unknown kind reads back as unrecognised`() {
        assertThat(WorkoutKind.parse("RUN")).isEqualTo(WorkoutKind.RUN)
        assertThat(WorkoutKind.parse("SKATEBOARD")).isEqualTo(WorkoutKind.UNRECOGNISED)
        assertThat(WorkoutKind.parse(null)).isEqualTo(WorkoutKind.UNRECOGNISED)
    }
}
