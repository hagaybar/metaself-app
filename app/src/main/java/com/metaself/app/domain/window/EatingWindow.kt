package com.metaself.app.domain.window

import com.metaself.app.domain.day.Meal
import java.time.Instant
import java.time.ZoneId

/**
 * The hours the owner has decided to eat between.
 *
 * @property fromEpochDay the first day it governs. **A window never applies backwards.** A rule
 *   invented on Thursday does not get to judge Tuesday: nothing already logged is re-scored, no past
 *   day acquires a mark, and no count is retroactively broken by a decision made after the fact.
 *   That constraint is the whole design.
 */
data class EatingWindow(
    val startHour: Int,
    val endHour: Int,
    val fromEpochDay: Long,
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { "an hour is between 0 and 23" }
    }

    /**
     * Whether an hour falls inside.
     *
     * **The end hour is a DEADLINE, not a last permitted hour.** "Until 20:00" means the window is
     * shut at 20:00, so twenty past eight in the evening is outside it. The first version read the
     * end inclusively, which quietly turned "until 20:00" into "until 20:59" — the mark was still
     * showing open after the window had shut, and it should not have been.
     *
     * The app knows hours and not minutes, so an end of 20 is the moment 20:00 arrives. That is the
     * reading a person gives "last meal until eight", and the one that cannot be argued with at
     * twenty past.
     *
     * A window whose end is not after its start runs past midnight — eating between two in the
     * afternoon and one in the morning is a perfectly ordinary fast. Which day such a meal belongs
     * to is not this function's problem: the meal already carries the day it was logged against.
     */
    fun contains(hour: Int): Boolean = if (startHour <= endHour) {
        hour >= startHour && hour < endHour
    } else {
        hour >= startHour || hour < endHour
    }
}

/**
 * What a day did about the window that governed it: the COUNT verdict.
 *
 * One of the two shapes a verdict takes (D29). The other is a span, and the two are kept apart by
 * [DayVerdict] so that neither can be read as the other.
 */
data class DayWindow(
    val window: EatingWindow,
    val mealsInside: Int,
    val mealsOutside: Int,
    /**
     * Meals that cannot be timed, and are therefore not judged.
     *
     * A meal written down on a different day from the one it belongs to has no trustworthy hour:
     * the record keeps when it was TYPED, not when it was eaten. Filling in Tuesday on Thursday is
     * a normal thing to do and must not produce a false accusation.
     */
    override val mealsUntimed: Int,
) : DayVerdict {
    override val kept: Boolean get() = mealsOutside == 0

    override val judged: Boolean get() = mealsInside + mealsOutside > 0
}

/**
 * The fixed-hours window's own scoring.
 *
 * **[inForceOn], [judge] and [daysKept] have no production caller left.** Both kinds of window now
 * go through [WindowRules], which owns the one gate; these three are kept on purpose as the proof,
 * in `EatingWindowTest`, that the fixed kind still answers exactly what it always answered. That
 * test is the regression net for the day the measured kind was added, so deleting them as dead code
 * would delete the evidence rather than tidy it. [score] is the live path: [WindowRules.judge]
 * calls it once the gate has been passed.
 */
object EatingWindows {

    /**
     * Which window governed a given day, or none.
     *
     * The most recent one that had already begun. Changing the window therefore changes it from now
     * on and leaves the old one governing the days it actually governed, so the record of whether he
     * kept to what he had decided AT THE TIME stays true.
     *
     * Kept for `EatingWindowTest` — see the note on [EatingWindows]. The app's own gate is
     * [WindowRules.inForceOn], which this delegates to.
     */
    fun inForceOn(windows: List<EatingWindow>, epochDay: Long): EatingWindow? =
        WindowRules.inForceOn(windows, epochDay, EatingWindow::fromEpochDay)

    /**
     * How one day's meals stood against the window that governed it, or null if none did.
     *
     * Kept for `EatingWindowTest` — see the note on [EatingWindows]. The app itself asks
     * [WindowRules.judge], which gates both kinds through the same code.
     */
    fun judge(
        windows: List<EatingWindow>,
        meals: List<Meal>,
        epochDay: Long,
        zone: ZoneId,
    ): DayWindow? {
        val window = inForceOn(windows, epochDay) ?: return null
        return score(window, meals, zone)
    }

    /**
     * The counting on its own, with the gate already passed.
     *
     * Split out of [judge] so that the measured kind can reuse the SAME gate instead of growing a
     * second copy of it. Not one number in here changed when it was split; [judge] is still the
     * gate followed by exactly this.
     *
     * `internal`, because it bypasses the gate by design: only [judge] and [WindowRules.judge] may
     * reach it, and both pass the gate first. The unit tests are a friend path and still see it.
     */
    internal fun score(window: EatingWindow, meals: List<Meal>, zone: ZoneId): DayWindow {
        var inside = 0
        var outside = 0
        var untimed = 0

        meals.forEach { meal ->
            val at = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone)
            if (at.toLocalDate().toEpochDay() != meal.epochDay) {
                untimed++
            } else if (window.contains(at.hour)) {
                inside++
            } else {
                outside++
            }
        }

        return DayWindow(window, mealsInside = inside, mealsOutside = outside, mealsUntimed = untimed)
    }

    /**
     * How many days were kept, counted from the record rather than stored.
     *
     * The same rule as the streak (D13): filling a day in late repairs it, and no counter can drift
     * out of step with what he actually ate. Days no window governed are not counted either way —
     * they were not days he was keeping to anything.
     *
     * Kept for `EatingWindowTest` — see the note on [EatingWindows]. The app counts with
     * [WindowRules.daysKept], which adds a stretch of fixed days and a stretch of measured ones
     * together without converting either into the other.
     */
    fun daysKept(
        windows: List<EatingWindow>,
        mealsByDay: Map<Long, List<Meal>>,
        fromEpochDay: Long,
        toEpochDay: Long,
        zone: ZoneId,
    ): Pair<Int, Int> {
        var kept = 0
        var judged = 0

        (fromEpochDay..toEpochDay).forEach { day ->
            val verdict = judge(windows, mealsByDay[day].orEmpty(), day, zone) ?: return@forEach
            if (!verdict.judged) return@forEach
            judged++
            if (verdict.kept) kept++
        }

        return kept to judged
    }
}
