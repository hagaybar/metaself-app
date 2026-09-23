package com.metaself.app.ui.screen.weight

import com.metaself.app.ui.goal.GoalWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** One horizontal gridline: the whole kilo it sits on, and what its label says. */
data class GridLine(val kg: Int, val label: String)

/** One date under the plot, and where its label's left edge goes. */
data class DateTick(val label: String, val leftPx: Float)

/**
 * What a finger held on the chart is reading: the day, the trend on it, and what the scale said —
 * null when nobody weighed that day, which is the whole of how a sparse week reads as sparse.
 */
data class ChartDay(val epochDay: Long, val trendKg: Double, val weighedKg: Double?)

/**
 * The quiet grid's decisions (public issue #15), each one a pure function so that it can be tested
 * without a canvas: where the gridlines go, which dates fit along the bottom, how far the dots fade,
 * when the finger should feel a tick, and what the chart says.
 *
 * The drawing in [WeightChart] asks these and does no arithmetic of its own beyond placing what
 * they return.
 */
object ChartLayout {

    /**
     * The gridline steps on offer, in kilograms: round numbers a reader can count along without
     * doing sums.
     */
    private val GRID_STEPS_KG = listOf(1, 2, 5, 10, 20, 50)

    /**
     * The most gaps a grid may cut the axis into. A judgement, not a measurement: enough lines to
     * read a value off, few enough that the grid stays quiet behind the trend.
     */
    const val MAX_GRID_GAPS = 6

    /**
     * How wide a reading's dot is, in dp. The drawing takes its radius from here, so the fade below
     * and the dot on screen cannot disagree about when two dots touch.
     */
    const val DOT_DIAMETER_DP = 6f

    /** The faintest a dot is ever drawn: crowded readings fade, but a reading never disappears. */
    const val MIN_DOT_ALPHA = 0.15f

    /** Below a year, a month start is "Sep"; at a year or more it needs its year to be placed. */
    private const val DAYS_IN_YEAR = 365L

    /** The smallest step that cuts [spanKg] into at most [MAX_GRID_GAPS] gaps. */
    fun gridStepKg(spanKg: Double): Int =
        GRID_STEPS_KG.firstOrNull { step -> spanKg / step <= MAX_GRID_GAPS } ?: GRID_STEPS_KG.last()

    /**
     * Every whole multiple of the step between the two ends of the axis, heaviest first — the order
     * they are drawn top to bottom.
     *
     * Only the top one says "kg". Five labels each saying it would be the unit printed five times;
     * one says what they are all in.
     */
    fun gridLines(lightestKg: Double, heaviestKg: Double): List<GridLine> {
        val step = gridStepKg(heaviestKg - lightestKg)
        val top = floor(heaviestKg / step).toInt() * step
        val bottom = ceil(lightestKg / step).toInt() * step
        if (top < bottom) return emptyList()
        return (top downTo bottom step step).mapIndexed { index, kg ->
            GridLine(kg = kg, label = if (index == 0) "$kg kg" else "$kg")
        }
    }

    /**
     * How strongly each dot is drawn, from how many readings share each dp of width.
     *
     * Solid until the dots start to touch — one every [DOT_DIAMETER_DP] — and beyond that in
     * inverse proportion: where two dots stack, each is half as strong, so the stack reads about as
     * dark as one lone dot. That is what keeps a long history from drawing a band that swamps the
     * trend it was there to support.
     */
    fun dotAlpha(readingsPerDp: Float): Float {
        val touching = 1f / DOT_DIAMETER_DP
        if (readingsPerDp <= touching) return 1f
        return (touching / readingsPerDp).coerceAtLeast(MIN_DOT_ALPHA)
    }

    /**
     * The dates along the bottom: the first and the last always, and each month start between them
     * that fits without coming within [gapPx] of a label already placed.
     *
     * The ends are placed first and never dropped, because they are what say how much history the
     * chart covers; the first reads from the left edge of its label and the last from the right, so
     * neither hangs off the plot. A month start is centred on its day. One day shown is one date,
     * centred, as the plot draws it.
     */
    fun dateTicks(
        firstDay: Long,
        lastDay: Long,
        xOf: (Long) -> Float,
        widthOf: (String) -> Float,
        gapPx: Float,
    ): List<DateTick> {
        val spanDays = lastDay - firstDay
        if (spanDays <= 0L) {
            val label = ChartGeometry.date(firstDay, 0L)
            return listOf(DateTick(label = label, leftPx = xOf(firstDay) - widthOf(label) / 2f))
        }

        val placed = mutableListOf<Placed>()
        ChartGeometry.date(firstDay, spanDays).let { label ->
            placed += Placed(label, left = xOf(firstDay), width = widthOf(label))
        }
        ChartGeometry.date(lastDay, spanDays).let { label ->
            val width = widthOf(label)
            placed += Placed(label, left = xOf(lastDay) - width, width = width)
        }

        val pattern = if (spanDays >= DAYS_IN_YEAR) "MMM yyyy" else "MMM"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
        var month = LocalDate.ofEpochDay(firstDay).withDayOfMonth(1).plusMonths(1)
        while (month.toEpochDay() < lastDay) {
            val label = month.format(formatter)
            val width = widthOf(label)
            val candidate = Placed(label, left = xOf(month.toEpochDay()) - width / 2f, width = width)
            if (placed.none { it.touches(candidate, gapPx) }) placed += candidate
            month = month.plusMonths(1)
        }

        return placed.sortedBy { it.left }.map { DateTick(label = it.label, leftPx = it.left) }
    }

    private data class Placed(val label: String, val left: Float, val width: Float) {
        val right: Float get() = left + width

        fun touches(other: Placed, gapPx: Float): Boolean =
            left < other.right + gapPx && other.left < right + gapPx
    }

    /**
     * Whether a finger moving from [fromDay] to [toDay] reached a day with a reading — the moment
     * the hand feels a tick. [fromDay] is null for where the hold first lands.
     *
     * The day left behind does not count and the day arrived on does, in either direction, so each
     * reading ticks once as the finger passes it, staying on one does not tick again, and a quick
     * drag across several readings ticks once rather than buzzing.
     */
    fun crossesReading(fromDay: Long?, toDay: Long, readingDays: Set<Long>): Boolean {
        if (fromDay == null) return toDay in readingDays
        if (fromDay == toDay) return false
        val reached = if (toDay > fromDay) (fromDay + 1)..toDay else toDay until fromDay
        return readingDays.any { it in reached }
    }

    /**
     * "Thu 3 Sep · Trend 80.3 · Weighed 80.1" — the line above the chart while a finger holds it.
     *
     * "Weighed" appears only when a reading exists that day. The trend on a day nobody weighed is
     * read off the line as drawn, and it is labelled "Trend" either way: it is the smoothed line,
     * never what a scale said (D4).
     */
    fun readout(day: ChartDay, spanDays: Long): String {
        val pattern = if (spanDays >= DAYS_IN_YEAR) "d MMM yyyy" else "EEE d MMM"
        val date = LocalDate.ofEpochDay(day.epochDay)
            .format(DateTimeFormatter.ofPattern(pattern, Locale.US))
        return listOfNotNull(
            date,
            "Trend ${oneDecimal(day.trendKg)}",
            day.weighedKg?.let { "Weighed ${oneDecimal(it)}" },
        ).joinToString(" · ")
    }

    /**
     * What a screen reader says for the chart: the dates it covers, the trend at each end, and the
     * goal when it is drawn. The finger-scrub cannot be heard, so this is the reading it offers
     * instead. The trend "went from" one figure to another — a report, not a comment on direction.
     */
    fun summary(geometry: ChartGeometry): String {
        val first = geometry.shown.first().trendKg
        val last = geometry.shown.last().trendKg
        val end = geometry.endLabel
        val trend = if (end == null) {
            "Weight chart, ${geometry.startLabel}. The trend is ${oneDecimal(last)} kg."
        } else {
            "Weight chart, ${geometry.startLabel} to $end. " +
                "The trend went from ${oneDecimal(first)} kg to ${oneDecimal(last)} kg."
        }
        val goal = geometry.targetKg?.let { " The dashed line is the goal weight, ${GoalWording.kg(it)} kg." }
        return trend + goal.orEmpty()
    }

    private fun oneDecimal(kg: Double): String = String.format(Locale.US, "%.1f", kg)
}
