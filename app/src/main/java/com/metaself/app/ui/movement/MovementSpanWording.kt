package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.trainer.Against
import com.metaself.app.domain.trainer.MovementSpan
import com.metaself.app.domain.trainer.MovementSpans
import com.metaself.app.domain.trainer.SpanBar
import com.metaself.app.domain.trainer.SpanChart
import com.metaself.app.domain.trainer.SpanMeasure
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What Your movement says about its spans (D135–D137): the switch, the span line, the headlines, the line under them
 * — counted on the phone, never the AI's (D115) — and the chart's labels. No praise and no reproach (D14).
 */
object MovementSpanWording {

    /** [Locale.US], as [MovementWeekWording]: en-GB's CLDR data writes "Sept". */
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

    private const val SEP = " · "

    const val NOTHING_THIS_FAR = "Nothing recorded this far back."
    const val PLANNED = "Planned"
    const val YOUR_OWN = "Your own"
    const val AVERAGE = "Average"

    /** The three-way switch (D135). */
    fun spanName(span: MovementSpan): String = when (span) {
        MovementSpan.WEEK -> "Week"
        MovementSpan.TWELVE_WEEKS -> "12 weeks"
        MovementSpan.YEAR -> "Year"
    }

    /** The 12-week chips (D136). */
    fun measureName(measure: SpanMeasure): String = when (measure) {
        SpanMeasure.MINUTES -> "Minutes"
        SpanMeasure.KM -> "Km"
        SpanMeasure.STEPS -> "Steps"
    }

    /**
     * The span line (D135): "20 JUL – 11 OCT" — its first Monday to its last day shown, a date outside today's year
     * with its year — and "NOV 2025 – OCT 2026" for Year. In capitals, as the week's kicker.
     */
    fun spanLine(chart: SpanChart): String {
        val text = when (chart.span) {
            MovementSpan.YEAR -> LocalDate.ofEpochDay(chart.first).format(MONTH_YEAR) + " – " + LocalDate.ofEpochDay(chart.last).format(MONTH_YEAR)
            else -> date(chart.first, chart.today) + " – " + date(chart.last, chart.today)
        }
        return text.uppercase(Locale.US)
    }

    /**
     * The headline (D136): the average with its unit — "148 min a week", "18.2 km a week", "8,900 steps a day",
     * "640 min a month" — or, on Year with no complete month, "85 min so far" (all invented). Null for a span wholly
     * before the record, which says [NOTHING_THIS_FAR] instead; "No km recorded" when the bars hold no figure.
     */
    fun headline(chart: SpanChart, measure: SpanMeasure): String? {
        if (chart.nothingRecorded) return null
        val shown = if (chart.span == MovementSpan.YEAR) SpanMeasure.MINUTES else measure
        chart.soFar?.let { return "${number(it)} min so far" }
        val average = chart.average(shown) ?: return "No ${unitName(shown)} recorded"
        return when (shown) {
            SpanMeasure.MINUTES -> "${number(average.roundToInt())} min a " + if (chart.span == MovementSpan.YEAR) "month" else "week"
            SpanMeasure.KM -> "${km(average)} km a week"
            SpanMeasure.STEPS -> "${number(average.roundToInt())} steps a day"
        }
    }

    /** The dashed line's label (D136): "avg 148", "avg 18.2", "avg 8,900" (invented). */
    fun averageLabel(average: Double, measure: SpanMeasure): String = "avg " + figure(average, measure)

    /**
     * The line under the 12-week headline (D137): "The last 4 weeks averaged 165, up from 120 the 4 before." — "up
     * from", "down from", or "about the same as" within a tenth — needing all eight weeks with a figure; otherwise
     * "Over the N weeks recorded."; null with none. Km and Steps carry their unit on the first figure.
     */
    fun twelveWeeksLine(chart: SpanChart, measure: SpanMeasure): String? {
        val trend = chart.trend(measure)
        if (trend == null) {
            val weeks = chart.averaged(measure).size
            return if (weeks == 0) null else "Over the ${count(weeks, "week")} recorded."
        }
        val way = when (trend.against) {
            Against.ABOUT -> "about the same as"
            Against.ABOVE -> "up from"
            Against.BELOW -> "down from"
        }
        val n = MovementSpans.COMPARE_WEEKS
        return "The last $n weeks averaged ${figure(trend.last, measure)}${unitAfter(measure)}, $way ${figure(trend.before, measure)} the $n before."
    }

    /** The line under the Year headline (D137): "Over the N months recorded." over the months it averages; else null. */
    fun yearLine(chart: SpanChart): String? {
        val months = chart.averaged(SpanMeasure.MINUTES).size
        return if (months == 0) null else "Over the ${count(months, "month")} recorded."
    }

    /**
     * Week's line (D131, D137): D131's line, then "about your usual week", "above your usual week of 140 min" or
     * "below your usual week of 140 min" (invented) against [usual]; nothing appended without one.
     */
    fun weekLine(week: MovementWeek, usual: Double?): String? {
        val against = usual?.let { usualPart(week.minutes, it) }
        val parts = listOfNotNull(MovementWeekWording.underHeadline(week), against)
        if (parts.isEmpty()) return null
        return parts.joinToString(SEP).replaceFirstChar { it.uppercaseChar() }
    }

    /** D137: one week's minutes against its usual. */
    fun usualPart(minutes: Int, usual: Double): String = when (MovementSpans.against(minutes.toDouble(), usual)) {
        Against.ABOUT -> "about your usual week"
        Against.ABOVE -> "above your usual week of ${number(usual.roundToInt())} min"
        Against.BELOW -> "below your usual week of ${number(usual.roundToInt())} min"
    }

    /**
     * Under each bar (D136): a 12-week bar's Monday's day of the month on every other bar, counted back from the
     * newest so it is always labelled, "" on the rest; a month's initial.
     */
    fun barLabels(chart: SpanChart): List<String> = chart.bars.mapIndexed { index, bar ->
        val date = LocalDate.ofEpochDay(bar.first)
        when (chart.span) {
            MovementSpan.YEAR -> date.month.getDisplayName(TextStyle.NARROW, Locale.US)
            else -> if ((chart.bars.size - 1 - index) % 2 == 0) date.dayOfMonth.toString() else ""
        }
    }

    /** The legend (D136): Minutes bars are split; on Year, "October so far" (invented) for the month holding today. */
    fun legend(chart: SpanChart, measure: SpanMeasure): List<String> {
        val split = chart.span == MovementSpan.YEAR || measure == SpanMeasure.MINUTES
        val current = chart.bars.lastOrNull { it.current }?.takeIf { chart.span == MovementSpan.YEAR }
        return listOfNotNull(
            PLANNED.takeIf { split },
            YOUR_OWN.takeIf { split },
            AVERAGE.takeIf { chart.average(if (chart.span == MovementSpan.YEAR) SpanMeasure.MINUTES else measure) != null },
            current?.let { monthName(it.first) + " so far" },
        )
    }

    /** What a bar says to a screen reader, and what tapping it opens: "Week of 28 Sep: 186 min" (invented). */
    fun barDescription(chart: SpanChart, bar: SpanBar, measure: SpanMeasure): String {
        val name = when (chart.span) {
            MovementSpan.YEAR -> monthName(bar.first) + if (bar.current) " so far" else ""
            else -> "Week of " + date(bar.first, chart.today)
        }
        val shown = if (chart.span == MovementSpan.YEAR) SpanMeasure.MINUTES else measure
        val said = when {
            !bar.recorded -> "before the record"
            else -> bar.value(shown)?.let { figure(it, shown) + unitAfter(shown).ifEmpty { " min" } } ?: "nothing recorded"
        }
        return "$name: $said"
    }

    private fun monthName(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).month.getDisplayName(TextStyle.FULL, Locale.US)

    private fun date(epochDay: Long, today: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.format(if (date.year == LocalDate.ofEpochDay(today).year) DAY_MONTH else DAY_MONTH_YEAR)
    }

    private fun figure(value: Double, measure: SpanMeasure): String =
        if (measure == SpanMeasure.KM) km(value) else number(value.roundToInt())

    private fun unitAfter(measure: SpanMeasure): String = when (measure) {
        SpanMeasure.MINUTES -> ""
        SpanMeasure.KM -> " km"
        SpanMeasure.STEPS -> " steps a day"
    }

    private fun unitName(measure: SpanMeasure): String = when (measure) {
        SpanMeasure.MINUTES -> "minutes"
        SpanMeasure.KM -> "km"
        SpanMeasure.STEPS -> "steps"
    }

    private fun count(n: Int, noun: String): String = if (n == 1) "one $noun" else "$n ${noun}s"

    private fun km(value: Double): String = String.format(Locale.US, "%,.1f", value)

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)
}
