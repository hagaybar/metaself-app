package com.metaself.app.domain.day

import com.metaself.app.domain.window.hasItsOwnTime
import java.time.Instant
import java.time.ZoneId

/**
 * The four fixed parts of the clock a day's loggings are gathered into (D51).
 *
 * **These are parts of the clock, not names for meals.** "Morning" is true by definition of the
 * hour; "Breakfast" would be a claim about what the eating WAS — an inference printed as a fact,
 * which is D4's defect, and wrong on exactly the days an eating window puts a first meal at
 * eleven. There is no Breakfast, Lunch or Dinner anywhere in this app and nothing here may
 * introduce one.
 *
 * The bands tile the clock exactly: every hour of the day belongs to one part and no hour belongs
 * to two. That is what makes "every logging falls into exactly one" true by construction rather
 * than by inspection — a gap would drop a logging nowhere, an overlap would draw one twice.
 *
 * The boundaries are named here rather than written into the grouping, because D51 says changing
 * them changes a view and loses nothing: they are read back out to print the hours on the row, so
 * the rule the owner is asked to follow and the rule the code runs are the same two numbers.
 *
 * **Declared in clock order, Night first, though D51's table lists Night last.** The table reads in
 * the order a person describes a day; the screen runs 00:00 to 23:59. The parts come out of
 * [DayParts.of] in this declaration order, so drawing Night last would make it the one row where a
 * later logging sits above an earlier one — a 02:00 logging beneath a 23:00 one — which is the rule
 * breaking itself on the very screen that prints the hours.
 *
 * @property firstHour the first hour of the day, 0-23, that falls in this part.
 * @property lastHour the last hour, INCLUSIVE — so Midday's last hour is 17, and 18:00 is Evening.
 */
enum class PartOfTheClock(val firstHour: Int, val lastHour: Int) {
    NIGHT(0, 5),
    MORNING(6, 11),
    MIDDAY(12, 17),
    EVENING(18, 23),
}

/**
 * One row of the day: a part of the clock, and the loggings that fall in it.
 *
 * **[part] is null for the loggings that have no honest time**, and that is deliberately a null
 * rather than a fifth constant on [PartOfTheClock]. A fifth constant would have to carry a pair of
 * hours like the other four, and there are no hours it could honestly carry — the whole reason
 * those loggings are here is that the moment stored against them is the moment of WRITING, on
 * another date, and says nothing about when they were eaten (D33). Giving it hours would be the
 * invention D51 refuses; giving it hours of 0-23 would overlap all four and break the tiling the
 * enum exists to state. It would also quietly join the enum's iteration order, so every place that
 * walks the four parts — the drawing, the hours printed on a row — would have to remember to skip
 * it, and the one that forgot would print a band of hours for a row that has none.
 *
 * Null says exactly what is true: this row belongs to no part of the clock.
 *
 * [meals] is never empty. A part with nothing in it is absent from the list rather than present and
 * empty, because an empty row is a row the owner has to read and discard.
 */
data class DayPart(val part: PartOfTheClock?, val meals: List<Meal>)

/**
 * Gathering a day's loggings into the rows the day screen draws (D51).
 *
 * The rule is one anybody can run in their head: you know when you ate a thing, so you know which
 * row holds it. No arithmetic, no dependence on what else was logged that day, and no dependence
 * on the order it was logged in. That is why the gap rule this replaces had to go — it
 * grouped loggings within 90 minutes of each other, so its row count was unbounded and locating
 * anything meant remembering every logging's time and computing the gaps between them.
 *
 * **Nothing here is stored.** The grouping is computed from each [Meal]'s `loggedAtMillis` read in
 * the day's own zone, so it cannot drift out of step with the record and no migration is owed.
 */
object DayParts {

    /**
     * The day's rows: the parts of the clock that hold something, in clock order, then the untimed
     * row if there is one.
     *
     * [zone] is the day's zone and is passed rather than read from the system, for the reason every
     * other time in this app is: one instant read in two places is two clock readings, and a row
     * that said Midday on a phone that had just flown somewhere would be the app disagreeing with
     * the clock the owner is reading it by.
     *
     * Loggings inside a timed part are ordered by their stored moment, computed here rather than
     * inherited from whatever order the store handed over — otherwise the row would re-order itself
     * the next time a query changed. The untimed row keeps the order it was given, because those
     * loggings have no clock reading to be sorted by and any order imposed would be invented.
     */
    fun of(meals: List<Meal>, zone: ZoneId): List<DayPart> {
        // The date test, not a time test: a moment on another date — later OR earlier — is the
        // moment of WRITING and not a time on this day at all. The same predicate the eating window
        // and the time shown beside a meal already use, so the three cannot disagree about which
        // loggings those are.
        val (timed, untimed) = meals.partition { hasItsOwnTime(it, zone) }

        val byPart = timed.groupBy { meal ->
            val hour = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone).hour
            // Every hour of the clock is in exactly one band, so this always finds one. `single`
            // rather than `first` on purpose: were a boundary ever edited into a gap or an overlap,
            // this throws where the defect is instead of silently filing by whichever band happened
            // to be declared first.
            PartOfTheClock.entries.single { hour >= it.firstHour && hour <= it.lastHour }
        }

        // Clock order, Night first, and a part with nothing in it is simply absent.
        val rows = PartOfTheClock.entries.mapNotNull { part ->
            byPart[part]?.let { held -> DayPart(part, held.sortedBy { it.loggedAtMillis }) }
        }

        // Always last, and the one case that exceeds D51's cap of four. That is deliberate: it is
        // the only row asking to be fixed, and the colophon sends him to it. Sorting it in among the
        // parts by its stored hour would bury the one row he is being asked to act on, in a part of
        // the day it may have nothing to do with.
        return if (untimed.isEmpty()) rows else rows + DayPart(null, untimed)
    }
}
