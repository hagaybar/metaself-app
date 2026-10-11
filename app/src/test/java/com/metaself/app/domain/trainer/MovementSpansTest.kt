package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D135–D137: the spans, their bars and their comparisons. Today is Thursday 3 September 2026 ([TEST_EPOCH_DAY]);
 * every session, distance, step count and plan is invented, and round.
 */
class MovementSpansTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")

    // D135, D136: where the spans fall.

    @Test
    fun `twelve weeks end with the week in view, the last one counted to today`() {
        val chart = weeks(emptyList(), recordStart = monday - 365)

        assertThat(chart.bars).hasSize(12)
        assertThat(chart.first).isEqualTo(monday - 77)
        assertThat(chart.bars.map { it.first }).isEqualTo((0 until 12).map { monday - 77 + 7L * it })
        assertThat(chart.bars.last().current).isTrue()
        assertThat(chart.bars.dropLast(1).none { it.current }).isTrue()
        assertThat(chart.last).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `twelve months end with the month in view`() {
        val chart = MovementSpans.months(TEST_EPOCH_DAY, TEST_EPOCH_DAY, emptyList(), emptyList(), emptyList(), recordStart = 0)

        assertThat(chart.bars.map { LocalDate.ofEpochDay(it.first).toString() }.first()).isEqualTo("2025-10-01")
        assertThat(LocalDate.ofEpochDay(chart.bars.last().first).toString()).isEqualTo("2026-09-01")
        assertThat(LocalDate.ofEpochDay(chart.bars.last().last).toString()).isEqualTo("2026-09-30")
        assertThat(chart.bars.last().current).isTrue()
        assertThat(chart.last).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `an earlier span ends with its own week or month and holds nothing current`() {
        val end = monday - 7L * 12 + 6
        val chart = MovementSpans.weeks(end, TEST_EPOCH_DAY, emptyList(), emptyList(), emptyList(), recordStart = 0)

        assertThat(chart.bars.last().first).isEqualTo(monday - 84)
        assertThat(chart.bars.none { it.current }).isTrue()
        assertThat(chart.last).isEqualTo(monday - 78)
    }

    @Test
    fun `arrows move one span and never past the span holding today`() {
        assertThat(MovementSpans.earlier(MovementSpan.WEEK, TEST_EPOCH_DAY)).isEqualTo(TEST_EPOCH_DAY - 7)
        assertThat(MovementSpans.earlier(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY)).isEqualTo(TEST_EPOCH_DAY - 84)
        assertThat(LocalDate.ofEpochDay(MovementSpans.earlier(MovementSpan.YEAR, TEST_EPOCH_DAY)).toString()).isEqualTo("2025-09-30")

        assertThat(MovementSpans.later(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY - 84, TEST_EPOCH_DAY)).isNull()
        assertThat(MovementSpans.later(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY - 168, TEST_EPOCH_DAY)).isEqualTo(TEST_EPOCH_DAY - 84)
        assertThat(MovementSpans.later(MovementSpan.YEAR, LocalDate.of(2025, 9, 30).toEpochDay(), TEST_EPOCH_DAY)).isNull()
        assertThat(MovementSpans.later(MovementSpan.YEAR, LocalDate.of(2024, 9, 30).toEpochDay(), TEST_EPOCH_DAY))
            .isEqualTo(LocalDate.of(2025, 9, 30).toEpochDay())
        assertThat(MovementSpans.later(MovementSpan.WEEK, monday - 1, TEST_EPOCH_DAY)).isNull()

        assertThat(MovementSpans.holdsToday(MovementSpan.YEAR, LocalDate.of(2026, 9, 1).toEpochDay(), TEST_EPOCH_DAY)).isTrue()
        assertThat(MovementSpans.holdsToday(MovementSpan.YEAR, LocalDate.of(2026, 8, 31).toEpochDay(), TEST_EPOCH_DAY)).isFalse()
    }

    @Test
    fun `back is offered only while the record starts before the span`() {
        assertThat(MovementSpans.canGoEarlier(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY, monday - 78)).isTrue()
        assertThat(MovementSpans.canGoEarlier(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY, monday - 77)).isFalse()
        assertThat(MovementSpans.canGoEarlier(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY, null)).isFalse()
        assertThat(MovementSpans.canGoEarlier(MovementSpan.YEAR, TEST_EPOCH_DAY, LocalDate.of(2025, 9, 30).toEpochDay())).isTrue()
        assertThat(MovementSpans.canGoEarlier(MovementSpan.YEAR, TEST_EPOCH_DAY, LocalDate.of(2025, 10, 1).toEpochDay())).isFalse()
    }

    // D136: what a bar counts.

    @Test
    fun `planned minutes are the sessions that ticked the week's plan, the rest are your own`() {
        val sessions = listOf(
            session(1, monday - 7), session(2, monday - 6, WorkoutKind.SWIM, minutes = 30),
            session(3, monday), session(4, monday + 1),
        )
        val plans = listOf(kept(plan(walk30), start = monday - 7))

        val chart = weeks(sessions, plans = plans, recordStart = monday - 365)

        assertThat(chart.bars[10].plannedMinutes).isEqualTo(40)
        assertThat(chart.bars[10].otherMinutes).isEqualTo(30)
        // One planned walk a week: the second walk this week is your own.
        assertThat(chart.bars[11].plannedMinutes).isEqualTo(40)
        assertThat(chart.bars[11].otherMinutes).isEqualTo(40)
        // The same partition as the Trainer's strip for this week.
        val strip = TrainerWeek.of(monday, TEST_EPOCH_DAY, sessions, emptyList(), emptyMap(), null, plans).strip
        assertThat(strip.days.flatMap { it.blocks }.filter { it.ticked }.sumOf { it.minutes }).isEqualTo(chart.bars[11].plannedMinutes)
    }

    @Test
    fun `hidden and uncounted sessions are not counted, and with no plan every minute is your own`() {
        val sessions = listOf(session(1, monday), session(2, monday).copy(hidden = true), session(3, monday + 1).copy(counted = false))

        val bar = weeks(sessions, recordStart = monday - 365).bars.last()

        assertThat(bar.plannedMinutes).isEqualTo(0)
        assertThat(bar.otherMinutes).isEqualTo(40)
    }

    @Test
    fun `km is the week's km of every step, steps the week's steps a day over the days that record them`() {
        val days = listOf(
            HealthDay(monday - 7, steps = 8_000, distanceM = 6_000),
            HealthDay(monday - 6, steps = 10_000, distanceM = 7_000),
            HealthDay(monday - 5, distanceM = 1_000),
        )

        val bar = weeks(emptyList(), days = days, recordStart = monday - 365).bars[10]

        assertThat(bar.distanceM).isEqualTo(14_000)
        assertThat(bar.stepsADay).isEqualTo(9_000)
        assertThat(bar.value(SpanMeasure.KM)).isEqualTo(14.0)
        assertThat(bar.value(SpanMeasure.STEPS)).isEqualTo(9_000.0)
        // A recorded week with no distance and no steps is blank in Km and Steps, and 0 in Minutes.
        val empty = weeks(emptyList(), days = days, recordStart = monday - 365).bars[9]
        assertThat(empty.value(SpanMeasure.KM)).isNull()
        assertThat(empty.value(SpanMeasure.STEPS)).isNull()
        assertThat(empty.value(SpanMeasure.MINUTES)).isEqualTo(0.0)
    }

    @Test
    fun `a month takes each session by its own day, with its week's practice`() {
        // Monday 31 August's week straddles the months; its planned walk is on Monday, its own swim on Wednesday.
        val sessions = listOf(session(1, monday), session(2, monday + 2, WorkoutKind.SWIM, minutes = 30))
        val chart = MovementSpans.months(TEST_EPOCH_DAY, TEST_EPOCH_DAY, sessions, emptyList(), listOf(kept(plan(walk30), start = monday)), recordStart = 0)

        assertThat(chart.bars[10].plannedMinutes).isEqualTo(40)
        assertThat(chart.bars[10].otherMinutes).isEqualTo(0)
        assertThat(chart.bars[11].plannedMinutes).isEqualTo(0)
        assertThat(chart.bars[11].otherMinutes).isEqualTo(30)
    }

    // D136: before the record, and the averages.

    @Test
    fun `a week before the record is blank, not zero, and out of the average`() {
        // The span ends last week, and the record starts on the Wednesday of the week before: those two weeks are
        // recorded, the ten before them are not.
        val sessions = listOf(session(1, monday - 14 + 2, minutes = 60), session(2, monday - 7 + 1, minutes = 20))

        val chart = MovementSpans.weeks(monday - 7, TEST_EPOCH_DAY, sessions, emptyList(), emptyList(), recordStart = monday - 12)

        assertThat(chart.bars.take(10).none { it.recorded }).isTrue()
        assertThat(chart.bars.take(10).map { it.value(SpanMeasure.MINUTES) }.distinct()).containsExactly(null)
        assertThat(chart.bars.drop(10).all { it.recorded }).isTrue()
        // 60 and 20: the average of the two recorded weeks.
        assertThat(chart.average(SpanMeasure.MINUTES)).isEqualTo(40.0)
        assertThat(chart.nothingRecorded).isFalse()
    }

    @Test
    fun `the week holding today is drawn but left out of the twelve-week average`() {
        // 60 and 20 in the two weeks before; 200 so far this week.
        val sessions = listOf(session(1, monday - 14, minutes = 60), session(2, monday - 7, minutes = 20), session(3, monday, minutes = 200))
        val days = listOf(HealthDay(monday - 14, steps = 6_000), HealthDay(monday - 7, steps = 8_000), HealthDay(monday, steps = 20_000))

        val chart = weeks(sessions, days = days, recordStart = monday - 14)

        assertThat(chart.bars.last().value(SpanMeasure.MINUTES)).isEqualTo(200.0)
        assertThat(chart.averaged(SpanMeasure.MINUTES)).containsExactly(60.0, 20.0).inOrder()
        assertThat(chart.average(SpanMeasure.MINUTES)).isEqualTo(40.0)
        assertThat(chart.average(SpanMeasure.STEPS)).isEqualTo(7_000.0)
        assertThat(chart.soFar(SpanMeasure.MINUTES)).isNull()
    }

    @Test
    fun `twelve weeks before today's week average all twelve`() {
        // The span ends the week before this one: its last week is complete, and counted.
        val sessions = listOf(session(1, monday - 14, minutes = 60), session(2, monday - 7, minutes = 20))

        val chart = MovementSpans.weeks(monday - 7, TEST_EPOCH_DAY, sessions, emptyList(), emptyList(), recordStart = monday - 14)

        assertThat(chart.bars.none { it.current }).isTrue()
        assertThat(chart.averaged(SpanMeasure.MINUTES)).containsExactly(60.0, 20.0).inOrder()
    }

    @Test
    fun `with no complete week recorded twelve weeks says the current week so far`() {
        val sessions = listOf(session(1, monday, minutes = 85))
        val days = listOf(HealthDay(monday, steps = 9_000))

        val chart = weeks(sessions, days = days, recordStart = monday)

        assertThat(chart.average(SpanMeasure.MINUTES)).isNull()
        assertThat(chart.soFar(SpanMeasure.MINUTES)).isEqualTo(85.0)
        assertThat(chart.soFar(SpanMeasure.STEPS)).isEqualTo(9_000.0)
        assertThat(chart.soFar(SpanMeasure.KM)).isNull()
    }

    @Test
    fun `a span wholly before the record says so`() {
        assertThat(weeks(emptyList(), recordStart = TEST_EPOCH_DAY + 1).nothingRecorded).isTrue()
        assertThat(weeks(emptyList(), recordStart = null).nothingRecorded).isTrue()
        assertThat(weeks(emptyList(), recordStart = null).average(SpanMeasure.MINUTES)).isNull()
    }

    @Test
    fun `the year's average is over the complete months recorded`() {
        val july = LocalDate.of(2026, 7, 6).toEpochDay()
        val august = LocalDate.of(2026, 8, 3).toEpochDay()
        val sessions = listOf(session(1, july, minutes = 300), session(2, august, minutes = 500), session(3, TEST_EPOCH_DAY, minutes = 20))

        val chart = MovementSpans.months(TEST_EPOCH_DAY, TEST_EPOCH_DAY, sessions, emptyList(), emptyList(), recordStart = LocalDate.of(2026, 7, 1).toEpochDay())

        assertThat(chart.average(SpanMeasure.MINUTES)).isEqualTo(400.0)
        assertThat(chart.averaged(SpanMeasure.MINUTES)).hasSize(2)
        assertThat(chart.soFar(SpanMeasure.MINUTES)).isNull()
    }

    @Test
    fun `with no complete month recorded the year says the current month so far`() {
        val chart = MovementSpans.months(
            TEST_EPOCH_DAY, TEST_EPOCH_DAY, listOf(session(1, TEST_EPOCH_DAY, minutes = 85)), emptyList(), emptyList(),
            recordStart = LocalDate.of(2026, 9, 1).toEpochDay(),
        )

        assertThat(chart.average(SpanMeasure.MINUTES)).isNull()
        assertThat(chart.soFar(SpanMeasure.MINUTES)).isEqualTo(85.0)
        assertThat(chart.bars.last().current).isTrue()
    }

    // D137: the comparisons.

    @Test
    fun `within a tenth either way is about, inclusive at the edge`() {
        assertThat(MovementSpans.against(154.0, 140.0)).isEqualTo(Against.ABOUT)
        assertThat(MovementSpans.against(126.0, 140.0)).isEqualTo(Against.ABOUT)
        assertThat(MovementSpans.against(154.5, 140.0)).isEqualTo(Against.ABOVE)
        assertThat(MovementSpans.against(125.5, 140.0)).isEqualTo(Against.BELOW)
        assertThat(MovementSpans.against(0.0, 0.0)).isEqualTo(Against.ABOUT)
        assertThat(MovementSpans.against(10.0, 0.0)).isEqualTo(Against.ABOVE)
    }

    @Test
    fun `the usual week is the recorded weeks of the four before, needing two`() {
        val sessions = listOf(session(1, monday - 7, minutes = 100), session(2, monday - 14, minutes = 60), session(3, monday - 21, minutes = 20))

        assertThat(MovementSpans.usualWeek(monday, TEST_EPOCH_DAY, sessions, recordStart = monday - 365)).isEqualTo(45.0)
        assertThat(MovementSpans.usualWeek(monday, TEST_EPOCH_DAY, sessions, recordStart = monday - 14)).isEqualTo(80.0)
        assertThat(MovementSpans.usualWeek(monday, TEST_EPOCH_DAY, sessions, recordStart = monday - 7)).isNull()
        assertThat(MovementSpans.usualWeek(monday, TEST_EPOCH_DAY, sessions, recordStart = null)).isNull()
    }

    @Test
    fun `the twelve-week trend needs all eight weeks recorded`() {
        // The four before at 120 minutes, the last four complete weeks at 160, and 10 so far this week.
        val sessions = (1..8).map { back -> session(back.toLong(), monday - 7L * back, minutes = if (back <= 4) 160 else 120) } +
            session(9, monday, minutes = 10)

        val trend = weeks(sessions, recordStart = monday - 7L * 8).trend(SpanMeasure.MINUTES)!!
        assertThat(trend.last).isEqualTo(160.0)
        assertThat(trend.before).isEqualTo(120.0)
        assertThat(trend.against).isEqualTo(Against.ABOVE)

        assertThat(weeks(sessions, recordStart = monday - 7L * 7).trend(SpanMeasure.MINUTES)).isNull()
        // Km needs a figure in all eight, too.
        assertThat(weeks(sessions, recordStart = monday - 365).trend(SpanMeasure.KM)).isNull()
    }

    @Test
    fun `twelve weeks before today's week compare their own last eight`() {
        // The span ends the week before this one: its last four are its own four newest weeks.
        val sessions = (1..8).map { back -> session(back.toLong(), monday - 7L * back, minutes = if (back <= 4) 90 else 120) }

        val trend = MovementSpans.weeks(monday - 7, TEST_EPOCH_DAY, sessions, emptyList(), emptyList(), recordStart = monday - 7L * 8)
            .trend(SpanMeasure.MINUTES)!!
        assertThat(trend.last).isEqualTo(90.0)
        assertThat(trend.before).isEqualTo(120.0)
    }

    private fun weeks(
        workouts: List<Workout>,
        days: List<HealthDay> = emptyList(),
        plans: List<CountedPlan> = emptyList(),
        recordStart: Long?,
    ) = MovementSpans.weeks(TEST_EPOCH_DAY, TEST_EPOCH_DAY, workouts, days, plans, recordStart)

    private fun plan(vararg sessions: PlannedSession) =
        WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", sessions.toList()) }, "Invented reason.")

    private fun kept(plan: WeeksPlan, start: Long) =
        CountedPlan(Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", start, ProgrammeStatus.RUNNING), PlanCounting(1, 0, emptyMap()))

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 40, hour: Int = 7) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
