package com.metaself.app.ui.day

import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.PartOfTheClock
import java.util.Locale

/**
 * The words a part of the clock is drawn with on the day (D51).
 *
 * Pure and in `ui/`, the division this project has used since the version marker: the domain
 * produces the grouping and the numbers, and never reaches for a phrase. The names of the parts
 * themselves are NOT here — they are `strings.xml` resources handed in by the screen, exactly as
 * [DayTotalsWording.itemNumbers] takes its portion words, because a name that is going to be
 * translated cannot be a constant in a Kotlin object (#9).
 */
object DayPartWording {

    /**
     * The band of the clock a part covers — "12:00–17:59".
     *
     * **The last minute, never the first minute of the next part.** An earlier draft wrote the
     * friendlier "12:00–18:00", which is ambiguous at exactly the boundary the row exists to make
     * unambiguous: 18:00 is Evening. A range printed to make a rule followable must not need a
     * footnote.
     *
     * Read back out of the enum rather than written down again here, so the hours the owner is
     * asked to follow and the hours the grouping actually uses cannot drift apart.
     *
     * An en dash, not a hyphen: it is a range, and it is the mark a range takes.
     */
    fun hours(part: PartOfTheClock): String = String.format(
        Locale.US,
        "%02d:00–%02d:59",
        part.firstHour,
        part.lastHour,
    )

    /**
     * The row's heading — "Midday · 12:00–17:59", or the untimed row's own words alone.
     *
     * [name] is the part's translated name, or the untimed row's sentence, handed in by the screen.
     *
     * The hours are on the row rather than buried in the code because that is the owner's
     * requirement, not decoration: the rule he is meant to locate a logging by has to be readable
     * on the screen it governs. The untimed row gets no hours, because it has none — that is the
     * whole reason it is a row of its own (D33).
     */
    fun heading(name: String, part: PartOfTheClock?): String =
        if (part == null) name else "$name · ${hours(part)}"

    /**
     * What is in the row, from what is already on the record.
     *
     * A meal the owner built keeps the name he gave it; anything else takes its first food's name —
     * the same fallback the time's spoken description and the time picker already use, so a row
     * never has to invent a label for itself. **More than one thing is said as a count** by
     * [size], never folded into this string: inventing "Breakfast" for two foods is the claim D51
     * refuses, and joining a name to a figure in one string is the bidirectional defect that once
     * put a calorie count in front of the food it belonged to.
     *
     * [meals] is never empty — [com.metaself.app.domain.day.DayParts] draws no empty row.
     */
    fun what(meals: List<Meal>): String = meals.first().let { first ->
        first.title ?: DayTotalsWording.itemName(first.items.first())
    }

    /**
     * How many things are behind the row.
     *
     * A thing is a row of the record as the record draws it: a meal he built is ONE thing, because
     * it has one name and opens to its parts, and everything else counts the foods it holds. So the
     * count says how much is behind the door rather than how many times he happened to press save.
     *
     * One is drawn as no count at all — [what] has already named the only thing there is.
     */
    fun size(meals: List<Meal>): Int =
        meals.sumOf { meal -> if (meal.title != null) 1 else meal.items.size }

    /**
     * What the whole row came to, in calories.
     *
     * Summed from the loggings' own rows, which is the same rule the day's total follows. **There
     * is no `Meal.kcal`**, and a meal definition's stored total is the wrong number for anything
     * logged adjusted — a row that read it would disagree with the day above it on exactly the days
     * the owner changed something.
     */
    fun total(meals: List<Meal>): String =
        DayTotalsWording.itemsTotal(meals.flatMap { it.items })
}
