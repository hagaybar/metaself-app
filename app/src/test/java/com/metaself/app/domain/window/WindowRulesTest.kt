package com.metaself.app.domain.window

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The one gate both kinds of window go through.
 *
 * A window never applies backwards. That is the whole design, and it matters more for the measured
 * kind than for the fixed one: everything needed to score last month
 * is already in the record, and nothing but the decision not to prevents it.
 *
 * The last two tests here assert the FIXED kind end to end through this shared entry point.
 * `EatingWindowTest` still guards `EatingWindows.judge` unedited, as the proof that the fixed
 * window's arithmetic is untouched — but once every caller reaches the window through `WindowRules`,
 * that file guards a composition the app no longer calls, and the path that actually runs would be
 * uncovered without these two.
 */
class WindowRulesTest {

    private val zone: ZoneId = ZoneId.of("UTC+01:00")
    private val today = LocalDate.of(2026, 9, 5).toEpochDay()

    private val untilEight = EatingWindow(startHour = 6, endHour = 20, fromEpochDay = today)
    private val sixteenEight = MeasuredWindow(fastingHours = 16)

    /**
     * Two days past anything logged here, so every stretch below has long since closed.
     *
     * The day rule this replaced needed no such thing: a calendar day was over at midnight and
     * could always be judged. A stretch is over when the fast completes, so "how did this day
     * stand?" cannot be asked without saying WHEN it is being asked — and every question in this
     * file is about a day that is safely over.
     */
    private val wellAfterwards: Long by lazy { instantAt(today + 2, 12) }

    /**
     * The days every test here read over, declared rather than assumed.
     *
     * A walk has to know where its list was cut or it will read the cut as the end of the eating
     * — see [MealRead]. This span is deliberately far wider at both ends than anything any test
     * below logs, and it reaches through the day every `now` here falls on, so nothing is cut at
     * either end and each test proves the rule it was actually written for. The cut itself is
     * proved by the two tests that declare a narrow read on purpose.
     */
    private val everythingRead = MealRead.OverDays(today - 40, today + 2)

    private fun mealAt(
        hour: Int,
        minute: Int = 0,
        onDay: Long = today,
        loggedOn: Long = onDay,
    ): Meal {
        val at = LocalDateTime.of(LocalDate.ofEpochDay(loggedOn), LocalTime.of(hour, minute))
        return Meal(
            epochDay = onDay,
            loggedAtMillis = at.atZone(zone).toInstant().toEpochMilli(),
            note = null,
            items = listOf(anItem()),
        )
    }

    /**
     * Null, not "not kept". A day the rule never governed has no verdict at all — the same nothing
     * the fixed window has always returned.
     */
    @Test
    fun `a measured window does not judge the day before it was set`() {
        val rules = listOf(WindowRule.Measured(sixteenEight, fromEpochDay = today))
        val yesterday = today - 1
        val meals = listOf(
            mealAt(8, onDay = yesterday),
            mealAt(23, onDay = yesterday),
        )

        assertThat(WindowRules.judge(rules, meals, everythingRead, yesterday, zone, wellAfterwards))
            .isNull()
    }

    /** The existing guarantee, re-asserted through the shared gate so a regression fails here too. */
    @Test
    fun `a fixed window still does not judge the day before it was set`() {
        val rules = listOf(WindowRule.Fixed(untilEight))
        val yesterday = today - 1

        assertThat(
            WindowRules.judge(
                rules,
                listOf(mealAt(23, onDay = yesterday)),
                everythingRead,
                yesterday,
                zone,
                wellAfterwards,
            ),
        ).isNull()
    }

    /** The boundary is inclusive: a rule set today governs today. */
    @Test
    fun `a rule set today judges today`() {
        val fixed = listOf(WindowRule.Fixed(untilEight))
        val measured = listOf(WindowRule.Measured(sixteenEight, fromEpochDay = today))
        val meals = listOf(mealAt(9), mealAt(16))

        assertThat(WindowRules.judge(fixed, meals, everythingRead, today, zone, wellAfterwards))
            .isNotNull()
        assertThat(WindowRules.judge(measured, meals, everythingRead, today, zone, wellAfterwards))
            .isNotNull()
    }

    @Test
    fun `the newest rule in force governs, whichever kind it is`() {
        val fixed = WindowRule.Fixed(untilEight.copy(fromEpochDay = today - 30))
        val measured = WindowRule.Measured(sixteenEight, fromEpochDay = today - 1)
        val rules = listOf(fixed, measured)

        assertThat(WindowRules.inForceOn(rules, today - 10)).isEqualTo(fixed)
        assertThat(WindowRules.inForceOn(rules, today)).isEqualTo(measured)
        assertThat(WindowRules.inForceOn(rules, today - 40)).isNull()
    }

    /**
     * Choosing the new kind must not re-score what the old kind governed: an old day stays a COUNT
     * verdict and never becomes a span one.
     */
    @Test
    fun `switching from hours to a ratio leaves the old days judged by the old rule`() {
        val rules = listOf(
            WindowRule.Fixed(untilEight.copy(fromEpochDay = today - 30)),
            WindowRule.Measured(sixteenEight, fromEpochDay = today - 1),
        )

        val old = WindowRules.judge(
            rules,
            listOf(mealAt(9, onDay = today - 10), mealAt(22, onDay = today - 10)),
            everythingRead,
            today - 10,
            zone,
            wellAfterwards,
        )
        val now = WindowRules.judge(
            rules,
            listOf(mealAt(9), mealAt(16)),
            everythingRead,
            today,
            zone,
            wellAfterwards,
        )

        assertThat(old).isInstanceOf(DayWindow::class.java)
        assertThat((old as DayWindow).mealsOutside).isEqualTo(1)
        assertThat(old.kept).isFalse()

        assertThat(now).isInstanceOf(DayMeasured::class.java)
        // A span over the calendar day was what this used to be; it is the one stretch the day
        // holds now, and 09:00 to 16:00 is the same seven hours either way.
        assertThat((now as DayMeasured).stretches.single().stretch.spanMinutes).isEqualTo(420)
        assertThat(now.kept).isTrue()
    }

    @Test
    fun `a day before any rule at all is not judged`() {
        val fixed = listOf(WindowRule.Fixed(untilEight))
        val measured = listOf(WindowRule.Measured(sixteenEight, fromEpochDay = today))
        val longAgo = today - 40
        val meals = listOf(mealAt(9, onDay = longAgo), mealAt(23, onDay = longAgo))

        assertThat(WindowRules.judge(fixed, meals, everythingRead, longAgo, zone, wellAfterwards))
            .isNull()
        assertThat(
            WindowRules.judge(measured, meals, everythingRead, longAgo, zone, wellAfterwards),
        ).isNull()
    }

    /**
     * A fortnight spanning a switch, and the two things this function is now for saying.
     *
     * **It adds both kinds without converting either.** Every day is scored by the rule that
     * actually governed it, a count of meals on one side of the switch and a walk over stretches on
     * the other, and the fraction is their sum.
     *
     * **Its measured half is a DAY-SLICED reading, and it flatters.** Each day is handed its own
     * meals and nothing else, so the seven ratio days below come back seven judged and seven kept —
     * while the record they were cut out of holds three BROKEN stretches. The eating runs 20:00 to
     * 22:00 and picks up again at 08:00 the next morning: under a sixteen-hour fast that is one
     * fourteen-hour stretch against a ratio allowing eight, and sliced at midnight it is two short
     * kept ones. The second half of this test asserts exactly that gap, against the same meals read
     * whole.
     *
     * That is not a defect to fix here. It is the reason this function has no measured caller left:
     * the day screen, the settings tally and the weekly compliment all clamp their range to the day
     * the rule in force began, so a ratio day never reaches this code in the app. The compliment
     * was the last one that did not clamp, and it was flattered by precisely the reading below.
     */
    @Test
    fun `days kept adds both kinds, and its ratio half reads narrower than the record`() {
        val rules = listOf(
            WindowRule.Fixed(untilEight.copy(fromEpochDay = today - 13)),
            WindowRule.Measured(sixteenEight, fromEpochDay = today - 6),
        )

        val byDay = mutableMapOf<Long, List<Meal>>()
        // Seven days under the hours: all kept but one late dinner.
        (13 downTo 7).forEach { back ->
            val day = today - back
            byDay[day] = if (back == 10) {
                listOf(mealAt(13, onDay = day), mealAt(22, onDay = day))
            } else {
                listOf(mealAt(13, onDay = day))
            }
        }
        // Seven days under the ratio, eaten late and finished the next morning. Every day's own
        // slice is a two-hour stretch; the record is three fourteen-hour ones and one short one.
        (6 downTo 0).forEach { back ->
            val day = today - back
            byDay[day] = if (back % 2 == 0) {
                listOf(mealAt(20, onDay = day), mealAt(22, onDay = day))
            } else {
                listOf(mealAt(8, onDay = day), mealAt(10, onDay = day))
            }
        }

        val (kept, judged) =
            WindowRules.daysKept(rules, byDay, today - 13, today, zone, wellAfterwards)

        // Seven judged under the hours and seven under the ratio; six kept and seven.
        assertThat(judged).isEqualTo(14)
        assertThat(kept).isEqualTo(13)

        // The same meals, read whole, judged by the same ratio. Four stretches it may judge, and
        // only one of them kept — the last, which began on the final day and had no morning after
        // it to run into. The day-sliced count above said seven of seven.
        val whole = WindowRules
            .stretches(
                rule = rules.last() as WindowRule.Measured,
                meals = byDay.values.flatten(),
                read = everythingRead,
                zone = zone,
                nowMillis = wellAfterwards,
            )
            .verdicts
            .filter { it.judged }

        assertThat(whole).hasSize(4)
        assertThat(whole.count { it.kept }).isEqualTo(1)
        // Fourteen hours, 20:00 to 10:00, against a ratio that allows eight.
        assertThat(whole.filterNot { it.kept }.map { it.stretch.spanMinutes })
            .containsExactly(840L, 840L, 840L)
    }

    /** A quiet day raises neither half of the fraction, so the count never reads as a failure. */
    @Test
    fun `an unjudged day is not counted as broken`() {
        val rules = listOf(WindowRule.Measured(sixteenEight, fromEpochDay = today - 2))
        val byDay = mapOf(
            (today - 2) to listOf(mealAt(9, onDay = today - 2), mealAt(16, onDay = today - 2)),
            (today - 1) to listOf(mealAt(9, onDay = today - 1)),
            today to listOf(mealAt(9), mealAt(16)),
        )

        val (kept, judged) =
            WindowRules.daysKept(rules, byDay, today - 2, today, zone, wellAfterwards)

        assertThat(judged).isEqualTo(2)
        assertThat(kept).isEqualTo(2)
    }

    /**
     * The fixed window, end to end, through the entry point the app actually calls.
     *
     * The numbers are written out as literals rather than computed from the other route on purpose:
     * two files that compute the same answer the same way can drift together and stay green.
     */
    @Test
    fun `the fixed window scores a day through the shared entry point exactly as it always did`() {
        val rules = listOf(WindowRule.Fixed(untilEight.copy(fromEpochDay = today - 30)))
        val meals = listOf(mealAt(8, 30), mealAt(13, 15), mealAt(19, 45), mealAt(21, 10))

        val verdict = WindowRules.judge(rules, meals, everythingRead, today, zone, wellAfterwards)

        assertThat(verdict).isInstanceOf(DayWindow::class.java)
        verdict as DayWindow
        assertThat(verdict.mealsInside).isEqualTo(3)
        assertThat(verdict.mealsOutside).isEqualTo(1)
        assertThat(verdict.mealsUntimed).isEqualTo(0)
        assertThat(verdict.kept).isFalse()
        assertThat(verdict.judged).isTrue()
    }

    /**
     * The only test here that compares the two routes directly. It is what fails if splitting the
     * gate from the scoring changed a result while both halves stayed individually green.
     */
    @Test
    fun `the days-kept tally for a fixed-only history is the same through either route`() {
        val window = untilEight.copy(fromEpochDay = today - 13)
        val byDay = (13 downTo 0).associate { back ->
            val day = today - back
            day to when (back) {
                11 -> listOf(mealAt(13, onDay = day), mealAt(22, onDay = day))
                5 -> emptyList()
                2 -> listOf(mealAt(21, onDay = day))
                else -> listOf(mealAt(8, onDay = day), mealAt(13, onDay = day))
            }
        }

        val throughRules = WindowRules.daysKept(
            listOf(WindowRule.Fixed(window)),
            byDay,
            today - 13,
            today,
            zone,
            wellAfterwards,
        )
        val throughWindows = EatingWindows.daysKept(listOf(window), byDay, today - 13, today, zone)

        assertThat(throughRules).isEqualTo(throughWindows)
        // And it is a real tally, not two empty pairs agreeing with each other.
        assertThat(throughRules.second).isEqualTo(13)
        assertThat(throughRules.first).isEqualTo(11)
    }

    // ------------------------------------------------------------------ the stretch rule (1.3)

    /** Fourteen hours fasting, ten eating — the ratio is written fasting first. */
    private val fourteenTen = MeasuredWindow(fastingHours = 14)

    private fun instantAt(day: Long, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(day), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * Design §2.4: a rule still never applies backwards, and for stretches the gate is the day the
     * stretch BEGAN on.
     *
     * The stretch that began yesterday comes back — the day screen has to be able to show it — but
     * it carries no verdict. Nothing already logged acquires a mark from a decision made
     * afterwards, which is the whole of D27.
     */
    @Test
    fun `a stretch beginning before the rule started is not judged, one beginning on that day is`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today)
        val yesterday = today - 1
        val meals = listOf(
            mealAt(9, onDay = yesterday),
            mealAt(15, onDay = yesterday),
            mealAt(9),
            mealAt(15),
        )

        // Eighteen hours between 15:00 yesterday and 09:00 today, so these really are two stretches.
        val verdicts =
            WindowRules.stretches(rule, meals, everythingRead, zone, instantAt(today + 2, 12))
                .verdicts

        assertThat(verdicts).hasSize(2)

        assertThat(verdicts[0].stretch.startedOnEpochDay).isEqualTo(yesterday)
        assertThat(verdicts[0].closed).isTrue()
        assertThat(verdicts[0].judged).isFalse()
        assertThat(verdicts[0].kept).isFalse()

        assertThat(verdicts[1].stretch.startedOnEpochDay).isEqualTo(today)
        assertThat(verdicts[1].judged).isTrue()
        assertThat(verdicts[1].kept).isTrue()
    }

    /**
     * The other half of design §2.4, and the half nothing pinned. A stretch that began before the
     * rule and has NOT yet closed keeps every fact about itself — including the moment it has to be
     * finished by, which the day screen shows — while the two things the gate controls stay false.
     *
     * Today that holds by construction, because the gate only rewrites `judged` and `kept`. This
     * test is what stops a later edit deciding that an ungated stretch has no closing time worth
     * announcing: silencing the verdict is not the same as erasing the record.
     */
    @Test
    fun `an open stretch from before the rule keeps its closing time and is still not judged`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today)
        val yesterday = today - 1

        // Began at 20:00 last night and last ate at 23:00, three hours ago; the fast is fourteen.
        val meals = listOf(mealAt(20, onDay = yesterday), mealAt(23, onDay = yesterday))

        val verdict = WindowRules.stretches(rule, meals, everythingRead, zone, instantAt(today, 2))
            .verdicts
            .single()

        assertThat(verdict.stretch.startedOnEpochDay).isEqualTo(yesterday)
        assertThat(verdict.closed).isFalse()
        assertThat(verdict.judged).isFalse()
        assertThat(verdict.kept).isFalse()
        // Its first input plus the ten eating hours, untouched by the gate.
        assertThat(verdict.closesAtMillis).isEqualTo(instantAt(today, 6))
    }

    /**
     * Reading the record is not judging it (design §2.4).
     *
     * The same two inputs on the rule's first day get opposite answers depending on an input from
     * the day BEFORE the rule existed: twelve hours after last night's dinner they continue
     * yesterday's stretch, which the rule may not judge; fifteen hours after it they open a stretch
     * of their own, which it may. The pre-rule input is never itself judged either way.
     */
    @Test
    fun `working out that a stretch begins may read an input from before the rule started`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today)
        val yesterday = today - 1
        val now = instantAt(today + 2, 12)

        val continued = WindowRules.stretches(
            rule,
            listOf(mealAt(20, onDay = yesterday), mealAt(8), mealAt(12)),
            everythingRead,
            zone,
            now,
        ).verdicts

        assertThat(continued).hasSize(1)
        assertThat(continued[0].stretch.startedOnEpochDay).isEqualTo(yesterday)
        assertThat(continued[0].stretch.inputs).isEqualTo(3)
        assertThat(continued[0].judged).isFalse()

        val fasted = WindowRules.stretches(
            rule,
            listOf(mealAt(17, onDay = yesterday), mealAt(8), mealAt(12)),
            everythingRead,
            zone,
            now,
        ).verdicts

        assertThat(fasted).hasSize(2)
        assertThat(fasted[0].stretch.startedOnEpochDay).isEqualTo(yesterday)
        assertThat(fasted[0].judged).isFalse()
        assertThat(fasted[1].stretch.startedOnEpochDay).isEqualTo(today)
        assertThat(fasted[1].stretch.inputs).isEqualTo(2)
        assertThat(fasted[1].judged).isTrue()
        assertThat(fasted[1].kept).isTrue()
    }

    /**
     * Design §3.1. Only closed, judged stretches count, and an open one is in neither half — the app
     * does not guess at a verdict it cannot yet have, and the count on screen never reads as a
     * failure for something it declined to have an opinion about.
     */
    @Test
    fun `the tally counts closed judged stretches only, and an open one raises neither half`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today - 6)
        val now = instantAt(today, 18)

        val meals = listOf(
            // Before the rule: a perfectly good stretch the rule may not judge.
            mealAt(9, onDay = today - 8), mealAt(15, onDay = today - 8),
            // Eight hours: kept.
            mealAt(9, onDay = today - 6), mealAt(17, onDay = today - 6),
            // Twelve hours against a ratio that allows ten: broken.
            mealAt(9, onDay = today - 5), mealAt(21, onDay = today - 5),
            // One input: closed, but a span of nothing is not judged.
            mealAt(13, onDay = today - 4),
            // Still open at 18:00 — four hours since the last input, and the fast is fourteen.
            mealAt(10), mealAt(14),
        )

        assertThat(WindowRules.stretchesKept(rule, meals, everythingRead, zone, now))
            .isEqualTo(1 to 2)

        val verdicts = WindowRules.stretches(rule, meals, everythingRead, zone, now).verdicts

        assertThat(verdicts).hasSize(5)
        val open = verdicts.last()
        assertThat(open.stretch.startedOnEpochDay).isEqualTo(today)
        assertThat(open.closed).isFalse()
        assertThat(open.judged).isFalse()
        assertThat(open.kept).isFalse()
        // Its closing time is the first input plus the eating hours: 10:00 + 10h.
        assertThat(open.closesAtMillis).isEqualTo(instantAt(today, 20))
    }

    // -------------------------------------------------------- where the read was cut (D4)

    /**
     * Seven days of eating that never fasts its hours, as one stretch: 09:00 and 20:00 every day,
     * every gap thirteen hours against a fourteen-hour fast.
     *
     * The record runs from ten days ago to six days ago and holds nothing outside that.
     */
    private fun oneLongStretch(): List<Meal> = (10L downTo 6L).flatMap { back ->
        listOf(mealAt(9, onDay = today - back), mealAt(20, onDay = today - back))
    }

    /**
     * **A stretch the read cut short is OPEN, not a measured span.**
     *
     * The whole of D4 in one test. The walk is handed five days and the eating does not stop inside
     * them, so the last input it can see only LOOKS final — and asking a moment a week later would
     * answer "long since closed" about eating whose end the walk never saw. The span that came out
     * would be the width of the read rather than a measurement of anything he did.
     *
     * A stretch is judged at the last moment the record is known complete to instead, which here is
     * midnight ending the read's last day. The fast had not completed by then, so it is open, and
     * open is the honest answer for as long as nothing more has been read.
     */
    @Test
    fun `a stretch still running when the read stopped is open, not a span`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today - 30)

        // Two days of lead-in with nothing logged in them, so the START of the stretch is not in
        // question here and only the end can be what withholds the verdict.
        val read = MealRead.OverDays(today - 12, today - 6)

        val verdict = WindowRules.stretches(rule, oneLongStretch(), read, zone, wellAfterwards)
            .verdicts
            .single()

        assertThat(verdict.stretch.startedOnEpochDay).isEqualTo(today - 10)
        assertThat(verdict.closed).isFalse()
        assertThat(verdict.judged).isFalse()
        assertThat(verdict.kept).isFalse()
    }

    /**
     * The same eating, read far enough to see the fast complete, IS judged — and broken.
     *
     * The control for the test above, and what stops the cut rule being satisfied by never judging
     * anything. Two further days are read and nothing was logged in them, so the fourteen hours
     * after the last input pass inside the read and the stretch is provably over. A hundred and
     * seven hours against a ratio allowing ten.
     */
    @Test
    fun `the same stretch read two days further is closed and judged`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today - 30)
        val read = MealRead.OverDays(today - 12, today - 4)

        val verdict = WindowRules.stretches(rule, oneLongStretch(), read, zone, wellAfterwards)
            .verdicts
            .single()

        assertThat(verdict.closed).isTrue()
        assertThat(verdict.judged).isTrue()
        assertThat(verdict.kept).isFalse()
        // Four days and eleven hours, to the minute.
        assertThat(verdict.stretch.spanMinutes).isEqualTo(107L * 60L)
    }

    /**
     * A stretch cut short by the end of the read is in NEITHER half of the tally.
     *
     * The same reason an open one is not: the app does not put a verdict on screen that the read
     * invented. Without this the fraction would gain a broken stretch every time the owner paged to
     * a day whose eating outran the days around it.
     */
    @Test
    fun `a stretch cut short at the end of the read counts in neither half of the tally`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today - 30)

        assertThat(
            WindowRules.stretchesKept(
                rule,
                oneLongStretch(),
                MealRead.OverDays(today - 12, today - 6),
                zone,
                wellAfterwards,
            ),
        ).isEqualTo(0 to 0)

        // And it is a real tally rather than an empty one agreeing with itself: read far enough to
        // see the fast complete and the same eating counts, as one stretch that was broken.
        assertThat(
            WindowRules.stretchesKept(
                rule,
                oneLongStretch(),
                MealRead.OverDays(today - 12, today - 4),
                zone,
                wellAfterwards,
            ),
        ).isEqualTo(0 to 1)
    }

    /**
     * The mirror at the other end: a stretch that may have begun before the read is not judged.
     *
     * Its first input is nine hours after the read opens and the fast is fourteen, so eating the
     * evening before would have been part of the same stretch and the walk cannot see whether there
     * was any. Its start day and its span are then the shape of the read rather than his eating,
     * and a span the read invented is the same D4 offence at either end.
     *
     * This is also why the lead-in is a requirement and not advice: two calendar days is longer
     * than any fast a ratio allows, so nothing the rule may judge is ever cut at the start in the
     * app.
     */
    @Test
    fun `a stretch that may have begun before the read starts is not judged`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today - 30)
        val meals = listOf(mealAt(9, onDay = today - 10), mealAt(14, onDay = today - 10))

        val cut = WindowRules.stretches(
            rule,
            meals,
            MealRead.OverDays(today - 10, today - 4),
            zone,
            wellAfterwards,
        ).verdicts.single()

        assertThat(cut.closed).isTrue()
        assertThat(cut.judged).isFalse()
        assertThat(cut.kept).isFalse()

        // The same eating, read from two days earlier: the fast before it passes inside the read,
        // so the stretch provably begins where it appears to, and five hours keeps a 14/10.
        val whole = WindowRules.stretches(
            rule,
            meals,
            MealRead.OverDays(today - 12, today - 4),
            zone,
            wellAfterwards,
        ).verdicts.single()

        assertThat(whole.judged).isTrue()
        assertThat(whole.kept).isTrue()
    }

    /**
     * And a read now SAYS whether it held the stretch's beginning, rather than only declining to
     * judge one it did not.
     *
     * The same two reads as the test above. A caller that means to state how long the eating has
     * gone on — the day screen's live line (design §3.0a) — cannot tell a stretch's real beginning
     * from the edge of its own read unless it is told, and a duration measured from the edge would
     * be an artefact presented as a measurement, which D4 forbids by name. Being told is what lets
     * it read further back until the beginning is inside.
     */
    @Test
    fun `a read says whether it held the stretch's beginning`() {
        val rule = WindowRule.Measured(fourteenTen, fromEpochDay = today - 30)
        val meals = listOf(mealAt(9, onDay = today - 10), mealAt(14, onDay = today - 10))

        fun startKnownOver(read: MealRead): Boolean =
            WindowRules.stretches(rule, meals, read, zone, wellAfterwards).verdicts.single()
                .startKnown

        // Nine hours after the read opens, against a fourteen-hour fast: the eating the evening
        // before was never looked at, so where this stretch began is not known.
        assertThat(startKnownOver(MealRead.OverDays(today - 10, today - 4))).isFalse()
        // Two days earlier, and the fast before it passes inside the read.
        assertThat(startKnownOver(MealRead.OverDays(today - 12, today - 4))).isTrue()
        // A read that says it is the whole record cuts nothing at either end.
        assertThat(startKnownOver(MealRead.Whole)).isTrue()
    }

    /**
     * The fixed hours count the day's OWN meals, however wide a range they are handed.
     *
     * Live, not theoretical: with a ratio in force today the day screen reads three days at a time
     * and hands the whole lot to the same entry point, whichever kind governed the day on screen.
     * Without the filter a day governed by fixed hours would have another day's meals counted into
     * its inside and outside — four inside here instead of two.
     */
    @Test
    fun `the fixed hours count only the day's own meals, however wide the range handed over`() {
        val rules = listOf(WindowRule.Fixed(untilEight.copy(fromEpochDay = today - 30)))
        val meals = listOf(
            mealAt(9, onDay = today - 1),
            mealAt(8, 30),
            mealAt(13, 15),
            mealAt(21, 10),
            mealAt(9, onDay = today + 1),
        )

        val verdict =
            WindowRules.judge(rules, meals, everythingRead, today, zone, wellAfterwards) as DayWindow

        assertThat(verdict.mealsInside).isEqualTo(2)
        assertThat(verdict.mealsOutside).isEqualTo(1)
    }
}
