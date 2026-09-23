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
 * An eating stretch: a run of inputs with a fast on each side of it.
 *
 * The rule (design §2): consecutive inputs belong to the same stretch while the gap between them is
 * SHORTER than the fasting hours. A gap of at least the fasting hours closes one and the next input
 * opens another. A stretch is closed once the fasting hours have passed since its LAST input, and
 * open until then. A closed stretch of two or more inputs is judged, and kept when its span — first
 * input to last input — is no longer than the eating hours.
 *
 * Two things are deliberately not the old day rule and are asserted here rather than assumed:
 *
 * - **Gaps are between instants, not clock readings** (design §2.2), so an hour the clocks moved
 *   cannot manufacture or erase a fast.
 * - **Midnight is an artefact of the log** (design §1). A stretch that crosses it is one stretch,
 *   and belongs to the day it BEGAN on (design §3).
 *
 * `EatingStretches.of` is the ungated walk: the rule's start day plays no part in it, which is why
 * every test here passes a ratio and never a [WindowRule]. The gate is `WindowRules.stretches`, and
 * it is proved in `WindowRulesTest`.
 *
 * The fixed-hours window is a different thing and is untouched by any of this; it is proved in
 * `EatingWindowTest`, and the day-span rule this replaces in `MeasuredWindowTest`.
 */
class EatingStretchTest {

    private val zone: ZoneId = ZoneId.of("UTC+01:00")
    private val today = LocalDate.of(2026, 9, 5).toEpochDay()

    /** Fourteen hours fasting, ten eating — the ratio is written fasting first. */
    private val fourteenTen = MeasuredWindow(fastingHours = 14)

    private fun instant(
        day: Long,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
        inZone: ZoneId = zone,
    ): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(day), LocalTime.of(hour, minute, second))
            .atZone(inZone)
            .toInstant()
            .toEpochMilli()

    /**
     * One input.
     *
     * [onDay] defaults to the day it was logged on, which is what makes it timed. Passing a
     * different [onDay] is how a meal filled in late is written — see the exclusion test.
     */
    private fun meal(
        day: Long,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
        onDay: Long = day,
        inZone: ZoneId = zone,
    ): Meal = Meal(
        epochDay = onDay,
        loggedAtMillis = instant(day, hour, minute, second, inZone),
        note = null,
        items = listOf(anItem()),
    )

    private fun hours(n: Long): Long = n * 60L * 60L * 1000L

    // ---------------------------------------------------------------- what a stretch is (1.1)

    @Test
    fun `inputs closer together than the fast are one stretch`() {
        val stretches = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9), meal(today, 13), meal(today, 19)),
            zone,
        ).stretches

        assertThat(stretches).hasSize(1)
        assertThat(stretches[0].inputs).isEqualTo(3)
        assertThat(stretches[0].startedAtMillis).isEqualTo(instant(today, 9))
        assertThat(stretches[0].lastAtMillis).isEqualTo(instant(today, 19))
        assertThat(stretches[0].startedOnEpochDay).isEqualTo(today)
        assertThat(stretches[0].spanMinutes).isEqualTo(600)
    }

    /**
     * Fourteen hours exactly counts as fasted — the same reading the fixed window gives its end
     * hour, where "until 20:00" means shut AT 20:00 and not at 20:59.
     */
    @Test
    fun `a gap of exactly the fasting hours starts a new one`() {
        val exactly = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 8), meal(today, 22)),
            zone,
        ).stretches

        assertThat(exactly).hasSize(2)
        assertThat(exactly[0].lastAtMillis).isEqualTo(instant(today, 8))
        assertThat(exactly[1].startedAtMillis).isEqualTo(instant(today, 22))

        // And the minute below the boundary is still one stretch, so the line is where it is said
        // to be rather than an hour either side of it.
        val oneMinuteShort = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 8), meal(today, 21, 59)),
            zone,
        ).stretches

        assertThat(oneMinuteShort).hasSize(1)
        assertThat(oneMinuteShort[0].inputs).isEqualTo(2)
    }

    @Test
    fun `a gap longer than the fast starts a new one`() {
        val stretches = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 12), meal(today, 19), meal(today + 1, 10)),
            zone,
        ).stretches

        assertThat(stretches).hasSize(2)
        assertThat(stretches[0].inputs).isEqualTo(2)
        assertThat(stretches[0].startedOnEpochDay).isEqualTo(today)
        assertThat(stretches[1].inputs).isEqualTo(1)
        assertThat(stretches[1].startedOnEpochDay).isEqualTo(today + 1)
    }

    // ------------------------------------------------------- open, closed, judged, kept (1.2)

    /**
     * Whether a stretch has closed depends on how long ago its last input was, so "now" is supplied
     * rather than read from a clock inside the domain — the pattern the `Now` interface sets for
     * everything else in the app that needs the moment.
     */
    @Test
    fun `a stretch that has not yet fasted its hours is still open`() {
        val stretch = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9), meal(today, 13)),
            zone,
        ).stretches.single()

        val now = instant(today, 15)

        assertThat(stretch.closed(fourteenTen, now)).isFalse()

        val verdict = stretch.at(fourteenTen, now)
        assertThat(verdict.closed).isFalse()
        // Its closing time is the FIRST input plus the eating hours: 09:00 + 10h.
        assertThat(verdict.closesAtMillis).isEqualTo(instant(today, 19))
    }

    @Test
    fun `a stretch is closed once the fast has passed since its last input`() {
        // The two types are named here on purpose: a stretch is the record of what happened, and a
        // verdict is what a given "now" makes of it. Nothing can ask "was it kept?" of the first.
        val stretch: EatingStretch = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9), meal(today, 13)),
            zone,
        ).stretches.single()

        val fourteenHoursAfterTheLast = instant(today, 13) + hours(14)

        assertThat(stretch.closed(fourteenTen, fourteenHoursAfterTheLast - 60_000L)).isFalse()
        assertThat(stretch.closed(fourteenTen, fourteenHoursAfterTheLast)).isTrue()

        val verdict: StretchVerdict = stretch.at(fourteenTen, fourteenHoursAfterTheLast)
        assertThat(verdict.closed).isTrue()
        // A closed stretch has no closing time left to announce.
        assertThat(verdict.closesAtMillis).isNull()
    }

    /**
     * The honest consequence of hours instead of days: until the fast completes there is no verdict
     * to give, however far over the ratio the stretch already runs. The app says so rather than
     * guessing (design §5).
     */
    @Test
    fun `an open stretch is not judged, however long it already is`() {
        val stretch = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 8), meal(today, 18), meal(today, 22)),
            zone,
        ).stretches.single()

        // Four hours after the last input: the fast is nowhere near done.
        val now = instant(today + 1, 2)
        val verdict = stretch.at(fourteenTen, now)

        // Fourteen hours of eating, against a ratio that allows ten.
        assertThat(stretch.spanMinutes).isEqualTo(840)
        assertThat(verdict.closed).isFalse()
        assertThat(verdict.judged).isFalse()
        assertThat(verdict.kept).isFalse()
    }

    /**
     * A span of nothing would keep any ratio, which is a compliment for having logged almost
     * nothing. Neither kept nor broken, and in neither half of the tally (design §2.1).
     */
    @Test
    fun `a closed stretch of one input is not judged`() {
        val stretch = EatingStretches.of(fourteenTen, listOf(meal(today, 13)), zone)
            .stretches
            .single()

        val verdict = stretch.at(fourteenTen, instant(today + 1, 6))

        assertThat(stretch.inputs).isEqualTo(1)
        assertThat(stretch.spanMinutes).isEqualTo(0)
        assertThat(verdict.closed).isTrue()
        assertThat(verdict.judged).isFalse()
        assertThat(verdict.kept).isFalse()
    }

    /** Exactly the eating hours is kept: "no longer than" includes the boundary. */
    @Test
    fun `a closed stretch inside the eating hours is kept`() {
        val stretch = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9, 30), meal(today, 14), meal(today, 19, 30)),
            zone,
        ).stretches.single()

        val verdict = stretch.at(fourteenTen, instant(today + 1, 12))

        assertThat(stretch.spanMinutes).isEqualTo(600)
        assertThat(verdict.closed).isTrue()
        assertThat(verdict.judged).isTrue()
        assertThat(verdict.kept).isTrue()
    }

    @Test
    fun `a closed stretch longer than the eating hours is broken`() {
        val stretch = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9), meal(today, 20)),
            zone,
        ).stretches.single()

        val verdict = stretch.at(fourteenTen, instant(today + 1, 12))

        assertThat(stretch.spanMinutes).isEqualTo(660)
        assertThat(verdict.judged).isTrue()
        assertThat(verdict.kept).isFalse()
    }

    /**
     * To the minute, not to the hour. On a 14/10, 09:30 to 19:31 is over by one minute and is
     * broken; an hours-only reading would have called it ten hours and kept it.
     */
    @Test
    fun `a stretch is measured to the minute`() {
        val stretch = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9, 30), meal(today, 19, 31)),
            zone,
        ).stretches.single()

        val verdict = stretch.at(fourteenTen, instant(today + 1, 12))

        assertThat(stretch.spanMinutes).isEqualTo(601)
        assertThat(verdict.judged).isTrue()
        assertThat(verdict.kept).isFalse()
    }

    // ----------------------------------------------------- midnight, the clocks, and the record

    /**
     * Seconds are real — `loggedAtMillis` comes from the system clock — so the direction a span
     * rounds in is a decision and not an accident. It rounds UP: one second past the eating hours
     * is a stretch a minute too long, and is broken.
     *
     * Truncating would have forgiven up to 59.999 seconds, which is the flattering direction, and
     * this app refuses rather than rounds in its own favour. The second case is the one the day rule
     * this replaces got right: it differenced two clock readings, so 09:30:59 to 19:31:01 came out
     * as 601 minutes and broken, and truncating here would have quietly started keeping it.
     */
    @Test
    fun `a span is rounded up, never in the owner's favour`() {
        val oneSecondOver = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9, 30), meal(today, 19, 30, second = 1)),
            zone,
        ).stretches.single()

        assertThat(oneSecondOver.spanMinutes).isEqualTo(601)
        assertThat(oneSecondOver.at(fourteenTen, instant(today + 1, 12)).kept).isFalse()

        val theOldRuleAlsoBroke = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9, 30, second = 59), meal(today, 19, 31, second = 1)),
            zone,
        ).stretches.single()

        assertThat(theOldRuleAlsoBroke.spanMinutes).isEqualTo(601)
        assertThat(theOldRuleAlsoBroke.at(fourteenTen, instant(today + 1, 12)).kept).isFalse()

        // Exactly the eating hours, to the second, is still kept: rounding up adds nothing.
        val exactly = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 9, 30), meal(today, 19, 30)),
            zone,
        ).stretches.single()

        assertThat(exactly.spanMinutes).isEqualTo(600)
        assertThat(exactly.at(fourteenTen, instant(today + 1, 12)).kept).isTrue()
    }

    /**
     * The case the whole change exists for. Under the old day rule the 01:00 input started the next
     * day's span early and could break it on its own; here it is simply the end of the stretch that
     * began at 18:00, and that stretch belongs to the day it began on.
     */
    @Test
    fun `a stretch that crosses midnight is one stretch`() {
        val stretches = EatingStretches.of(
            fourteenTen,
            listOf(meal(today, 18), meal(today, 23, 30), meal(today + 1, 1)),
            zone,
        ).stretches

        assertThat(stretches).hasSize(1)
        val stretch = stretches.single()
        assertThat(stretch.inputs).isEqualTo(3)
        assertThat(stretch.startedOnEpochDay).isEqualTo(today)
        assertThat(stretch.startedAtMillis).isEqualTo(instant(today, 18))
        assertThat(stretch.lastAtMillis).isEqualTo(instant(today + 1, 1))
        assertThat(stretch.spanMinutes).isEqualTo(420)

        val verdict = stretch.at(fourteenTen, instant(today + 1, 20))
        assertThat(verdict.judged).isTrue()
        assertThat(verdict.kept).isTrue()
    }

    /**
     * Gaps come from the moments themselves, so a night the clocks moved is still the number of
     * hours that actually passed (design §2.2).
     *
     * New York, because its two transitions are the textbook ones. In the spring the clock reads
     * fourteen and a half hours between the two inputs and only thirteen and a half actually
     * passed, so it is ONE stretch — a clock-reading implementation would see a completed fast that
     * never happened. In the autumn the clock reads thirteen and a half and fourteen and a half
     * passed, so it is TWO — a clock-reading implementation would miss a fast that did happen.
     */
    @Test
    fun `an hour the clocks moved does not make or break a fast`() {
        val newYork = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8).toEpochDay() // 02:00 becomes 03:00.
        val fallBack = LocalDate.of(2026, 11, 1).toEpochDay() // 02:00 becomes 01:00.

        val overTheSpring = EatingStretches.of(
            fourteenTen,
            listOf(
                meal(springForward - 1, 21, inZone = newYork),
                meal(springForward, 11, 30, inZone = newYork),
            ),
            newYork,
        ).stretches

        assertThat(overTheSpring).hasSize(1)
        // Thirteen and a half hours really passed, which is also the span: over the ten allowed.
        assertThat(overTheSpring[0].spanMinutes).isEqualTo(810)
        assertThat(
            overTheSpring[0].at(fourteenTen, instant(springForward + 2, 12, inZone = newYork)).kept,
        ).isFalse()

        val overTheAutumn = EatingStretches.of(
            fourteenTen,
            listOf(
                meal(fallBack - 1, 22, inZone = newYork),
                meal(fallBack, 11, 30, inZone = newYork),
            ),
            newYork,
        ).stretches

        assertThat(overTheAutumn).hasSize(2)
        assertThat(overTheAutumn[0].inputs).isEqualTo(1)
        assertThat(overTheAutumn[1].inputs).isEqualTo(1)
    }

    /**
     * Design §2.3, and the order is the decision. A meal typed on a different day from the one it
     * belongs to keeps when it was TYPED, and one typed two days late would otherwise open a
     * stretch of its own in the middle of the record, or stretch a real one to fifty hours.
     */
    @Test
    fun `a meal with no trustworthy hour is left out before any stretch is worked out`() {
        val walk = EatingStretches.of(
            fourteenTen,
            listOf(
                meal(today, 12),
                meal(today, 14),
                // Eaten today, written down at 10:00 two days later.
                meal(day = today + 2, hour = 10, onDay = today),
            ),
            zone,
        )

        val stretches = walk.stretches
        assertThat(stretches).hasSize(1)
        assertThat(stretches[0].inputs).isEqualTo(2)
        assertThat(stretches[0].startedAtMillis).isEqualTo(instant(today, 12))
        assertThat(stretches[0].lastAtMillis).isEqualTo(instant(today, 14))
        assertThat(stretches[0].spanMinutes).isEqualTo(120)

        // Left out of the walk, but not lost. The day screen still says how many meals had no
        // trustworthy hour, and that number has to be this walk's own answer rather than a second
        // count taken somewhere else, or the two can disagree (design §2.3).
        assertThat(walk.mealsUntimed).isEqualTo(1)
    }

    /** Nothing may depend on the order the database happens to hand the meals over in. */
    @Test
    fun `the stretches come back in the order they happened`() {
        val meals = listOf(
            meal(today + 2, 11),
            meal(today, 19),
            meal(today + 1, 12),
            meal(today, 13),
            meal(today + 2, 15),
            meal(today + 1, 20),
        )

        val stretches = EatingStretches.of(fourteenTen, meals.shuffled(), zone).stretches

        assertThat(stretches).hasSize(3)
        assertThat(stretches.map { it.startedOnEpochDay })
            .containsExactly(today, today + 1, today + 2)
            .inOrder()
        assertThat(stretches.map { it.startedAtMillis })
            .containsExactly(instant(today, 13), instant(today + 1, 12), instant(today + 2, 11))
            .inOrder()
        assertThat(stretches.map { it.lastAtMillis })
            .containsExactly(instant(today, 19), instant(today + 1, 20), instant(today + 2, 15))
            .inOrder()
    }
}
