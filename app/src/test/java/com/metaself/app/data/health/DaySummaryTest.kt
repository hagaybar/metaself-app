package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/** Every figure is invented and round. */
class DaySummaryTest {

    private val day = TEST_EPOCH_DAY

    @Test
    fun `nothing at all is no summary`() {
        assertThat(DaySummary.of(day, DayTotals(), emptyList(), emptyList(), emptyList(), null, 1_000)).isNull()
    }

    /** D69: the four totals are Health Connect's de-duplicated aggregate, never the rows added up. */
    @Test
    fun `the totals are taken as given and say so`() {
        val rows = listOf(reading("STEPS", 999_999.0))

        val summary = DaySummary.of(
            day, DayTotals(steps = 9_000, distanceM = 6_000, activeKcal = 300, totalKcal = 2_400),
            rows, emptyList(), emptyList(), null, 1_000,
        )!!

        assertThat(summary.steps).isEqualTo(9_000)
        assertThat(summary.stepsSource).isEqualTo("TOTAL")
        assertThat(summary.distanceM).isEqualTo(6_000)
        assertThat(summary.activeKcal).isEqualTo(300)
        assertThat(summary.totalKcal).isEqualTo(2_400)
        assertThat(summary.totalKcalSource).isEqualTo("TOTAL")
    }

    @Test
    fun `averages are computed from the day's readings, and say so`() {
        val summary = DaySummary.of(
            day, DayTotals(),
            listOf(
                reading("HEART_RATE", 60.0), reading("HEART_RATE", 80.0),
                reading("HRV_RMSSD", 40.0),
                reading("OXYGEN_SATURATION", 96.0), reading("OXYGEN_SATURATION", 98.0),
                reading("RESPIRATORY_RATE", 14.0),
            ),
            emptyList(), emptyList(), null, 1_000,
        )!!

        assertThat(summary.avgHeartRate).isEqualTo(70)
        assertThat(summary.avgHeartRateSource).isEqualTo("COMPUTED")
        assertThat(summary.hrvMs).isEqualTo(40.0)
        assertThat(summary.oxygenPct).isEqualTo(97.0)
        assertThat(summary.respiratoryRate).isEqualTo(14.0)
    }

    /** The band's own resting figure is read, never worked out here; the latest one wins. */
    @Test
    fun `the resting heart rate is the day's last reading of it`() {
        val summary = DaySummary.of(
            day, DayTotals(),
            listOf(reading("RESTING_HEART_RATE", 60.0, at = 1_000), reading("RESTING_HEART_RATE", 58.0, at = 2_000)),
            emptyList(), emptyList(), null, 1_000,
        )!!

        assertThat(summary.restingHeartRate).isEqualTo(58)
        assertThat(summary.restingHeartRateSource).isEqualTo("READ")
    }

    @Test
    fun `a night's stages add up into the day it ended`() {
        val night = SleepNight(
            session = SleepSessionEntity(1, day, 0, 480 * MINUTE, ORIGIN, "s-1", null),
            stages = listOf(
                stage("LIGHT", 0, 240), stage("DEEP", 240, 330), stage("REM", 330, 420),
                stage("AWAKE", 420, 450), stage("SLEEPING", 450, 480),
            ),
        )

        val summary = DaySummary.of(day, DayTotals(), emptyList(), listOf(night), emptyList(), null, 1_000)!!

        assertThat(summary.lightMinutes).isEqualTo(240)
        assertThat(summary.deepMinutes).isEqualTo(90)
        assertThat(summary.remMinutes).isEqualTo(90)
        assertThat(summary.awakeMinutes).isEqualTo(30)
        assertThat(summary.sleepMinutes).isEqualTo(450)
        assertThat(summary.sleepSource).isEqualTo("COMPUTED")
    }

    @Test
    fun `a night with no stages counts its whole length as sleep`() {
        val night = SleepNight(SleepSessionEntity(1, day, 0, 420 * MINUTE, ORIGIN, "s-1", null), emptyList())

        val summary = DaySummary.of(day, DayTotals(), emptyList(), listOf(night), emptyList(), null, 1_000)!!

        assertThat(summary.sleepMinutes).isEqualTo(420)
        assertThat(summary.deepMinutes).isNull()
    }

    @Test
    fun `hidden workouts are not counted`() {
        val summary = DaySummary.of(
            day, DayTotals(), emptyList(), emptyList(),
            listOf(workout(30), workout(45), workout(60).copy(hidden = true)),
            null, 1_000,
        )!!

        assertThat(summary.workoutCount).isEqualTo(2)
        assertThat(summary.workoutMinutes).isEqualTo(75)
    }

    /** D12d: the owner's figure wins, and the summary says it is his. */
    @Test
    fun `a correction replaces what was read and says so`() {
        val summary = DaySummary.of(
            day, DayTotals(steps = 30_000, activeKcal = 900), emptyList(), emptyList(), emptyList(),
            MovementCorrectionEntity(day, steps = 8_000, activeKcal = null, setAtMillis = 1, note = null),
            1_000,
        )!!

        assertThat(summary.steps).isEqualTo(8_000)
        assertThat(summary.stepsSource).isEqualTo("CORRECTED")
        assertThat(summary.activeKcal).isEqualTo(900)
        assertThat(summary.activeKcalSource).isEqualTo("TOTAL")
    }

    private fun reading(kind: String, value: Double, at: Long = 1_000) = HealthReadingEntity(
        kind = kind, startMillis = at, endMillis = null, value = value, unit = "u",
        origin = ORIGIN, recordId = "r-$kind-$at-$value", epochDay = day,
    )

    private fun stage(name: String, fromMinute: Int, toMinute: Int) =
        SleepStageEntity(sessionId = 1, stage = name, startMillis = fromMinute * MINUTE, endMillis = toMinute * MINUTE)

    private fun workout(minutes: Int) = WorkoutEntity(
        epochDay = day, startedAtMillis = 0, durationMinutes = minutes, kind = "RUN", title = null,
        distanceM = null, energyKcal = null, energySource = "NONE", effort = null, source = "SYNCED",
        origin = ORIGIN, originId = "w-$minutes", note = null,
    )

    private companion object {
        const val ORIGIN = "com.example.band"
        const val MINUTE = 60_000L
    }
}
