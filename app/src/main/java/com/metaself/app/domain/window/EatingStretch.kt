package com.metaself.app.domain.window

import com.metaself.app.domain.day.Meal
import java.time.Instant
import java.time.ZoneId

/**
 * A run of eating with a fast on each side of it.
 *
 * The unit the measured window judges (design §2). Consecutive inputs belong to the same stretch
 * while the gap between them is SHORTER than the fasting hours; a gap of at least the fasting hours
 * closes one and the next input opens another.
 *
 * [startedAtMillis] and [lastAtMillis] are moments, not minutes from midnight. A ratio is a
 * statement about hours, and the date a moment happens to fall on is an artefact of the log — which
 * is why a stretch that crosses midnight is one stretch and not two halves of two days. It is also
 * what makes an hour the clocks moved harmless: see [spanMinutes].
 *
 * The record and the verdict are deliberately two types. A stretch is what happened and is true
 * forever; whether it is closed, judged or kept depends on when you ask, so those are only
 * reachable through [at], which makes "when are you asking?" impossible to leave out.
 *
 * @property inputs how many timed inputs it holds. Meals with no trustworthy hour never reach here
 *   at all — they are dropped by [EatingStretches.of] before any stretch is worked out and counted
 *   in [EatingStretchWalk.mealsUntimed] instead (design §2.3).
 * @property startedOnEpochDay the local date of the FIRST input, in the zone the walk was given. A
 *   stretch belongs to the day it began on and never to two (design §3), because giving it to both
 *   would let one late dinner break two days.
 */
data class EatingStretch(
    val startedAtMillis: Long,
    val lastAtMillis: Long,
    val inputs: Int,
    val startedOnEpochDay: Long,
) {

    /**
     * First input to last input, rounded UP to the minute.
     *
     * Subtracted from the instants themselves rather than from two clock readings, so a night the
     * clocks moved is the number of hours that actually passed (design §2.2). Reading clocks would
     * see a fast that never happened in the spring and miss one that did in the autumn.
     *
     * Up rather than down, deliberately. `loggedAtMillis` comes from the system clock, so these
     * moments carry seconds, and truncating would forgive up to 59.999 seconds of an over-long
     * stretch. The day rule this replaces differenced two clock readings, where the seconds simply
     * vanished and a span could come out a minute either side of the truth; rounding up is the only
     * direction that cannot flatter a span the app is about to judge, and this app refuses rather
     * than rounds in its own favour. So a stretch of 10h00m01s against a ratio allowing ten hours is
     * broken, by a minute, and says so.
     *
     * Zero for a single input, which is a span that would keep any ratio — see [StretchVerdict].
     */
    val spanMinutes: Long
        get() = (lastAtMillis - startedAtMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE

    /**
     * Whether the eating in this stretch has gone on longer than the ratio allows.
     *
     * The ONE rule for running over, used both by the verdict a closed stretch is given and by the
     * sentence said about an open one while it is still going. Two copies of it could drift, and the
     * app would then tell the owner mid-stretch that he had run over a ratio it went on to say he
     * kept — or the reverse. Strictly over: exactly the eating hours keeps the ratio.
     *
     * Measured over the eating only — first input to last — and never to the moment of asking. A
     * stretch stays open until the fast after it completes, and those fasting hours are not eating.
     */
    fun ranOver(window: MeasuredWindow): Boolean = spanMinutes > window.eatingHours.toLong() * 60L

    /**
     * Whether the fast that ends this stretch has completed.
     *
     * "Now" is supplied rather than read from a clock in here, the pattern the app's `Now` interface
     * sets everywhere else the moment is needed. The boundary is inclusive: fourteen hours exactly
     * counts as fasted, the same reading the fixed window gives its end hour, where "until 20:00"
     * means shut AT 20:00 and not at 20:59.
     */
    fun closed(window: MeasuredWindow, nowMillis: Long): Boolean =
        nowMillis >= lastAtMillis + window.fastingHours * MILLIS_PER_HOUR

    /**
     * What a given moment makes of this stretch.
     *
     * The only route to [StretchVerdict.judged] and [StretchVerdict.kept], so neither can be asked
     * without saying when it is being asked. An honest consequence of counting hours instead of
     * days: until the fast completes there is no verdict to give, however far over the ratio the
     * stretch already runs (design §5).
     *
     * The gate on the rule's start day is NOT applied here. This is the ungated meaning, which is
     * what the walk itself knows; whether the rule in force may judge the stretch at all belongs to
     * [WindowRules.stretches].
     *
     * `internal`, because it bypasses the gate by design: only [WindowRules.stretches] may reach it,
     * and that passes the gate first. The unit tests are a friend path and still see it.
     */
    internal fun at(window: MeasuredWindow, nowMillis: Long): StretchVerdict {
        val closed = closed(window, nowMillis)
        val judged = closed && inputs >= 2
        return StretchVerdict(
            stretch = this,
            closed = closed,
            judged = judged,
            kept = judged && !ranOver(window),
            closesAtMillis = if (closed) {
                null
            } else {
                startedAtMillis + window.eatingHours * MILLIS_PER_HOUR
            },
            // The ungated meaning knows nothing of reads: it was handed a stretch and it answers
            // about that stretch. Only [WindowRules.stretches] has been told where the read was
            // cut, so only it can say the beginning was cut off.
            startKnown = true,
        )
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60L * 1000L
        const val MILLIS_PER_HOUR = 60L * 60L * 1000L
    }
}

/**
 * What one moment makes of one stretch.
 *
 * Separate from [EatingStretch] because the stretch is a fact and this is an opinion held at a time:
 * the same stretch is open this afternoon and closed tomorrow morning, and nothing should be able to
 * read "kept" off the record without having said which of the two it meant.
 *
 * @property judged whether it is scored at all. A stretch is judged once it has closed AND holds two
 *   or more inputs. One input is a span of nothing and would trivially keep any ratio, which would
 *   be a compliment for having logged almost nothing (design §2.1) — so it is neither kept nor
 *   broken, and in neither half of the tally.
 * @property kept only meaningful when [judged]: the span is no longer than the eating hours. "No
 *   longer than" includes the boundary, and it is measured to the minute — on a 14/10, 09:30 to
 *   19:31 is over by one minute and is broken.
 * @property closesAtMillis when an open stretch must have finished by: its FIRST input plus the
 *   eating hours. Null once it has closed, because a stretch that is over has no deadline left to
 *   announce.
 * @property startKnown whether the read the walk was given reached back far enough to hold this
 *   stretch's real beginning. False when the first input sits within one fast of where the read
 *   starts, because eating before that moment could have been part of the same stretch and was
 *   never looked at. It is already the reason such a stretch is not judged; it is said out loud
 *   because [startedAtMillis][EatingStretch.startedAtMillis] is then the READ's shape rather than
 *   the owner's, and a caller that wants to state how long the eating has gone on has to know it
 *   would be stating an artefact (D4). A caller that wants the true beginning reads further back
 *   until this is true.
 */
data class StretchVerdict(
    val stretch: EatingStretch,
    val closed: Boolean,
    val judged: Boolean,
    val kept: Boolean,
    val closesAtMillis: Long?,
    val startKnown: Boolean,
)

/**
 * What one walk of the record found.
 *
 * The count travels WITH the stretches because it is the same walk's answer: the meals that had to
 * be left out are exactly the ones the stretches cannot account for, and a caller that had to ask a
 * second question to learn how many there were could get the two out of step (design §2.3).
 *
 * @property stretches oldest first.
 * @property mealsUntimed meals with no trustworthy hour, dropped before any stretch was worked out
 *   and counted here instead — the same number the day verdict has always reported.
 */
data class EatingStretchWalk(
    val stretches: List<EatingStretch>,
    val mealsUntimed: Int,
)

/** The walk that turns a pile of meals into the stretches they made. */
object EatingStretches {

    /**
     * The stretches a list of meals forms, oldest first, and how many meals could not be timed.
     *
     * The order is the decision:
     *
     * 1. Drop anything that cannot be timed FIRST (design §2.3). A meal written down on a different
     *    day from the one it belongs to keeps when it was TYPED, not when it was eaten. A span is
     *    far more exposed to that than a count is: a meal typed two days late would otherwise open
     *    a stretch of its own in the middle of the record, or stretch a real one to fifty hours.
     * 2. Sort what is left by the moment it happened, because nothing may depend on the order the
     *    database hands the rows over in.
     * 3. Walk it, breaking wherever the gap reaches the fasting hours.
     *
     * Pure and ungated: the rule's start day plays no part here, which is why this takes a ratio and
     * never a [WindowRule]. The gate lives in [WindowRules.stretches].
     *
     * `internal`, because it bypasses the gate by design: only [WindowRules.stretches] may reach it,
     * and that passes the gate first. The unit tests are a friend path and still see it.
     */
    internal fun of(window: MeasuredWindow, meals: List<Meal>, zone: ZoneId): EatingStretchWalk {
        val (timedMeals, untimedMeals) = meals.partition { meal -> hasItsOwnTime(meal, zone) }
        val timed = timedMeals.map { it.loggedAtMillis }.sorted()

        if (timed.isEmpty()) {
            return EatingStretchWalk(stretches = emptyList(), mealsUntimed = untimedMeals.size)
        }

        val fast = window.fastingHours * 60L * 60L * 1000L
        val stretches = mutableListOf<EatingStretch>()

        var startedAt = timed.first()
        var lastAt = timed.first()
        var inputs = 1

        timed.drop(1).forEach { at ->
            if (at - lastAt >= fast) {
                stretches += finish(startedAt, lastAt, inputs, zone)
                startedAt = at
                inputs = 1
            } else {
                inputs++
            }
            lastAt = at
        }
        stretches += finish(startedAt, lastAt, inputs, zone)

        return EatingStretchWalk(stretches = stretches, mealsUntimed = untimedMeals.size)
    }

    /**
     * One finished stretch, with the day it began on read off its first input.
     *
     * The day comes from the START and never from the end, so a stretch that runs past midnight
     * still belongs to the evening it began in (design §3).
     */
    private fun finish(startedAt: Long, lastAt: Long, inputs: Int, zone: ZoneId): EatingStretch =
        EatingStretch(
            startedAtMillis = startedAt,
            lastAtMillis = lastAt,
            inputs = inputs,
            startedOnEpochDay = Instant.ofEpochMilli(startedAt)
                .atZone(zone)
                .toLocalDate()
                .toEpochDay(),
        )
}

/**
 * Whether a meal's stored moment is a time on its own day — whether it can be timed at all.
 *
 * The stored moment is when the meal was LOGGED. Logged on its own day, that is when it was eaten
 * as nearly as anything can say; written down later onto an earlier day, it is the moment of writing,
 * on another date, and says nothing about when it was eaten (design §2.3). One predicate for the
 * walk, the day's admission of what it left out, and the time the day shows beside each meal (D33),
 * so the three cannot disagree about which meals those are.
 */
fun hasItsOwnTime(meal: Meal, zone: ZoneId): Boolean =
    Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone).toLocalDate().toEpochDay() == meal.epochDay
