package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WeekSessions
import com.metaself.app.domain.movement.Workout
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** D135: the three spans of Your movement. */
enum class MovementSpan { WEEK, TWELVE_WEEKS, YEAR }

/** D136: what a 12-week bar counts — minutes moving, km of every step, or steps a day. */
enum class SpanMeasure { MINUTES, KM, STEPS }

/** D137: a figure against the one it is compared with — within a tenth either way is "about". */
enum class Against { ABOUT, ABOVE, BELOW }

/**
 * D136: one bar — a Monday-to-Sunday week, or a calendar month — from [first] to [last].
 *
 * @property current the bar holds today, and is counted to today.
 * @property recorded the bar ends on or after the record's first day; one before it is blank, never zero, and is
 *   left out of every average.
 * @property plannedMinutes its counted sessions (D131) that ticked a planned session in their week's practice (D110).
 * @property otherMinutes its other counted sessions.
 * @property distanceM the km of every step over its days ([MovementWeek.distanceOf]); null when no day has one.
 * @property stepsADay the mean over its days that record steps; null when none does.
 */
data class SpanBar(
    val first: Long,
    val last: Long,
    val current: Boolean,
    val recorded: Boolean,
    val plannedMinutes: Int,
    val otherMinutes: Int,
    val distanceM: Int?,
    val stepsADay: Int?,
) {
    val minutes: Int get() = plannedMinutes + otherMinutes

    /** The bar's height in [measure]'s unit (km for KM); null when blank — before the record, or nothing measured. */
    fun value(measure: SpanMeasure): Double? = if (!recorded) {
        null
    } else {
        when (measure) {
            SpanMeasure.MINUTES -> minutes.toDouble()
            SpanMeasure.KM -> distanceM?.let { it / 1000.0 }
            SpanMeasure.STEPS -> stepsADay?.toDouble()
        }
    }
}

/** D137: the last four weeks' average against the four before. */
data class SpanTrend(val last: Double, val before: Double) {
    val against: Against get() = MovementSpans.against(last, before)
}

/**
 * D136: twelve weeks or twelve months, oldest first, the last holding the day in view. [today] is the calendar's.
 */
data class SpanChart(val span: MovementSpan, val today: Long, val bars: List<SpanBar>) {
    init {
        require(span != MovementSpan.WEEK) { "the Week span is drawn as the strip" }
    }

    val first: Long get() = bars.first().first

    /** The last day shown: today when the last bar holds it. */
    val last: Long get() = minOf(bars.last().last, today)

    /** D136: a span wholly before the record says so, and draws nothing. */
    val nothingRecorded: Boolean get() = bars.none { it.recorded }

    /** The bars [average] is taken over: every recorded week; on Year, the complete recorded months. */
    fun averaged(measure: SpanMeasure): List<Double> = bars
        .filter { span == MovementSpan.TWELVE_WEEKS || !it.current }
        .mapNotNull { it.value(measure) }

    /** D136: the headline's average and the dashed line; null when no bar counts towards it. */
    fun average(measure: SpanMeasure): Double? = averaged(measure).takeIf { it.isNotEmpty() }?.average()

    /** D136: Year with no complete month recorded — the current month's minutes, said "so far"; else null. */
    val soFar: Int?
        get() = if (span != MovementSpan.YEAR || average(SpanMeasure.MINUTES) != null) null
        else bars.lastOrNull { it.current && it.recorded }?.minutes

    /**
     * D137: the last four weeks against the four before, when all eight have a figure in [measure]; null
     * otherwise, and on Year.
     */
    fun trend(measure: SpanMeasure): SpanTrend? {
        if (span != MovementSpan.TWELVE_WEEKS) return null
        val values = bars.takeLast(2 * MovementSpans.COMPARE_WEEKS).map { it.value(measure) }
        if (values.size < 2 * MovementSpans.COMPARE_WEEKS || values.any { it == null }) return null
        val before = values.take(MovementSpans.COMPARE_WEEKS).filterNotNull().average()
        val last = values.takeLast(MovementSpans.COMPARE_WEEKS).filterNotNull().average()
        return SpanTrend(last, before)
    }
}

/**
 * D135–D137: the spans' days, stepping between them, and their bars — every count the shared one (D131), "planned"
 * the week ledger's practice (D110). The day in view ("end") is any day of the span's last week or month. Pure.
 */
object MovementSpans {

    const val BARS = 12

    /** D137: the weeks either side of the 12-week comparison. */
    const val COMPARE_WEEKS = 4

    /** D137: the weeks before a week its usual is counted over, and the fewest of them recorded for one. */
    const val USUAL_WEEKS = 4
    const val USUAL_LEAST = 2

    /** The span's first day: its first week's Monday, or its first month's 1st. */
    fun firstDay(span: MovementSpan, end: Long): Long = when (span) {
        MovementSpan.WEEK -> MovementWeek.mondayOf(end)
        MovementSpan.TWELVE_WEEKS -> MovementWeek.mondayOf(end) - 7L * (BARS - 1)
        MovementSpan.YEAR -> YearMonth.from(LocalDate.ofEpochDay(end)).minusMonths(BARS - 1L).atDay(1).toEpochDay()
    }

    /** The span's last day: its last week's Sunday, or its last month's last day — after today on today's span. */
    fun lastDay(span: MovementSpan, end: Long): Long = when (span) {
        MovementSpan.WEEK, MovementSpan.TWELVE_WEEKS -> MovementWeek.mondayOf(end) + 6
        MovementSpan.YEAR -> YearMonth.from(LocalDate.ofEpochDay(end)).atEndOfMonth().toEpochDay()
    }

    /** The first day a span's sessions are read from: its first day's Monday, so a month's first week is whole. */
    fun readFrom(span: MovementSpan, end: Long): Long = MovementWeek.mondayOf(firstDay(span, end))

    /** D135: the span holds today; nothing later is shown. */
    fun holdsToday(span: MovementSpan, end: Long, today: Long): Boolean = lastDay(span, end) >= today

    /** D135, D136: ‹ is offered while the record goes back before the span's first day. */
    fun canGoEarlier(span: MovementSpan, end: Long, recordStart: Long?): Boolean =
        recordStart != null && recordStart < firstDay(span, end)

    /** D135: the day in view one span back. */
    fun earlier(span: MovementSpan, end: Long): Long = when (span) {
        MovementSpan.WEEK -> end - 7
        MovementSpan.TWELVE_WEEKS -> end - 7L * BARS
        MovementSpan.YEAR -> YearMonth.from(LocalDate.ofEpochDay(end)).minusMonths(BARS.toLong()).atEndOfMonth().toEpochDay()
    }

    /** D135: the day in view one span forward; null — today's span — once it reaches the span holding today. */
    fun later(span: MovementSpan, end: Long, today: Long): Long? {
        val next = when (span) {
            MovementSpan.WEEK -> end + 7
            MovementSpan.TWELVE_WEEKS -> end + 7L * BARS
            MovementSpan.YEAR -> YearMonth.from(LocalDate.ofEpochDay(end)).plusMonths(BARS.toLong()).atEndOfMonth().toEpochDay()
        }
        return next.takeUnless { holdsToday(span, it, today) }
    }

    /**
     * D136: the twelve weeks ending with [end]'s week.
     *
     * @param workouts the record's sessions from [readFrom] on; hidden and uncounted ones are left out here.
     * @param days the health record's days over the span.
     * @param plans every kept version of a weekly plan, for each week's practice.
     * @param recordStart the earliest day with a step count or a session; null when the record holds none.
     */
    fun weeks(end: Long, today: Long, workouts: List<Workout>, days: List<HealthDay>, plans: List<CountedPlan>, recordStart: Long?): SpanChart {
        require(MovementWeek.mondayOf(end) <= MovementWeek.mondayOf(today)) { "a span after today's: $end" }
        val first = firstDay(MovementSpan.TWELVE_WEEKS, end)
        val ticked = ticked(first, lastDay(MovementSpan.TWELVE_WEEKS, end), today, workouts, plans)
        val bars = (0 until BARS).map { index ->
            val monday = first + 7L * index
            bar(monday, monday + 6, today, workouts, days, ticked, recordStart)
        }
        return SpanChart(MovementSpan.TWELVE_WEEKS, today, bars)
    }

    /** D136: the twelve calendar months ending with [end]'s month; the arguments are [weeks]'. */
    fun months(end: Long, today: Long, workouts: List<Workout>, days: List<HealthDay>, plans: List<CountedPlan>, recordStart: Long?): SpanChart {
        require(YearMonth.from(LocalDate.ofEpochDay(end)) <= YearMonth.from(LocalDate.ofEpochDay(today))) { "a span after today's: $end" }
        val first = YearMonth.from(LocalDate.ofEpochDay(firstDay(MovementSpan.YEAR, end)))
        val ticked = ticked(first.atDay(1).toEpochDay(), lastDay(MovementSpan.YEAR, end), today, workouts, plans)
        val bars = (0 until BARS).map { index ->
            val month = first.plusMonths(index.toLong())
            bar(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay(), today, workouts, days, ticked, recordStart)
        }
        return SpanChart(MovementSpan.YEAR, today, bars)
    }

    /**
     * D137: the mean minutes of the [USUAL_WEEKS] weeks before [monday]'s that are recorded; null under
     * [USUAL_LEAST] of them. [workouts] must reach back that far.
     */
    fun usualWeek(monday: Long, today: Long, workouts: List<Workout>, recordStart: Long?): Double? {
        if (recordStart == null) return null
        val weeks = (1..USUAL_WEEKS).map { monday - 7L * it }.filter { it + 6 >= recordStart }
        if (weeks.size < USUAL_LEAST) return null
        return weeks.map { WeekSessions.minutes(WeekSessions.of(workouts, it, today)).toDouble() }.average()
    }

    /** D137: within a tenth of [usual] either way is [Against.ABOUT]; of a usual of 0, only 0 is. */
    fun against(value: Double, usual: Double): Against = when {
        abs(value - usual) * 10 <= usual -> Against.ABOUT
        value > usual -> Against.ABOVE
        else -> Against.BELOW
    }

    /** Every session that ticked a planned session in a week from [from]'s to [to]'s (or today's), as each week's practice counts. */
    private fun ticked(from: Long, to: Long, today: Long, workouts: List<Workout>, plans: List<CountedPlan>): Set<Long> {
        if (plans.isEmpty()) return emptySet()
        val lastMonday = MovementWeek.mondayOf(minOf(to, today))
        return generateSequence(MovementWeek.mondayOf(from)) { it + 7 }
            .takeWhile { it <= lastMonday }
            .flatMap { monday -> WeekLedgers.practiceOf(monday, today, workouts, plans)?.ticks.orEmpty().mapNotNull { it.by?.id } }
            .toSet()
    }

    private fun bar(
        first: Long,
        last: Long,
        today: Long,
        workouts: List<Workout>,
        days: List<HealthDay>,
        ticked: Set<Long>,
        recordStart: Long?,
    ): SpanBar {
        val to = minOf(last, today)
        val sessions = WeekSessions.between(workouts, first, to)
        val planned = sessions.filter { it.id in ticked }
        val steps = days.filter { it.epochDay in first..to }.mapNotNull { it.steps }
        return SpanBar(
            first = first,
            last = last,
            current = today in first..last,
            recorded = recordStart != null && to >= recordStart,
            plannedMinutes = WeekSessions.minutes(planned),
            otherMinutes = WeekSessions.minutes(sessions) - WeekSessions.minutes(planned),
            distanceM = MovementWeek.distanceOf(days, first, to),
            stepsADay = steps.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }
}
