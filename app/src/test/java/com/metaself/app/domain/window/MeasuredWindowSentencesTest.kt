package com.metaself.app.domain.window

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.window.WindowWording
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Every situation the day screen can be in under a ratio, and exactly what it says in each.
 *
 * Written after a defect in the live line, which counted the hours of a fast as hours of eating
 * and so reported a kept fast as a broken window: a table of all the possibilities, each one tested. The table lives here
 * as data, one row per situation, and each row is its own test in the report.
 *
 * **What a row asserts is the WHOLE of what is said, not one sentence.** [said] asks every sentence
 * the day screen draws about a ratio, in the order it draws them, and the row lists the complete
 * answer. So a row catches a sentence that should have stayed quiet as surely as one that is wrong —
 * which is the shape of the defect this was written after: a false sentence speaking where the true
 * one should have. And it pins design §3.0a's rule, one sentence about one open stretch, never two
 * and never none, row by row rather than as a claim in a comment.
 *
 * Out of the table, because they are decided before any sentence is asked: whether a rule governs
 * the day at all, and whether a stretch began before the rule did (`WindowRulesTest`); whether the
 * read reached a stretch's real beginning (`WindowRulesTest`, `DayViewModelTest`); meals that
 * cannot be timed (`WindowWordingTest`); and the kept-of-judged tally beside the mark, which is
 * counted from the record rather than said about a stretch (`DayViewModelTest`).
 *
 * **Rewritten for D32**, which set out what today should say instead: a time that can be acted
 * on — when the fast allows the next meal, when the window wants the last one — each said
 * as a condition, because the app cannot know a meal is the last until the fast after it completes.
 * The today rows below are D32's five situations, with the greeting and the tomorrow/yesterday
 * wording at their edges. The past-day rows are unchanged by it.
 *
 * All rows use one ratio, 14/10, in one fixed zone.
 */
class MeasuredWindowSentencesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    fun `what the day says`(row: Row) {
        assertThat(said(row)).containsExactlyElementsIn(row.expected).inOrder()
    }

    /**
     * One situation.
     *
     * [beganOnScreen] are the stretches the day being looked at BEGAN — what its verdict holds.
     * [beganEarlier] are stretches that began before it, which matter only to today: the stretch open
     * right now may have begun on an earlier day, and the live line is about that stretch whichever
     * day it began on (design §3.0a).
     */
    data class Row(
        val name: String,
        val isToday: Boolean,
        val now: Long,
        val beganOnScreen: List<EatingStretch> = emptyList(),
        val beganEarlier: List<EatingStretch> = emptyList(),
        val expected: List<String>,
    ) {
        override fun toString(): String = name
    }

    /**
     * Everything the day screen says about a ratio, in the order it draws it.
     *
     * Mirrors `DayScreen`: today's one sentence about the latest stretch (D32), then, per stretch the
     * day began, the span, the still-open line and the one-input line. The latest stretch is found
     * as the view model finds it — the latest there is, open or closed, because a closed one is
     * what "your 14 hours were up" is said about.
     */
    private fun said(row: Row): List<String> {
        val verdicts = row.beganOnScreen.map { it.at(window, row.now) }
        val latest = (row.beganEarlier + row.beganOnScreen).maxByOrNull { it.lastAtMillis }

        return buildList {
            WindowWording.ratioNow(latest, window, row.now, zone, row.isToday)?.let(::add)
            verdicts.forEach { verdict ->
                WindowWording.span(verdict, window)?.let(::add)
                WindowWording.stillOpen(verdict, window, row.isToday)?.let(::add)
                WindowWording.notJudged(verdict)?.let(::add)
            }
        }
    }

    companion object {

        private val window = MeasuredWindow(fastingHours = 14)
        private val zone: ZoneId = ZoneId.of("UTC+01:00")

        /** The day the rows are anchored to. "Today" is this day unless a row says otherwise. */
        private val D = LocalDate.of(2026, 9, 17).toEpochDay()

        private fun at(day: Long, hour: Int, minute: Int = 0): Long =
            LocalDateTime.of(LocalDate.ofEpochDay(day), LocalTime.of(hour, minute))
                .atZone(zone).toInstant().toEpochMilli()

        /** A stretch from one moment to another, on the day it began. */
        private fun eating(
            fromDay: Long,
            fromHour: Int,
            fromMinute: Int = 0,
            toDay: Long = fromDay,
            toHour: Int = fromHour,
            toMinute: Int = fromMinute,
            inputs: Int = 2,
        ) = EatingStretch(
            startedAtMillis = at(fromDay, fromHour, fromMinute),
            lastAtMillis = at(toDay, toHour, toMinute),
            inputs = inputs,
            startedOnEpochDay = fromDay,
        )

        private const val MORNING = "Good morning. "

        private fun lastMealBy(time: String) = "If you're keeping your window, last meal by $time."

        private fun nextMealFrom(time: String) = "If you're keeping your fast, next meal from $time."

        private fun ateOverThenNext(hours: Int, minutes: Int, time: String) =
            "You've eaten for ${hours}h ${minutes}m — next meal from $time if you're keeping your fast."

        private fun fastWasUp(moment: String) = "Your 14 hours were up $moment — eat when you like."

        private const val STILL_OPEN = "Still open — nothing is judged until you have fasted 14 hours."
        private const val ONE_INPUT =
            "Only one thing was logged, so this stretch is not judged either way."

        private fun ateOver(hours: Int, minutes: Int) =
            "You ate over ${hours}h ${minutes}m; your ratio allows 10h."

        @JvmStatic
        fun table(): List<Row> = listOf(

            // --- TODAY on screen: D32's five situations ---------------------------------------------

            Row(
                name = "T1 today: nothing logged under the ratio — says nothing",
                isToday = true,
                now = at(D, 12),
                expected = emptyList(),
            ),
            Row(
                name = "T2 today, afternoon: one input — last meal by the closing time",
                isToday = true,
                now = at(D, 12),
                beganOnScreen = listOf(eating(D, 9, inputs = 1)),
                expected = listOf(lastMealBy("19:00")),
            ),
            Row(
                name = "T3 today, morning: one input — good morning, last meal by",
                isToday = true,
                now = at(D, 8),
                beganOnScreen = listOf(eating(D, 7, inputs = 1)),
                expected = listOf(MORNING + lastMealBy("17:00")),
            ),
            Row(
                name = "T4 today: eating under way, a minute before the closing time — last meal by",
                isToday = true,
                now = at(D, 18, 59),
                beganOnScreen = listOf(eating(D, 9, toHour = 15)),
                expected = listOf(lastMealBy("19:00")),
            ),
            Row(
                name = "T5 today: at the closing time, stopped inside the hours — next meal from",
                isToday = true,
                now = at(D, 19),
                beganOnScreen = listOf(eating(D, 9, toHour = 15)),
                expected = listOf(nextMealFrom("05:00 tomorrow")),
            ),
            Row(
                name = "T6 today: stopped inside the hours, evening — next meal from, tomorrow",
                isToday = true,
                now = at(D, 22),
                beganOnScreen = listOf(eating(D, 9, toHour = 18)),
                expected = listOf(nextMealFrom("08:00 tomorrow")),
            ),
            Row(
                name = "T7 today: stopped at exactly the hours allowed — inside, next meal from",
                isToday = true,
                now = at(D, 22),
                beganOnScreen = listOf(eating(D, 9, toHour = 19)),
                expected = listOf(nextMealFrom("09:00 tomorrow")),
            ),
            Row(
                name = "T8 today: one minute over — how long, to the minute, and next meal from",
                isToday = true,
                now = at(D, 19, 5),
                beganOnScreen = listOf(eating(D, 9, toHour = 19, toMinute = 1)),
                expected = listOf(ateOverThenNext(10, 1, "09:01 tomorrow")),
            ),
            Row(
                name = "T9 today: over and still eating — how long he ate, and next meal from",
                isToday = true,
                now = at(D, 22, 30),
                beganOnScreen = listOf(eating(D, 9, toHour = 22, inputs = 4)),
                expected = listOf(ateOverThenNext(13, 0, "12:00 tomorrow")),
            ),
            Row(
                name = "T10 today: over, then stopped — how long he ATE, not how long since",
                isToday = true,
                now = at(D, 23, 30),
                beganOnScreen = listOf(eating(D, 9, toHour = 21)),
                expected = listOf(ateOverThenNext(12, 0, "11:00 tomorrow")),
            ),
            Row(
                name = "T11 today: one input, past its closing time — next meal from, later today",
                isToday = true,
                now = at(D, 20),
                beganOnScreen = listOf(eating(D, 9, inputs = 1)),
                expected = listOf(nextMealFrom("23:00")),
            ),
            Row(
                name = "T12 today, the morning after: yesterday 08:00-17:00, " +
                    "looked at 05:00 — good morning, next meal from 07:00 (was: eating for 21h)",
                isToday = true,
                now = at(D + 1, 5),
                beganEarlier = listOf(eating(D, 8, toHour = 17, inputs = 3)),
                expected = listOf(MORNING + nextMealFrom("07:00")),
            ),
            Row(
                name = "T13 today: never fasting, eating since 06:00 yesterday — how long, next meal",
                isToday = true,
                now = at(D + 1, 20, 30),
                beganEarlier = listOf(eating(D, 6, toDay = D + 1, toHour = 20, inputs = 5)),
                expected = listOf(ateOverThenNext(38, 0, "10:00 tomorrow")),
            ),
            Row(
                name = "T14 today: the fast completed this morning, nothing since — it was up at",
                isToday = true,
                now = at(D + 1, 10),
                beganEarlier = listOf(eating(D, 9, toHour = 19)),
                expected = listOf(MORNING + fastWasUp("at 09:00")),
            ),
            Row(
                name = "T15 today: the fast completed yesterday, nothing since — it was up yesterday",
                isToday = true,
                now = at(D + 2, 13),
                beganEarlier = listOf(eating(D, 8, toHour = 16)),
                expected = listOf(fastWasUp("yesterday at 06:00")),
            ),
            Row(
                name = "T16 today: the fast completed days ago, nothing since — it was up on the date",
                isToday = true,
                now = at(D + 3, 13),
                beganEarlier = listOf(eating(D, 8, toHour = 16)),
                expected = listOf(fastWasUp("on 18 Sep at 06:00")),
            ),
            Row(
                name = "T17 today: first input after a completed fast — last meal by, from the new one",
                isToday = true,
                now = at(D + 1, 11),
                beganEarlier = listOf(eating(D, 9, toHour = 19)),
                beganOnScreen = listOf(eating(D + 1, 9, 30, inputs = 1)),
                expected = listOf(MORNING + lastMealBy("19:30")),
            ),
            Row(
                name = "T18 today: the closing time falls after midnight — says tomorrow",
                isToday = true,
                now = at(D, 21),
                beganOnScreen = listOf(eating(D, 20, 10, inputs = 1)),
                expected = listOf(lastMealBy("06:10 tomorrow")),
            ),
            Row(
                name = "T19 today: exactly noon — no longer morning, no greeting",
                isToday = true,
                now = at(D, 12),
                beganOnScreen = listOf(eating(D, 7, inputs = 1)),
                expected = listOf(lastMealBy("17:00")),
            ),
            Row(
                name = "T20 today: 11:59 — still morning, greeted",
                isToday = true,
                now = at(D, 11, 59),
                beganOnScreen = listOf(eating(D, 7, inputs = 1)),
                expected = listOf(MORNING + lastMealBy("17:00")),
            ),

            // --- A PAST DAY on screen ------------------------------------------------------------

            Row(
                name = "P1 past day: no stretch began on it — nothing",
                isToday = false,
                now = at(D + 1, 12),
                expected = emptyList(),
            ),
            Row(
                name = "P2 past day: closed and kept — nothing (the tally carries it)",
                isToday = false,
                now = at(D + 1, 12),
                beganOnScreen = listOf(eating(D, 9, toHour = 18)),
                expected = emptyList(),
            ),
            Row(
                name = "P3 past day: closed and over the hours — by how much",
                isToday = false,
                now = at(D + 1, 12),
                beganOnScreen = listOf(eating(D, 9, toHour = 20, toMinute = 30)),
                expected = listOf(ateOver(11, 30)),
            ),
            Row(
                name = "P4 past day: closed with one input — not judged",
                isToday = false,
                now = at(D + 1, 12),
                beganOnScreen = listOf(eating(D, 12, inputs = 1)),
                expected = listOf(ONE_INPUT),
            ),
            Row(
                name = "P5 past day, seen at 05:00 the next morning: " +
                    "stopped inside the hours — still open",
                isToday = false,
                now = at(D + 1, 5),
                beganOnScreen = listOf(eating(D, 8, toHour = 17, inputs = 3)),
                expected = listOf(STILL_OPEN),
            ),
            Row(
                name = "P6 past day: still open and over the hours — still open (no live line)",
                isToday = false,
                now = at(D + 1, 6),
                beganOnScreen = listOf(eating(D, 9, toHour = 21)),
                expected = listOf(STILL_OPEN),
            ),
            Row(
                name = "P7 past day: two stretches, one input then one over — one line each",
                isToday = false,
                now = at(D + 1, 17),
                beganOnScreen = listOf(
                    eating(D, 0, 30, inputs = 1),
                    eating(D, 15, toDay = D + 1, toHour = 2, inputs = 3),
                ),
                expected = listOf(ONE_INPUT, ateOver(11, 0)),
            ),
        )
    }
}
