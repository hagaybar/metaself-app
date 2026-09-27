package com.metaself.app.ui.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.KindArrivals
import com.metaself.app.domain.health.WorkoutArrivals
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/**
 * Every sentence of "What the band sends" (D80). TEST_EPOCH_DAY is 3 Sep 2026; the window starts
 * 5 Aug. Every count is invented and round; the apps are invented.
 */
class BandReportWordingTest {

    private val today = TEST_EPOCH_DAY
    private val from = BandReport.fromDayFor(today)

    @Test
    fun `a kind that arrived says how many, on how many days, from which app`() {
        val steps = KindArrivals(HealthKind.STEPS, 1_200, 30, listOf(BAND), from, today)

        assertThat(BandReportWording.kindLine(steps, emptySet(), emptyMap()))
            .isEqualTo("1,200 readings · 30 days · from com.example.band")
        assertThat(BandReportWording.kindDates(steps, today)).isEqualTo("first 5 Aug · last today")
    }

    @Test
    fun `an app the phone knows is named by its label`() {
        val steps = KindArrivals(HealthKind.STEPS, 1, 1, listOf(BAND, PHONE), today - 1, today - 1)

        assertThat(BandReportWording.kindLine(steps, emptySet(), mapOf(BAND to "Example Band")))
            .isEqualTo("1 reading · 1 day · from Example Band and com.example.phone")
        assertThat(BandReportWording.kindDates(steps, today)).isEqualTo("first 2 Sep · last 2 Sep")
    }

    @Test
    fun `three apps are listed with commas and a last and`() {
        val beats = KindArrivals(HealthKind.HEART_RATE, 30, 1, listOf("a.one", "b.two", "c.three"), today, today)

        assertThat(BandReportWording.kindLine(beats, emptySet(), emptyMap()))
            .isEqualTo("30 readings · 1 day · from a.one, b.two and c.three")
    }

    @Test
    fun `sleep counts nights and workouts count workouts`() {
        val sleep = KindArrivals(HealthKind.SLEEP, 20, 20, listOf(BAND), from, today)
        val exercise = KindArrivals(HealthKind.EXERCISE, 1, 1, listOf(BAND), today, today)

        assertThat(BandReportWording.kindLine(sleep, emptySet(), emptyMap())).startsWith("20 nights · 20 days")
        assertThat(BandReportWording.kindLine(exercise, emptySet(), emptyMap())).startsWith("1 workout · 1 day")
    }

    @Test
    fun `a kind with nothing says so, or that it is not allowed`() {
        val nothing = KindArrivals(HealthKind.WEIGHT, 0, 0, emptyList(), null, null)

        assertThat(BandReportWording.kindLine(nothing, emptySet(), emptyMap())).isEqualTo("nothing arrived")
        assertThat(BandReportWording.kindLine(nothing, setOf(HealthKind.WEIGHT), emptyMap())).isEqualTo("not allowed")
        assertThat(BandReportWording.kindDates(nothing, today)).isNull()
    }

    @Test
    fun `workouts are said by count, kind, source and detail`() {
        val workouts = WorkoutArrivals(
            total = 16,
            byKind = listOf(WorkoutKind.WALK to 12, WorkoutKind.RUN to 2, WorkoutKind.OTHER to 1, WorkoutKind.UNRECOGNISED to 1),
            copied = 14, typed = 2,
            copiedWithDistance = 12, copiedWithCalories = 0, copiedWithHeartRate = 14, copiedWithTitle = 14,
        )

        assertThat(BandReportWording.workoutLines(workouts)).containsExactly(
            "16 workouts",
            "Walking 12 · Running 2 · Exercise 2",
            "copied 14 · typed 2",
            "distance 12 of 14 · calories 0 of 14 · heart rate (worked out here) 14 of 14 · title 14 of 14",
        ).inOrder()
    }

    @Test
    fun `typed workouts alone have no details line`() {
        val workouts = WorkoutArrivals(total = 1, byKind = listOf(WorkoutKind.SWIM to 1), copied = 0, typed = 1)

        assertThat(BandReportWording.workoutLines(workouts))
            .containsExactly("1 workout", "Swimming 1", "copied 0 · typed 1").inOrder()
    }

    @Test
    fun `no workouts is one line`() {
        assertThat(BandReportWording.workoutLines(WorkoutArrivals())).containsExactly("none in these 30 days")
    }

    @Test
    fun `each figure of the daily summary is a line of days`() {
        val report = aReport(daysWith = DayFigure.entries.associateWith { 0 } + (DayFigure.STEPS to 30))

        val lines = BandReportWording.dayLines(report)

        assertThat(lines).hasSize(10)
        assertThat(lines.first()).isEqualTo("steps 30 of 30 days")
        assertThat(lines).contains("movement calories 0 of 30 days")
        assertThat(lines.last()).isEqualTo("workouts 0 of 30 days")
    }

    @Test
    fun `the window is said from its first day to today`() {
        assertThat(BandReportWording.window(aReport(), today)).isEqualTo("The last 30 days, 5 Aug to today.")
    }

    @Test
    fun `the line about what Health Connect cannot carry names only what has no record type`() {
        assertThat(BandReportWording.NOT_SHARED)
            .startsWith("Stress, training load and recovery time are not shared through Health Connect")
        assertThat(BandReportWording.NOT_SHARED)
            .contains("VO₂ max has a Health Connect record, but this app does not copy it.")
    }

    @Test
    fun `the copied text holds counts, dates and names, in the page's order`() {
        val report = aReport(
            kinds = HealthKind.entries.map { kind ->
                if (kind == HealthKind.STEPS) {
                    KindArrivals(kind, 300, 30, listOf(BAND), from, today)
                } else {
                    KindArrivals(kind, 0, 0, emptyList(), null, null)
                }
            },
        )

        val text = BandReportWording.asText(report, setOf(HealthKind.WEIGHT), mapOf(BAND to "Example Band"), today)
        val lines = text.lines()

        assertThat(lines.first()).isEqualTo("What the band sends — the last 30 days, 5 Aug to today")
        assertThat(lines).contains("Steps: 300 readings · 30 days · from Example Band · first 5 Aug · last today")
        assertThat(lines).contains("Weight: not allowed")
        assertThat(lines).contains("Distance: nothing arrived")
        assertThat(lines).contains("Workouts: none in these 30 days")
        assertThat(lines.indexOf("Daily summary")).isGreaterThan(lines.indexOf("Workouts: none in these 30 days"))
        assertThat(lines.last()).isEqualTo(BandReportWording.NOT_SHARED)
    }

    private fun aReport(
        kinds: List<KindArrivals> = HealthKind.entries.map { KindArrivals(it, 0, 0, emptyList(), null, null) },
        daysWith: Map<DayFigure, Int> = DayFigure.entries.associateWith { 0 },
    ) = BandReport(from, today, kinds, WorkoutArrivals(), daysWith)

    private companion object {
        const val BAND = "com.example.band"
        const val PHONE = "com.example.phone"
    }
}
