package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.trainer.MovementSpan
import com.metaself.app.domain.trainer.SpanBar
import com.metaself.app.domain.trainer.SpanChart
import com.metaself.app.domain.trainer.SpanMeasure
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D135–D137: what the spans say. Today is Thursday 3 September 2026 ([TEST_EPOCH_DAY]); every figure is invented.
 */
class MovementSpanWordingTest {

    private val monday = TEST_EPOCH_DAY - 3

    @Test
    fun `the span line names the span's days, and Year its months`() {
        assertThat(MovementSpanWording.spanLine(weeks(List(12) { 100 }))).isEqualTo("15 JUN – 3 SEP")
        val earlier = SpanChart(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY, weekBars(List(12) { 100 }, lastMonday = LocalDate.of(2026, 1, 12).toEpochDay()))
        assertThat(MovementSpanWording.spanLine(earlier)).isEqualTo("27 OCT 2025 – 18 JAN")
        assertThat(MovementSpanWording.spanLine(year(List(12) { 300 }))).isEqualTo("OCT 2025 – SEP 2026")
    }

    @Test
    fun `the headline is the average with its unit`() {
        val chart = weeks(List(12) { 150 }, km = List(12) { 18_200 }, steps = List(12) { 8_900 })

        assertThat(MovementSpanWording.headline(chart, SpanMeasure.MINUTES)).isEqualTo("150 min a week")
        assertThat(MovementSpanWording.headline(chart, SpanMeasure.KM)).isEqualTo("18.2 km a week")
        assertThat(MovementSpanWording.headline(chart, SpanMeasure.STEPS)).isEqualTo("8,900 steps a day")
        assertThat(MovementSpanWording.headline(weeks(List(12) { 150 }), SpanMeasure.KM)).isEqualTo("No km recorded")
        assertThat(MovementSpanWording.averageLabel(148.4, SpanMeasure.MINUTES)).isEqualTo("avg 148")
        assertThat(MovementSpanWording.averageLabel(18.24, SpanMeasure.KM)).isEqualTo("avg 18.2")
        assertThat(MovementSpanWording.averageLabel(8_900.0, SpanMeasure.STEPS)).isEqualTo("avg 8,900")
    }

    @Test
    fun `Year's headline is a month's average, or the current month so far`() {
        assertThat(MovementSpanWording.headline(year(List(11) { 600 } + 85), SpanMeasure.KM)).isEqualTo("600 min a month")
        val onlyThisMonth = year(List(11) { null } + 85)
        assertThat(MovementSpanWording.headline(onlyThisMonth, SpanMeasure.MINUTES)).isEqualTo("85 min so far")
        assertThat(MovementSpanWording.yearLine(onlyThisMonth)).isNull()
        assertThat(MovementSpanWording.yearLine(year(List(9) { null } + listOf(600, 640, 85)))).isEqualTo("Over the 2 months recorded.")
        assertThat(MovementSpanWording.yearLine(year(List(10) { null } + listOf(600, 85)))).isEqualTo("Over the one month recorded.")
    }

    @Test
    fun `a span wholly before the record has no headline`() {
        val chart = weeks(List(12) { null })

        assertThat(MovementSpanWording.headline(chart, SpanMeasure.MINUTES)).isNull()
        assertThat(MovementSpanWording.twelveWeeksLine(chart, SpanMeasure.MINUTES)).isNull()
    }

    @Test
    fun `the twelve-week line compares the last four weeks with the four before`() {
        val up = weeks(List(4) { 100 } + List(4) { 120 } + List(4) { 165 })
        assertThat(MovementSpanWording.twelveWeeksLine(up, SpanMeasure.MINUTES))
            .isEqualTo("The last 4 weeks averaged 165, up from 120 the 4 before.")

        val down = weeks(List(8) { 120 } + List(4) { 90 })
        assertThat(MovementSpanWording.twelveWeeksLine(down, SpanMeasure.MINUTES))
            .isEqualTo("The last 4 weeks averaged 90, down from 120 the 4 before.")

        // A tenth either way, at the edge, is about the same.
        val edge = weeks(List(8) { 120 } + List(4) { 132 })
        assertThat(MovementSpanWording.twelveWeeksLine(edge, SpanMeasure.MINUTES))
            .isEqualTo("The last 4 weeks averaged 132, about the same as 120 the 4 before.")
        val past = weeks(List(8) { 120 } + List(4) { 133 })
        assertThat(MovementSpanWording.twelveWeeksLine(past, SpanMeasure.MINUTES)).contains("up from 120")
    }

    @Test
    fun `km and steps say the same in their units`() {
        val chart = weeks(List(12) { 0 }, km = List(8) { 15_000 } + List(4) { 18_200 }, steps = List(8) { 7_100 } + List(4) { 8_900 })

        assertThat(MovementSpanWording.twelveWeeksLine(chart, SpanMeasure.KM))
            .isEqualTo("The last 4 weeks averaged 18.2 km, up from 15.0 the 4 before.")
        assertThat(MovementSpanWording.twelveWeeksLine(chart, SpanMeasure.STEPS))
            .isEqualTo("The last 4 weeks averaged 8,900 steps a day, up from 7,100 the 4 before.")
    }

    @Test
    fun `with too little record the line says how many weeks it is over`() {
        val sixWeeks = weeks(List(6) { null } + List(6) { 100 })

        assertThat(MovementSpanWording.twelveWeeksLine(sixWeeks, SpanMeasure.MINUTES)).isEqualTo("Over the 6 weeks recorded.")
        assertThat(MovementSpanWording.twelveWeeksLine(weeks(List(11) { null } + 100), SpanMeasure.MINUTES))
            .isEqualTo("Over the one week recorded.")
    }

    @Test
    fun `Week's line appends the usual week, and nothing under two weeks of record`() {
        val week = week(minutes = 156, sessions = 6, distanceM = 21_400)

        assertThat(MovementSpanWording.weekLine(week, usual = 150.0)).isEqualTo("6 sessions · 21.4 km · about your usual week")
        assertThat(MovementSpanWording.weekLine(week, usual = 140.0)).isEqualTo("6 sessions · 21.4 km · above your usual week of 140 min")
        assertThat(MovementSpanWording.weekLine(week, usual = 180.0)).isEqualTo("6 sessions · 21.4 km · below your usual week of 180 min")
        assertThat(MovementSpanWording.weekLine(week, usual = null)).isEqualTo("6 sessions · 21.4 km")
        // Alone, it starts the sentence.
        assertThat(MovementSpanWording.weekLine(week(minutes = 0, sessions = 0, distanceM = null), usual = 0.0)).isEqualTo("About your usual week")
        assertThat(MovementSpanWording.weekLine(week(minutes = 0, sessions = 0, distanceM = null), usual = null)).isNull()
    }

    @Test
    fun `the usual week's edges are a tenth either way`() {
        assertThat(MovementSpanWording.usualPart(154, 140.0)).isEqualTo("about your usual week")
        assertThat(MovementSpanWording.usualPart(155, 140.0)).isEqualTo("above your usual week of 140 min")
        assertThat(MovementSpanWording.usualPart(126, 140.0)).isEqualTo("about your usual week")
        assertThat(MovementSpanWording.usualPart(125, 140.0)).isEqualTo("below your usual week of 140 min")
    }

    @Test
    fun `bars are labelled every other week from the newest, and by month initial`() {
        val labels = MovementSpanWording.barLabels(weeks(List(12) { 100 }))
        assertThat(labels).containsExactly("", "22", "", "6", "", "20", "", "3", "", "17", "", "31").inOrder()
        assertThat(MovementSpanWording.barLabels(year(List(12) { 100 }))).containsExactly("O", "N", "D", "J", "F", "M", "A", "M", "J", "J", "A", "S").inOrder()
    }

    @Test
    fun `the legend and a bar's description`() {
        val chart = year(List(11) { 600 } + 85)
        assertThat(MovementSpanWording.legend(chart, SpanMeasure.MINUTES)).containsExactly("Planned", "Your own", "Average", "September so far").inOrder()
        assertThat(MovementSpanWording.legend(weeks(List(12) { 100 }, km = List(12) { 1_000 }), SpanMeasure.KM)).containsExactly("Average")
        assertThat(MovementSpanWording.barDescription(chart, chart.bars.last(), SpanMeasure.MINUTES)).isEqualTo("September so far: 85 min")
        val twelve = weeks(List(11) { null } + 100)
        assertThat(MovementSpanWording.barDescription(twelve, twelve.bars.first(), SpanMeasure.MINUTES)).isEqualTo("Week of 15 Jun: before the record")
        assertThat(MovementSpanWording.barDescription(twelve, twelve.bars.last(), SpanMeasure.MINUTES)).isEqualTo("Week of 31 Aug: 100 min")
    }

    /** Twelve weeks ending this week; a null minute is a week before the record. */
    private fun weeks(minutes: List<Int?>, km: List<Int?> = List(12) { null }, steps: List<Int?> = List(12) { null }) =
        SpanChart(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY, weekBars(minutes, monday, km, steps))

    private fun weekBars(minutes: List<Int?>, lastMonday: Long, km: List<Int?> = List(12) { null }, steps: List<Int?> = List(12) { null }) =
        minutes.mapIndexed { index, value ->
            val first = lastMonday - 7L * (11 - index)
            SpanBar(first, first + 6, TEST_EPOCH_DAY in first..first + 6, value != null, 0, value ?: 0, km[index], steps[index])
        }

    /** Twelve months ending this month; a null is a month before the record. */
    private fun year(minutes: List<Int?>): SpanChart {
        val bars = minutes.mapIndexed { index, value ->
            val month = java.time.YearMonth.of(2026, 9).minusMonths(11L - index)
            val first = month.atDay(1).toEpochDay()
            SpanBar(first, month.atEndOfMonth().toEpochDay(), TEST_EPOCH_DAY in first..month.atEndOfMonth().toEpochDay(), value != null, value?.div(2) ?: 0, value?.let { it - it / 2 } ?: 0, null, null)
        }
        return SpanChart(MovementSpan.YEAR, TEST_EPOCH_DAY, bars)
    }

    private fun week(minutes: Int, sessions: Int, distanceM: Int?) = MovementWeek(
        monday = monday, thisMonday = monday, today = TEST_EPOCH_DAY, distanceM = distanceM, averageActiveKcal = null,
        sessions = sessions, minutes = minutes, days = emptyList(), previousWeeksM = List(4) { null },
    )
}
