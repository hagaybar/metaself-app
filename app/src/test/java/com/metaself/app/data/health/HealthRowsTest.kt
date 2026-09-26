package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.health.HealthKind
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Every figure is invented and round. Times are built from local dates so the day logic is visible. */
class HealthRowsTest {

    private val zone: ZoneId = ZoneOffset.ofHours(3)
    private val rows = HealthRows(zone)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val day = LocalDate.of(2026, 9, 3)

    @Test
    fun `a series becomes one row per sample, numbered, under one record id`() {
        val record = ReadRecord.Reading(
            kind = HealthKind.HEART_RATE,
            origin = ORIGIN,
            recordId = "hr-1",
            samples = listOf(
                Sample(at(day, 10), null, 60.0),
                Sample(at(day, 10, 1), null, 62.0),
            ),
        )

        val out = rows.readings(record)

        assertThat(out.map { it.sampleIndex }).containsExactly(0, 1).inOrder()
        assertThat(out.map { it.recordId }.distinct()).containsExactly("hr-1")
        assertThat(out.map { it.unit }.distinct()).containsExactly("bpm")
        assertThat(out.map { it.epochDay }.distinct()).containsExactly(day.toEpochDay())
    }

    /** A reading belongs to the local day it starts on. */
    @Test
    fun `a span that crosses midnight belongs to the day it started`() {
        val record = ReadRecord.Reading(
            kind = HealthKind.STEPS,
            origin = ORIGIN,
            recordId = "st-1",
            samples = listOf(Sample(at(day, 23, 50), at(day.plusDays(1), 0, 10), 400.0)),
        )

        assertThat(rows.readings(record).single().epochDay).isEqualTo(day.toEpochDay())
    }

    /** D68: a night belongs to the day it ends. */
    @Test
    fun `a night belongs to the morning it ends on, with its stages`() {
        val night = ReadRecord.Night(
            origin = ORIGIN,
            recordId = "s-1",
            startMillis = at(day, 23),
            endMillis = at(day.plusDays(1), 7),
            title = null,
            stages = listOf(StageSpan("DEEP", at(day, 23), at(day.plusDays(1), 1))),
        )

        val (session, stages) = rows.night(night)

        assertThat(session.epochDay).isEqualTo(day.plusDays(1).toEpochDay())
        assertThat(stages.single().stage).isEqualTo("DEEP")
    }

    @Test
    fun `a session becomes a synced workout whose energy says it came from the band`() {
        val session = ReadRecord.Session(
            origin = ORIGIN,
            recordId = "w-1",
            startMillis = at(day, 7),
            endMillis = at(day, 7, 30),
            kind = "RUN",
            title = "Running",
            distanceM = 5_000,
            energyKcal = 300,
        )

        val workout = rows.workout(session)

        assertThat(workout.epochDay).isEqualTo(day.toEpochDay())
        assertThat(workout.durationMinutes).isEqualTo(30)
        assertThat(workout.source).isEqualTo("SYNCED")
        assertThat(workout.energySource).isEqualTo("BAND")
        assertThat(workout.origin).isEqualTo(ORIGIN)
        assertThat(workout.originId).isEqualTo("w-1")
        assertThat(workout.effort).isNull()
    }

    @Test
    fun `a workout's length is rounded to the nearest minute`() {
        val start = at(day, 7)
        val session = ReadRecord.Session(ORIGIN, "w-3", start, start + 29 * 60_000 + 45_000, "RUN", null, null, null)

        assertThat(rows.workout(session).durationMinutes).isEqualTo(30)
    }

    /** A phone that travels files each row by the zone it is in when the row is made. */
    @Test
    fun `the zone is asked for each time, not fixed once`() {
        var current: ZoneId = ZoneOffset.UTC
        val travelling = HealthRows { current }
        val lateEvening = day.atTime(22, 0).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()

        val before = travelling.dayOf(lateEvening)
        current = ZoneOffset.ofHours(3)
        val after = travelling.dayOf(lateEvening)

        assertThat(before).isEqualTo(day.toEpochDay())
        assertThat(after).isEqualTo(day.plusDays(1).toEpochDay())
    }

    @Test
    fun `a session with no energy says so rather than guessing`() {
        val session = ReadRecord.Session(ORIGIN, "w-2", at(day, 7), at(day, 8), "OTHER", null, null, null)

        val workout = rows.workout(session)

        assertThat(workout.energyKcal).isNull()
        assertThat(workout.energySource).isEqualTo("NONE")
    }

    private companion object {
        const val ORIGIN = "com.example.band"
    }
}
