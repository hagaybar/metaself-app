package com.metaself.app.domain.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/**
 * What arrived, counted (D80). TEST_EPOCH_DAY is 3 Sep 2026, so the window is 5 Aug (20,670) to 3 Sep.
 * Every count is invented and round; `com.example.band` and `com.example.phone` are invented apps.
 */
class BandReportTest {

    private val today = TEST_EPOCH_DAY
    private val from = BandReport.fromDayFor(today)

    @Test
    fun `the window is today and the 29 days before it`() {
        assertThat(from).isEqualTo(today - 29)
        assertThat(report().windowDays).isEqualTo(30)
    }

    @Test
    fun `every kind is listed, in order, those with nothing included`() {
        assertThat(report().kinds.map { it.kind }).containsExactlyElementsIn(HealthKind.entries).inOrder()
        assertThat(report().kinds.single { it.kind == HealthKind.WEIGHT })
            .isEqualTo(KindArrivals(HealthKind.WEIGHT, count = 0, days = 0, origins = emptyList(), firstDay = null, lastDay = null))
    }

    @Test
    fun `a kind's rows are summed, its days counted once, and its apps listed most first`() {
        val arrivals = (from..today).map { Arrival(HealthKind.STEPS, BAND, it, 10) } +
            Arrival(HealthKind.STEPS, PHONE, today, 5)

        val steps = report(arrivals = arrivals).kinds.single { it.kind == HealthKind.STEPS }

        assertThat(steps).isEqualTo(
            KindArrivals(HealthKind.STEPS, count = 305, days = 30, origins = listOf(BAND, PHONE), firstDay = from, lastDay = today),
        )
    }

    @Test
    fun `rows outside the window are not counted`() {
        val arrivals = listOf(
            Arrival(HealthKind.HEART_RATE, BAND, from - 1, 100),
            Arrival(HealthKind.HEART_RATE, BAND, today - 9, 400),
            Arrival(HealthKind.HEART_RATE, BAND, today, 100),
        )

        val beats = report(arrivals = arrivals).kinds.single { it.kind == HealthKind.HEART_RATE }

        assertThat(beats.count).isEqualTo(500)
        assertThat(beats.days).isEqualTo(2)
        assertThat(beats.firstDay).isEqualTo(today - 9)
    }

    @Test
    fun `workouts arrive from the copied sessions, not from any row handed in as exercise`() {
        val report = report(
            arrivals = listOf(Arrival(HealthKind.EXERCISE, BAND, today, 7)),
            workouts = listOf(copied(WorkoutKind.WALK), copied(WorkoutKind.RUN), typed(WorkoutKind.SWIM)),
        )

        val exercise = report.kinds.single { it.kind == HealthKind.EXERCISE }
        assertThat(exercise.count).isEqualTo(2)
        assertThat(exercise.origins).containsExactly(BAND)
    }

    @Test
    fun `workouts are counted by kind, by source, and by the details the copied ones carry`() {
        val workouts = List(3) { copied(WorkoutKind.WALK, distance = true, heartRate = true, title = true) } +
            copied(WorkoutKind.RUN, calories = true, heartRate = true) +
            typed(WorkoutKind.SWIM)

        assertThat(report(workouts = workouts).workouts).isEqualTo(
            WorkoutArrivals(
                total = 5,
                byKind = listOf(WorkoutKind.WALK to 3, WorkoutKind.RUN to 1, WorkoutKind.SWIM to 1),
                copied = 4,
                typed = 1,
                copiedWithDistance = 3,
                copiedWithCalories = 1,
                copiedWithHeartRate = 4,
                copiedWithTitle = 3,
                apps = listOf(WorkoutApp(BAND, workouts = 4, walks = 3, withDistance = 3, withOwnDistance = 0, walksCounted = true)),
            ),
        )
    }

    /** D81: a walk from an app switched off is still counted as arrived, and marked as not counted. */
    @Test
    fun `walks from an app switched off are counted as arrived and as not counted`() {
        val workouts = List(3) { copied(WorkoutKind.WALK) } + copied(WorkoutKind.RUN) +
            copied(WorkoutKind.WALK, origin = PHONE) + typed(WorkoutKind.WALK)

        val report = report(workouts = workouts, uncounted = setOf(BAND))

        assertThat(report.workouts.total).isEqualTo(6)
        assertThat(report.workouts.notCounted).isEqualTo(3)
        assertThat(report.uncountedWalkApps).containsExactly(BAND)
    }

    @Test
    fun `each app that wrote workouts is listed, most first, with its walks and distances`() {
        val workouts = listOf(
            copied(WorkoutKind.WALK, distance = true, ownDistance = true),
            copied(WorkoutKind.WALK, distance = true),
            copied(WorkoutKind.RUN),
            copied(WorkoutKind.WALK, origin = PHONE),
            typed(WorkoutKind.WALK),
        )

        assertThat(report(workouts = workouts, uncounted = setOf(PHONE)).workouts.apps).containsExactly(
            WorkoutApp(BAND, workouts = 3, walks = 2, withDistance = 2, withOwnDistance = 1, walksCounted = true),
            WorkoutApp(PHONE, workouts = 1, walks = 1, withDistance = 0, withOwnDistance = 0, walksCounted = false),
        ).inOrder()
    }

    @Test
    fun `apps with as many workouts are listed by name`() {
        val workouts = listOf(copied(WorkoutKind.RUN, origin = PHONE), copied(WorkoutKind.RUN))

        assertThat(report(workouts = workouts).workouts.apps.map { it.origin }).containsExactly(BAND, PHONE).inOrder()
    }

    /** So a choice can always be undone from the page that made it. */
    @Test
    fun `an app switched off with no workouts in the window is still listed`() {
        val apps = report(workouts = listOf(copied(WorkoutKind.RUN)), uncounted = setOf(PHONE)).workouts.apps

        assertThat(apps).containsExactly(
            WorkoutApp(BAND, workouts = 1, walks = 0, withDistance = 0, withOwnDistance = 0, walksCounted = true),
            WorkoutApp(PHONE, workouts = 0, walks = 0, withDistance = 0, withOwnDistance = 0, walksCounted = false),
        ).inOrder()
    }

    @Test
    fun `each figure of the daily summary is counted on the days it has a value`() {
        val days = (from..today).map { day ->
            DayCoverage(day, if (day == today - 1) setOf(DayFigure.STEPS, DayFigure.SLEEP) else setOf(DayFigure.STEPS))
        }

        val counted = report(days = days).daysWith

        assertThat(counted.keys).containsExactlyElementsIn(DayFigure.entries)
        assertThat(counted[DayFigure.STEPS]).isEqualTo(30)
        assertThat(counted[DayFigure.SLEEP]).isEqualTo(1)
        assertThat(counted[DayFigure.OXYGEN]).isEqualTo(0)
    }

    @Test
    fun `the apps that wrote anything are gathered once`() {
        val report = report(
            arrivals = listOf(Arrival(HealthKind.STEPS, BAND, today, 1), Arrival(HealthKind.WEIGHT, PHONE, today, 1)),
            workouts = listOf(copied(WorkoutKind.WALK)),
        )

        assertThat(report.origins).containsExactly(BAND, PHONE)
    }

    private fun report(
        arrivals: List<Arrival> = emptyList(),
        workouts: List<ArrivedWorkout> = emptyList(),
        days: List<DayCoverage> = emptyList(),
        uncounted: Set<String> = emptySet(),
    ) = BandReport.of(from, today, arrivals, workouts, days, uncounted)

    private fun copied(
        kind: WorkoutKind,
        distance: Boolean = false,
        calories: Boolean = false,
        heartRate: Boolean = false,
        title: Boolean = false,
        origin: String = BAND,
        ownDistance: Boolean = false,
    ) = ArrivedWorkout(today, kind, typed = false, origin = origin, distance, calories, heartRate, title, ownDistance)

    private fun typed(kind: WorkoutKind) =
        ArrivedWorkout(today, kind, typed = true, origin = null, hasDistance = false, hasCalories = false, hasHeartRate = false, hasTitle = false)

    private companion object {
        const val BAND = "com.example.band"
        const val PHONE = "com.example.phone"
    }
}
