package com.metaself.app.domain.window

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.window.WindowWording
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Every sentence the eating window says.
 *
 * Two kinds, two shapes of answer, and the file is in two halves to match. The FIXED half above is
 * a statement about a calendar day and is untouched by the stretch rule; the MEASURED half below
 * now speaks about eating stretches bounded by the fast rather than about days (design §3).
 */
class WindowWordingTest {

    private val window = EatingWindow(startHour = 6, endHour = 20, fromEpochDay = 20_000)

    private fun day(outside: Int, inside: Int = 2, untimed: Int = 0) =
        DayWindow(window, mealsInside = inside, mealsOutside = outside, mealsUntimed = untimed)

    /**
     * A kept day used to get a sentence, and it was long and unnecessary: the mark beside it
     * already says the window is being kept, in the width of a word.
     */
    @Test
    fun `a day kept says nothing at all`() {
        assertThat(WindowWording.outside(day(outside = 0), isToday = true)).isNull()
    }

    @Test
    fun `today points out a late meal`() {
        assertThat(WindowWording.outside(day(outside = 1), isToday = true))
            .isEqualTo("One meal outside your 06:00 to 20:00 window.")
    }

    /** D14: the past states the fact and does not reproach him for it. */
    @Test
    fun `a past day states it flatly`() {
        val text = WindowWording.outside(day(outside = 2), isToday = false)!!

        assertThat(text).isEqualTo("Your window was 06:00 to 20:00; 2 meals fell outside it.")
        assertThat(text.lowercase()).doesNotContain("should")
        assertThat(text.lowercase()).doesNotContain("failed")
    }

    @Test
    fun `a day no window governed says nothing at all`() {
        assertThat(WindowWording.outside(null, isToday = true)).isNull()
    }

    @Test
    fun `a day with nothing logged says nothing either`() {
        assertThat(WindowWording.outside(day(outside = 0, inside = 0), isToday = true)).isNull()
    }

    /** He is entitled to know a day was not fully judged rather than assume it passed. */
    @Test
    fun `meals that cannot be timed are admitted to`() {
        assertThat(WindowWording.untimed(day(outside = 0, untimed = 1)))
            .contains("written down on another day")
        assertThat(WindowWording.untimed(day(outside = 0, untimed = 0))).isNull()
    }

    @Test
    fun `the count comes from the record`() {
        assertThat(WindowWording.kept(kept = 10, judged = 14)).isEqualTo("Window kept on 10 of 14 days")
        assertThat(WindowWording.kept(kept = 0, judged = 0)).isNull()
    }

    /** The one firm requirement, said where the window is set. */
    @Test
    fun `setting one says plainly that it does not reach backwards`() {
        assertThat(WindowWording.FROM_TODAY).contains("from today onwards")
        assertThat(WindowWording.FROM_TODAY).contains("left exactly as they are")
    }

    // --- the measured window, which now judges stretches (design §3) ---------------------------

    /** Fasting first, the way the ratio is written everywhere else in the world. */
    @Test
    fun `a ratio is written fasting first`() {
        assertThat(WindowWording.ratio(MeasuredWindow(fastingHours = 16))).isEqualTo("16/8")
        assertThat(WindowWording.ratio(MeasuredWindow(fastingHours = 12))).isEqualTo("12/12")
    }

    /** The slash never stands alone: the meaning is said, not left to be inferred. */
    @Test
    fun `and is also said in words`() {
        assertThat(WindowWording.inWords(MeasuredWindow(fastingHours = 16)))
            .isEqualTo("16 hours fasting, 8 hours eating")
        assertThat(WindowWording.inWords(MeasuredWindow(fastingHours = 20)))
            .isEqualTo("20 hours fasting, 4 hours eating")
    }

    /** Fourteen hours fasting, ten eating — the ratio is written fasting first. */
    private val fourteenTen = MeasuredWindow(fastingHours = 14)

    /** Fixed, so no sentence here changes its answer with the machine it runs on. */
    private val zone: ZoneId = ZoneId.of("UTC+01:00")

    private val day = LocalDate.of(2026, 9, 5).toEpochDay()

    private fun at(epochDay: Long, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * One stretch, and what [nowMillis] makes of it.
     *
     * Built through `EatingStretch.at` rather than by filling a [StretchVerdict] in by hand, so
     * every sentence below is being shown a verdict the domain can actually produce. `at` is
     * internal and the unit tests are the friend path CLAUDE.md describes.
     */
    private fun verdict(
        fromHour: Int,
        fromMinute: Int = 0,
        toHour: Int = fromHour,
        toMinute: Int = fromMinute,
        onDay: Long = day,
        endsOnDay: Long = onDay,
        inputs: Int = 2,
        nowMillis: Long,
        window: MeasuredWindow = fourteenTen,
    ): StretchVerdict = EatingStretch(
        startedAtMillis = at(onDay, fromHour, fromMinute),
        lastAtMillis = at(endsOnDay, toHour, toMinute),
        inputs = inputs,
        startedOnEpochDay = onDay,
    ).at(window, nowMillis)





    /**
     * A broken stretch states the span and what the ratio allows, flatly, no reproach (D14).
     *
     * The design's own sentence (§3). 09:30 to 21:10 is eleven hours and forty minutes against a
     * 14/10, which allows ten.
     */
    @Test
    fun `a broken stretch states the span and what the ratio allows`() {
        val now = at(day + 1, 12)
        val broken =
            verdict(
                fromHour = 9,
                fromMinute = 30,
                toHour = 21,
                toMinute = 10,
                inputs = 4,
                nowMillis = now,
            )

        val text = WindowWording.span(broken, fourteenTen)!!

        assertThat(text).isEqualTo("You ate over 11h 40m; your ratio allows 10h.")
        assertThat(text.lowercase()).doesNotContain("should")
        assertThat(text.lowercase()).doesNotContain("failed")
        assertThat(WindowWording.span(null, fourteenTen)).isNull()
    }




    /**
     * A closed stretch of one input is not judged, and says so rather than leaving a silent gap.
     *
     * Carried over from the day rule word for word except for the unit: a span of nothing would
     * keep any ratio, which would be a compliment for having logged almost nothing (design §2.1).
     * No `isToday` here, because an OPEN one-input stretch is the closing-time case above and this
     * one only ever speaks about a stretch the fast has already shut.
     */
    @Test
    fun `a closed stretch of one input says it was not judged`() {
        val now = at(day + 1, 12)
        val alone = verdict(fromHour = 10, inputs = 1, nowMillis = now)
        val judged = verdict(fromHour = 9, toHour = 17, inputs = 2, nowMillis = now)

        assertThat(WindowWording.notJudged(alone))
            .isEqualTo("Only one thing was logged, so this stretch is not judged either way.")
        assertThat(WindowWording.notJudged(judged)).isNull()
    }


    // --- the one thing said while a stretch is still running (design §3.0a) --------------------

    /** Sixteen hours fasting, eight eating — the ratio the review's own case is written in. */
    private val sixteenEight = MeasuredWindow(fastingHours = 16)

    /**
     * The stretch itself, with no verdict on it.
     *
     * [WindowWording.eatingSoFar] is shown the RECORD rather than a verdict, because it speaks
     * about the stretch open at this moment rather than about the day on screen — and the day on
     * screen is the only thing a verdict is ever built for. See its own KDoc.
     */
    private fun stretchOf(
        fromHour: Int,
        fromMinute: Int = 0,
        toHour: Int = fromHour,
        toMinute: Int = fromMinute,
        onDay: Long = day,
        endsOnDay: Long = onDay,
        inputs: Int = 2,
    ): EatingStretch = EatingStretch(
        startedAtMillis = at(onDay, fromHour, fromMinute),
        lastAtMillis = at(endsOnDay, toHour, toMinute),
        inputs = inputs,
        startedOnEpochDay = onDay,
    )












    /**
     * The same admission as before, from the stretch walk's own count.
     *
     * The number rides on the day's verdict exactly as it did, because the screen prints it beside
     * the window's line and the two have to have come from one reading of the same meals.
     */
    @Test
    fun `untimed meals are still reported for a measured day`() {
        val now = at(day, 12)

        assertThat(
            WindowWording.untimed(
                DayMeasured(
                    window = fourteenTen,
                    stretches = emptyList(),
                    nowMillis = now,
                    zone = zone,
                    mealsUntimed = 1,
                ),
            ),
        ).contains("written down on another day")
        assertThat(
            WindowWording.untimed(
                DayMeasured(
                    window = fourteenTen,
                    stretches = emptyList(),
                    nowMillis = now,
                    zone = zone,
                    mealsUntimed = 0,
                ),
            ),
        ).isNull()
    }

    // --- D32: today's one sentence, the tally in words ------------------------------------------
    //
    // Every situation today can be in, and the exact sentence for each, is the table in
    // `MeasuredWindowSentencesTest`. What is here is what a table row cannot say on its own: the
    // properties every one of those sentences must have.

    /**
     * Not one of today's sentences tells him off, and none counts down.
     *
     * No reproach (D14): a time to act on, or a fact, never a verdict he has not yet earned. No
     * countdown (D15, D27): every time is a clock time, never time remaining. Checked across all
     * five situations at once, morning and evening, so a new wording cannot slip a "should" in.
     */
    @Test
    fun `no sentence today reproaches him or counts anything down`() {
        val said = listOf(
            // before the closing time, morning
            WindowWording.ratioNow(stretchOf(fromHour = 7), fourteenTen, at(day, 8), zone, true),
            // past it, inside the hours
            WindowWording.ratioNow(
                stretchOf(fromHour = 9, toHour = 18), fourteenTen, at(day, 22), zone, true,
            ),
            // past it, over the hours
            WindowWording.ratioNow(
                stretchOf(fromHour = 9, toHour = 22), fourteenTen, at(day, 23), zone, true,
            ),
            // the fast complete
            WindowWording.ratioNow(
                stretchOf(fromHour = 9, toHour = 19), fourteenTen, at(day + 1, 10), zone, true,
            ),
        ).map { it!!.lowercase() }

        said.forEach { sentence ->
            listOf("should", "failed", "broke", "too long", "left", "remaining", "to go", "hurry")
                .forEach { word -> assertThat(sentence).doesNotContain(word) }
        }
    }

    /**
     * Every sentence about the future is a condition, because the app cannot know it (D32 §2).
     *
     * A meal is known to be the last one only once the fast after it completes. So "last meal by"
     * and "next meal from" are always said as "if you're keeping …", which is the only honest
     * framing. The sentence this replaced went wrong exactly where it stopped being
     * conditional.
     */
    @Test
    fun `every sentence about what comes next is said as a condition`() {
        val ahead = listOf(
            WindowWording.ratioNow(stretchOf(fromHour = 9), fourteenTen, at(day, 12), zone, true),
            WindowWording.ratioNow(
                stretchOf(fromHour = 9, toHour = 18), fourteenTen, at(day, 22), zone, true,
            ),
            WindowWording.ratioNow(
                stretchOf(fromHour = 9, toHour = 22), fourteenTen, at(day, 23), zone, true,
            ),
        )

        ahead.forEach { assertThat(it!!.lowercase()).contains("if you're keeping your") }
    }

    /**
     * Said on today and on no other day.
     *
     * A past day has nothing to act on. It keeps the sentences about the stretches it began — the
     * span, the still-open line, the one-input line — and those are unchanged by D32.
     */
    @Test
    fun `today's sentence is said on today only`() {
        val open = stretchOf(fromHour = 9, toHour = 18)

        assertThat(WindowWording.ratioNow(open, fourteenTen, at(day, 22), zone, isToday = false))
            .isNull()
        assertThat(WindowWording.ratioNow(null, fourteenTen, at(day, 22), zone, isToday = true))
            .isNull()
    }

    /**
     * The still-open line belongs to a past day, and never to today.
     *
     * On today the one sentence has already said the one thing there is to say about an open
     * stretch; a second explaining that nothing has been judged yet would be one too many.
     */
    @Test
    fun `the still-open line is a past day's, never today's`() {
        val now = at(day + 1, 4)
        val open = verdict(fromHour = 9, toHour = 18, nowMillis = now)

        assertThat(open.closed).isFalse()
        assertThat(WindowWording.stillOpen(open, fourteenTen, isToday = false))
            .isEqualTo("Still open — nothing is judged until you have fasted 14 hours.")
        assertThat(WindowWording.stillOpen(open, fourteenTen, isToday = true)).isNull()
    }

    /** A kept stretch, on the day it began, says nothing: the tally carries it. */
    @Test
    fun `a kept stretch says nothing on its day`() {
        val kept = verdict(fromHour = 9, fromMinute = 30, toHour = 17, inputs = 3, nowMillis = at(day + 1, 9))

        assertThat(kept.kept).isTrue()
        assertThat(WindowWording.span(kept, fourteenTen)).isNull()
        assertThat(WindowWording.stillOpen(kept, fourteenTen, isToday = false)).isNull()
        assertThat(WindowWording.notJudged(kept)).isNull()
    }

    /**
     * The tally is words, counted since the ratio was set (D32 §5).
     *
     * It was a bare fraction over the last fortnight, which did not say what it counted.
     */
    @Test
    fun `the tally says what it counts and since when, and is silent before anything is judged`() {
        val since = LocalDate.of(2026, 9, 3)

        assertThat(WindowWording.keptStretches(kept = 10, judged = 14, since = since))
            .isEqualTo("Kept 10 of 14 stretches since 3 Sep")
        assertThat(WindowWording.keptStretches(kept = 0, judged = 0, since = since)).isNull()
    }
}
