package com.metaself.app.domain.window

import com.metaself.app.domain.day.Meal
import java.time.LocalDate
import java.time.ZoneId

/**
 * A ratio: how much of the day is fasting, and how much is eating.
 *
 * Written fasting first, the way the ratio is written everywhere else in the world — "16/8" is
 * sixteen hours fasting and eight eating. That reading is pinned here, in the domain, rather than
 * only on the settings screen, because reading the first number as the eating hours would silently
 * mean the opposite of what the owner typed.
 *
 * It carries no day of its own. The day a rule begins belongs to the rule, not to the ratio; see
 * [WindowRule.Measured].
 */
data class MeasuredWindow(val fastingHours: Int) {
    init {
        require(fastingHours in 1..23) { "a ratio leaves some of the day for each half" }
    }

    val eatingHours: Int get() = 24 - fastingHours
}

/**
 * What a day did about the window that governed it, whichever kind that was.
 *
 * Sealed with exactly two members so that a `when` over it is exhaustive and neither kind can be
 * read as the other. A count of meals inside and outside does not describe a span, and a span does
 * not describe a count.
 */
sealed interface DayVerdict {

    /** Whether this day was scored at all. */
    val judged: Boolean

    /** Only meaningful when [judged]. */
    val kept: Boolean

    /** Meals with no trustworthy hour, counted the same way by both kinds. */
    val mealsUntimed: Int
}

/**
 * The stretch verdict: the eating stretches that BEGAN on a day, and what a moment makes of them.
 *
 * It has no "meals inside", no "meals outside" and no span of its own, and never will — those words
 * do not describe a list of stretches. A day holds the stretches it STARTED (design §3): a stretch
 * that began last night and ended this morning belongs to last night, because giving it to both
 * days would let one late dinner break two of them. So [stretches] is empty on a day the owner ate
 * on but began nothing on, and that day has no verdict of either kind.
 *
 * This replaced a span over the calendar day. A ratio is a statement about hours, and midnight is
 * an artefact of the log; the day-span rule and its `measure()` were removed with the change rather
 * than left beside it, because two rules that answer the same question differently is how a screen
 * comes to disagree with itself.
 *
 * @property nowMillis the moment [stretches] were judged at, carried so the screen can say "done
 *   by" and "still open" from the same reading the verdict was made from. A second clock read on
 *   the way to the words could put the sentence and the verdict minutes apart.
 * @property zone the zone the stretches were walked in, carried for the same reason [nowMillis] is.
 *   The verdict decides which day a stretch began on in this zone, and the screen turns its moments
 *   into "12:00" — so a screen that reached for the device's zone at the moment of printing could
 *   draw a clock time the verdict never agreed to. One reading, one zone, one answer.
 * @property mealsUntimed meals with no trustworthy hour, from the same walk (design §2.3).
 */
data class DayMeasured(
    val window: MeasuredWindow,
    val stretches: List<StretchVerdict>,
    val nowMillis: Long,
    val zone: ZoneId,
    override val mealsUntimed: Int,
) : DayVerdict {

    /**
     * Whether the day is scored at all: it holds at least one stretch the rule could judge.
     *
     * A day whose only stretch is still open is not judged — there is no verdict to have yet — and
     * neither is one whose stretches hold a single input each. Both are silence rather than a mark.
     */
    override val judged: Boolean get() = stretches.any { it.judged }

    /** Every stretch that WAS judged kept the ratio. One broken stretch breaks the day. */
    override val kept: Boolean
        get() = judged && stretches.filter { it.judged }.all { it.kept }
}

/**
 * A window the owner set, of either kind, and the day it begins governing.
 *
 * The asymmetry is deliberate. [EatingWindow] already carries its own `fromEpochDay` — that is the
 * whole of its design — so [Fixed] inherits the day from the window it wraps rather than storing a
 * second one that could disagree with it. [MeasuredWindow] holds only the ratio, so [Measured] has
 * to carry the day itself; leaving it off would make a ratio ungated, which is the one rule this
 * design exists to keep.
 */
sealed interface WindowRule {

    val fromEpochDay: Long

    data class Fixed(val window: EatingWindow) : WindowRule {
        override val fromEpochDay: Long get() = window.fromEpochDay
    }

    data class Measured(
        val window: MeasuredWindow,
        override val fromEpochDay: Long,
    ) : WindowRule
}

/**
 * How much of the record a pile of meals actually is.
 *
 * A property of the READ and not of any stretch, which is why it travels beside the meals rather
 * than inside them. Nothing on screen reads a whole lifetime of meals to draw one day — the day
 * screen reads a handful of days around the one it is showing — and a walk that does not know where
 * its list was cut will mistake the cut for the end of the eating.
 *
 * That mistake is the one D4 forbids by name. Take a 16/8 with breakfast at 08:00 and dinner at
 * 20:00 every day: every gap is twelve hours, under the sixteen-hour fast, so the whole record is
 * ONE stretch that never closes. Read five days of it and the last input in the five looks final,
 * the fast looks long since completed, and the screen would state a span that is an artefact of
 * where the read stopped rather than a measurement of the eating.
 */
sealed interface MealRead {

    /**
     * The meals handed over are every meal there is, so nothing is cut at either end.
     *
     * Said by a caller that holds the whole record for the question it is asking, and by nothing
     * else: it switches the boundary off, and a caller that says it falsely gets exactly the false
     * "closed" this type exists to prevent.
     */
    data object Whole : MealRead

    /**
     * Every meal on [fromEpochDay] to [throughEpochDay] inclusive, in the zone of the walk, and
     * nothing outside those days — whether or not any particular day in them held anything.
     */
    data class OverDays(val fromEpochDay: Long, val throughEpochDay: Long) : MealRead
}

/**
 * What the rule in force makes of a set of meals: a verdict per stretch, and the meals it could not
 * time.
 *
 * The untimed count rides along rather than being asked for separately, for the same reason
 * [DayVerdict.mealsUntimed] does: the screen says "2 meals with no time" beside the window's line,
 * and the two numbers have to have come from one reading of the same meals or they can disagree.
 *
 * @property verdicts oldest stretch first. Stretches the rule may not judge are here too — the day
 *   screen has to be able to show them — with [StretchVerdict.judged] and [StretchVerdict.kept]
 *   false.
 * @property mealsUntimed meals with no trustworthy hour, excluded before any stretch was worked out
 *   (design §2.3). Not gated: a meal is untimed whatever rule was in force.
 */
data class MeasuredStretches(
    val verdicts: List<StretchVerdict>,
    val mealsUntimed: Int,
)

/**
 * The one place in the app that decides whether a day is governed, and by what.
 *
 * Both kinds go through the same gate, which is what makes "a window never applies backwards" a
 * property of the code rather than a habit of two call sites. It matters more for the measured kind
 * than for the fixed one: everything needed to score last month is already in the record, and
 * nothing but the decision not to prevents it.
 */
object WindowRules {

    /**
     * The newest rule that had already begun, or none.
     *
     * Generic in the day selector so that [EatingWindows.inForceOn] can delegate to exactly this
     * code over its own list rather than keeping a second copy of the same two lines.
     */
    fun <T> inForceOn(rules: List<T>, epochDay: Long, from: (T) -> Long): T? = rules
        .filter { from(it) <= epochDay }
        .maxByOrNull { from(it) }

    /** The overload every caller in the app actually uses. */
    fun inForceOn(rules: List<WindowRule>, epochDay: Long): WindowRule? =
        inForceOn(rules, epochDay, WindowRule::fromEpochDay)

    /**
     * How one day stood against the rule that governed it, or null if none did.
     *
     * Null rather than "not kept": a day no rule governed has no verdict at all, which is the same
     * nothing the fixed window has always returned.
     *
     * The two kinds are shown different meals on purpose, and the difference is the whole of what
     * changed here:
     *
     * - **The fixed hours are a statement about ONE calendar day**, so the count is taken over the
     *   meals filed against [epochDay] and nothing else. The filter is what lets a caller hand this
     *   a wider range without the fixed count quietly swelling.
     * - **A ratio is a statement about hours**, so the walk is shown everything it is given and
     *   keeps the stretches that BEGAN on [epochDay] (design §3). Whether a stretch begins at all
     *   may depend on an input from the day before, so a caller that wants a true answer passes the
     *   two preceding days as well — see [stretches] for why two is enough.
     *
     * [nowMillis] is the moment the question is being asked at. A stretch is open until the fast
     * completes, so there is no verdict to give without one; the old day rule needed no such thing
     * because a calendar day was over at midnight, and that is precisely the reading this replaced.
     *
     * [read] says which days [meals] were taken from. The FIXED half ignores it completely — a
     * count over one calendar day cannot be cut short by where a read stopped, and the filter above
     * is the whole of what it needs. See [stretches] for what it buys the measured half.
     */
    fun judge(
        rules: List<WindowRule>,
        meals: List<Meal>,
        read: MealRead,
        epochDay: Long,
        zone: ZoneId,
        nowMillis: Long,
    ): DayVerdict? = when (val rule = inForceOn(rules, epochDay)) {
        null -> null

        is WindowRule.Fixed ->
            EatingWindows.score(rule.window, meals.filter { it.epochDay == epochDay }, zone)

        is WindowRule.Measured -> {
            val walked = stretches(rule, meals, read, zone, nowMillis)
            DayMeasured(
                window = rule.window,
                stretches = walked.verdicts.filter { it.stretch.startedOnEpochDay == epochDay },
                nowMillis = nowMillis,
                // The zone the walk used, for the same reason the moment is carried: the screen
                // prints these stretches as clock times, and a zone read again at the point of
                // printing could disagree with the one the days were decided in.
                zone = zone,
                // THIS day's untimed meals, not the walk's. The walk reads the days either side
                // too, so its count admitted, on today, a meal filed two days back — and since D33
                // the line says "tap its time to set it", pointing at a "--:--" that is not on this
                // page. Same predicate as the walk, so nothing is admitted that the walk kept.
                mealsUntimed = meals.count { it.epochDay == epochDay && !hasItsOwnTime(it, zone) },
            )
        }
    }

    /**
     * The eating stretches in a set of meals, each with the verdict a given moment makes of it.
     *
     * The gated route, and the only one the app may use. [EatingStretches.of] works out what
     * happened; this decides what the rule in force is allowed to say about it.
     *
     * **A stretch is judged only if it BEGAN on or after the day the rule began** (design §2.4). A
     * stretch that started earlier still comes back — the day screen has to be able to show it —
     * but with [StretchVerdict.judged] and [StretchVerdict.kept] forced false, so nothing already
     * logged acquires a mark from a decision made afterwards. Working out that a stretch begins at
     * all may read an input from before that day: that is reading the record, not judging it, and
     * the input so read is never itself judged either.
     *
     * **How far back the caller must read.** To know whether the first input of a range opens a
     * stretch or continues one, the walk needs the input before it, so [meals] should reach two
     * calendar days further back than the range being shown. Two is enough, and more cannot change
     * an answer: a fast is at most twenty-three hours ([MeasuredWindow] requires it), so an input
     * more than twenty-three hours before the first input of the range has already closed whatever
     * stretch it was in and cannot belong to the same one. Reading from midnight one calendar day
     * back already covers more than twenty-three hours, whatever the hour of that first input; the
     * second day is margin.
     *
     * **And what the caller must admit it did NOT read.** [read] is that admission, and it is the
     * second gate. A walk shown a slice of the record can only see as far as the slice goes, so a
     * stretch whose last input is near the end of it may have gone on past the cut — and asking
     * `nowMillis`, which may be months later, would answer "long since closed" about eating the
     * walk never saw the end of. The span that came out would be an artefact of where the read
     * stopped rather than a measurement, which is the one thing D4 forbids.
     *
     * So every stretch is judged **at the last moment the record is known complete to** — the
     * earlier of [nowMillis] and the end of the read's last day — rather than at [nowMillis]
     * itself. That is exact rather than cautious: every input that could have continued a stretch
     * falls within one fast of its last input, so a stretch whose fast completed before the read
     * ended is provably over, and one whose fast had not is provably unknown. The unknown one comes
     * back OPEN, which withholds the span, says the still-open sentence, and keeps it out of both
     * halves of [stretchesKept] — the same nothing a genuinely open stretch gets, for the same
     * reason.
     *
     * The mirror holds at the other end and is why the lead-in above is a requirement rather than
     * advice. A stretch whose first input is less than one fast after the read begins may have
     * begun before it, so its start day and its span are the read's shape and not the owner's; it
     * is not judged either. The lead-in is what makes that harmless in practice: two calendar days
     * is more than any fast, so nothing the rule may judge is ever cut at the start.
     */
    fun stretches(
        rule: WindowRule.Measured,
        meals: List<Meal>,
        read: MealRead,
        zone: ZoneId,
        nowMillis: Long,
    ): MeasuredStretches {
        val walk = EatingStretches.of(rule.window, meals, zone)
        val fast = rule.window.fastingHours.toLong() * 60L * 60L * 1000L

        val readBegan = (read as? MealRead.OverDays)?.let { midnightOn(it.fromEpochDay, zone) }
        val readEnded = (read as? MealRead.OverDays)
            ?.let { midnightOn(it.throughEpochDay + 1, zone) }

        // The moment the record stops being known, which is the moment every stretch is judged at.
        val knownThrough = readEnded?.let { minOf(nowMillis, it) } ?: nowMillis

        return MeasuredStretches(
            verdicts = walk.stretches.map { stretch ->
                val verdict = stretch.at(rule.window, knownThrough)
                val cutAtTheStart = readBegan != null && stretch.startedAtMillis - readBegan < fast
                val mayJudge = stretch.startedOnEpochDay >= rule.fromEpochDay && !cutAtTheStart
                verdict.copy(
                    judged = verdict.judged && mayJudge,
                    kept = verdict.kept && mayJudge,
                    // Said out loud rather than only folded into the gate above, because a caller
                    // that means to state how long the eating has gone on needs to know whether it
                    // is looking at the stretch's beginning or at the edge of its own read.
                    startKnown = !cutAtTheStart,
                )
            },
            mealsUntimed = walk.mealsUntimed,
        )
    }

    /** Midnight opening [epochDay], in the zone the walk is being made in. */
    private fun midnightOn(epochDay: Long, zone: ZoneId): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * How many stretches were kept, and how many were judged at all (design §3.1).
     *
     * The same fraction [daysKept] gives, counted in the unit the measured window now works in. An
     * open stretch raises neither half, and neither does one the rule may not judge, so the count on
     * screen never reads as a failure for something the app declined to have an opinion about.
     *
     * A stretch cut short by the end of [read] is in neither half either, for exactly the reason an
     * open one is not: the walk has not seen it finish. Counting it would put a verdict on screen
     * that the read invented.
     */
    fun stretchesKept(
        rule: WindowRule.Measured,
        meals: List<Meal>,
        read: MealRead,
        zone: ZoneId,
        nowMillis: Long,
    ): Pair<Int, Int> {
        val judged = stretches(rule, meals, read, zone, nowMillis).verdicts.filter { it.judged }
        return judged.count { it.kept } to judged.size
    }

    /**
     * How many days were kept, and how many were judged at all, counted from the record.
     *
     * **The fixed window's tally.** It is counted by the calendar day because that is what the
     * fixed kind IS: "eat between 06:00 and 20:00" is a statement about a day, and it keeps its day
     * verdict, its day mark and its day tally (design §4). A ratio no longer has a day tally at all
     * — [stretchesKept] is its answer, in the unit it now judges in.
     *
     * A history can still hold both kinds, because the owner may have switched, and every day is
     * scored by the rule that actually governed it. A measured day reached this way is shown only
     * its own meals, so it contributes the stretches that both began AND ended inside it; that is
     * a narrower reading than the day screen's, and it is the reason the screen does not use this.
     *
     * **Why each day is handed over as [MealRead.Whole] and not as the one day it is.** Saying
     * "these are the meals of day D and nothing else" would make every stretch here cut at both
     * ends — a sixteen-hour fast cannot complete inside the day it started in — and the measured
     * half of a mixed fortnight would stop contributing anything at all. Saying [MealRead.Whole]
     * keeps this function's day-sliced reading exactly as it has always been, no wider and no
     * narrower.
     *
     * **And that reading is narrower than the record, which is why nothing in the app reaches it.**
     * Eating that begins at 20:00 and runs to noon the next day is one broken sixteen-hour stretch;
     * read a day at a time it is two kept short ones, and the difference flatters. So the measured
     * branch here is unreachable in production, outright: the day screen and the settings tally
     * clamp their range with `maxOf(inForce.fromEpochDay, ...)`, and the weekly compliment now
     * clamps the same way — which it did not, and that was a defect, because a week the owner
     * switched kinds in was counted seven days back and scored its ratio days exactly like this.
     *
     * The branch stays because a `when` over the two kinds has to be exhaustive, and it stays
     * honest rather than argued for: `WindowRulesTest` pins what it does AND how far it is from the
     * record, in one place, so a future caller cannot reach for it without reading that. The
     * ratio's own tally is [stretchesKept], which is the one both screens draw and the one the cut
     * rule guards; this is the fixed window's tally.
     *
     * A day nothing judged raises neither half of the fraction, so the count on screen never reads
     * as a failure for a day the app declined to have an opinion about.
     */
    fun daysKept(
        rules: List<WindowRule>,
        mealsByDay: Map<Long, List<Meal>>,
        fromEpochDay: Long,
        toEpochDay: Long,
        zone: ZoneId,
        nowMillis: Long,
    ): Pair<Int, Int> {
        var kept = 0
        var judged = 0

        (fromEpochDay..toEpochDay).forEach { day ->
            val verdict = judge(
                rules = rules,
                meals = mealsByDay[day].orEmpty(),
                read = MealRead.Whole,
                epochDay = day,
                zone = zone,
                nowMillis = nowMillis,
            ) ?: return@forEach
            if (!verdict.judged) return@forEach
            judged++
            if (verdict.kept) kept++
        }

        return kept to judged
    }
}
