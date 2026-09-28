package com.metaself.app.ui.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.KindArrivals
import com.metaself.app.domain.health.WorkoutApp
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

    /** D81. The figures are invented. */
    @Test
    fun `walks not counted are said after the sources`() {
        val workouts = WorkoutArrivals(total = 6, byKind = listOf(WorkoutKind.WALK to 6), copied = 6, notCounted = 4)

        assertThat(BandReportWording.workoutLines(workouts)).containsAtLeast("copied 6 · typed 0", "4 walks not counted").inOrder()
        assertThat(BandReportWording.workoutLines(workouts.copy(notCounted = 1))).contains("1 walk not counted")
        assertThat(BandReportWording.workoutLines(workouts.copy(notCounted = 0)).joinToString()).doesNotContain("not counted")
    }

    /** The figures are invented: 6 workouts, 4 of them walks, 2 with a distance. */
    @Test
    fun `an app's workouts say how many are walks and how many carry a distance`() {
        val app = WorkoutApp(BAND, workouts = 6, walks = 4, withDistance = 2, withOwnDistance = 2, walksCounted = true)

        assertThat(lines(app)).containsExactly("6 workouts · 4 walks", "distance on 2 of 6").inOrder()
    }

    @Test
    fun `an app switched off says its walks are not counted`() {
        val app = WorkoutApp(BAND, workouts = 1, walks = 1, withDistance = 1, withOwnDistance = 1, walksCounted = false)

        assertThat(lines(app).first()).isEqualTo("1 workout · 1 walk, not counted")
    }

    @Test
    fun `an app with no walks says so`() {
        val app = WorkoutApp(BAND, workouts = 2, walks = 0, withDistance = 2, withOwnDistance = 2, walksCounted = true)

        assertThat(lines(app).first()).isEqualTo("2 workouts · no walks")
    }

    /** The spec's line, said only when the app itself wrote no distance during any of its workouts. */
    @Test
    fun `an app that wrote no distance during any of its workouts is said not to share it`() {
        val none = WorkoutApp(BAND, workouts = 4, walks = 4, withDistance = 1, withOwnDistance = 0, walksCounted = true)
        val some = none.copy(withOwnDistance = 1)

        assertThat(lines(none)).contains(BandReportWording.DISTANCE_NOT_SHARED)
        assertThat(lines(some)).doesNotContain(BandReportWording.DISTANCE_NOT_SHARED)
    }

    /** Distance not allowed: nothing is stored to tell, so the app is not blamed. */
    @Test
    fun `distance not allowed never says the app does not share it`() {
        val app = WorkoutApp(BAND, workouts = 4, walks = 4, withDistance = 0, withOwnDistance = 0, walksCounted = true)

        assertThat(BandReportWording.appLines(app, windowDays = 30, distanceAllowed = false))
            .doesNotContain(BandReportWording.DISTANCE_NOT_SHARED)
    }

    /** Every workout has a distance: saying the app shares none would contradict the line above it. */
    @Test
    fun `an app whose every workout has a distance is not said not to share it`() {
        val app = WorkoutApp(BAND, workouts = 4, walks = 4, withDistance = 4, withOwnDistance = 0, walksCounted = true)

        assertThat(lines(app)).containsExactly("4 workouts · 4 walks", "distance on 4 of 4").inOrder()
    }

    @Test
    fun `an app switched off with nothing in the window says both, over the window's own length`() {
        val app = WorkoutApp(PHONE, workouts = 0, walks = 0, withDistance = 0, withOwnDistance = 0, walksCounted = false)

        assertThat(lines(app)).containsExactly("no workouts in these 30 days · walks not counted")
        assertThat(BandReportWording.appLines(app, windowDays = 7, distanceAllowed = true))
            .containsExactly("no workouts in these 7 days · walks not counted")
    }

    @Test
    fun `the copied text names each app with its lines`() {
        val report = aReport().copy(
            workouts = WorkoutArrivals(
                total = 2, byKind = listOf(WorkoutKind.WALK to 2), copied = 2,
                apps = listOf(WorkoutApp(BAND, workouts = 2, walks = 2, withDistance = 0, withOwnDistance = 0, walksCounted = true)),
            ),
        )

        val lines = BandReportWording.asText(report, emptySet(), mapOf(BAND to "Example Band"), today).lines()

        assertThat(lines).contains(
            "Example Band: 2 workouts · 2 walks · distance on 0 of 2 · distance not shared for workouts by this app",
        )
        val withoutDistance = BandReportWording.asText(report, setOf(HealthKind.DISTANCE), mapOf(BAND to "Example Band"), today)
        assertThat(withoutDistance).doesNotContain(BandReportWording.DISTANCE_NOT_SHARED)
    }

    private fun lines(app: WorkoutApp) = BandReportWording.appLines(app, windowDays = 30, distanceAllowed = true)

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
