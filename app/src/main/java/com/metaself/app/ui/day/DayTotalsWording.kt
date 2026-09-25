package com.metaself.app.ui.day

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.window.hasItsOwnTime
import com.metaself.app.domain.day.Remaining
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.day.Source
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * The sentences the Today screen shows.
 *
 * Pure and in `ui/`, the division this project has used since the version marker: the domain
 * produces numbers and never reaches for a phrase.
 *
 * A day in the past has nothing "left" of it — it is over. What the number means there is how far
 * under the target it ended, which is the same arithmetic and a different sentence.
 *
 * [origin] returns null for a typed item on purpose. Decision D4 says an estimate is never
 * presented as a measurement; the corollary is that a measurement should not be dressed up as
 * anything at all. A badge saying "typed by you" on every one of the owner's own entries would be
 * noise, and would make the estimate badge look like a category rather than a warning.
 */
object DayTotalsWording {

    /**
     * The biggest number on the day, in words.
     *
     * [nothingLogged] is the one case where there is no number to give. A past day with no rows is
     * not a day of fasting, it is a day with no record, and subtracting nothing from the target
     * would present the whole target as a measurement of how far under he came — an estimate dressed
     * as a measurement, which is exactly what D4 forbids. Today is unaffected: a day still in
     * progress legitimately has its whole target left.
     */
    fun headline(remaining: Remaining, isToday: Boolean, nothingLogged: Boolean = false): String = when {
        !isToday && nothingLogged -> "Nothing logged"
        remaining.overTarget -> "${grouped(remaining.kcal.absoluteValue)} kcal over"
        isToday -> "${grouped(remaining.kcal)} kcal left"
        else -> "${grouped(remaining.kcal)} kcal under"
    }

    /**
     * The same answer as [headline], as a figure with no unit attached — "1,240" (D49).
     *
     * The page sets the figure in the display face at 72 sp and the words beside it in the text
     * face at 14 sp, and two faces at two sizes cannot come out of one string. So the sentence is
     * available whole, for whoever wants it whole, and in two pieces for whoever has to set them
     * apart. Nothing is recomputed: both read the same [Remaining].
     *
     * **Null for a past day with nothing on it**, which is the one case with no figure at all. That
     * day has no number — it is the whole reason [headline] says "Nothing logged" rather than
     * presenting the untouched target as a measurement of a fast nobody recorded (D4). A dash in
     * its place would set a punctuation mark in the slot the screen reserves for its one answer,
     * which reads as a figure that failed to load; null lets the caller draw nothing and give the
     * slot to [unit]'s sentence. It is also what [origin] and [mealTitle] already do to say there
     * is nothing here.
     *
     * How far over, never a negative: the minus sign is the truth of the arithmetic and "210 over"
     * is the truth of the day — and at 72 sp a leading minus shifts the whole figure off its
     * column.
     */
    fun amount(remaining: Remaining, isToday: Boolean, nothingLogged: Boolean = false): String? = when {
        !isToday && nothingLogged -> null
        remaining.overTarget -> grouped(remaining.kcal.absoluteValue)
        else -> grouped(remaining.kcal)
    }

    /**
     * What the figure means, without repeating it — "kcal left", "kcal over", "kcal under" (D49).
     *
     * The empty past day has no figure for a unit to belong to, so the unit stops being a unit and
     * becomes the whole sentence: "Nothing logged". Today is unaffected, exactly as in [headline] —
     * a day still running legitimately has its whole target left.
     */
    fun unit(remaining: Remaining, isToday: Boolean, nothingLogged: Boolean = false): String = when {
        !isToday && nothingLogged -> "Nothing logged"
        remaining.overTarget -> "kcal over"
        isToday -> "kcal left"
        else -> "kcal under"
    }

    /**
     * "of 2,090" — the denominator, so the proportion drawn has something to be a proportion of.
     *
     * **No second "kcal".** The line read "1,240 kcal left of 2,090 kcal", which wrapped on a phone
     * for no better reason than saying the unit twice — once where it belongs and once where it is
     * redundant. Dropping it is not a saving of five characters; it is what keeps the line on one
     * line (D49).
     */
    fun ofTarget(target: DailyTarget): String = "of ${grouped(target.kcal)}"

    /**
     * One bar per macro, labelled short.
     *
     * The bars sit side by side in a third of the width each, where "Protein 149 g left" wraps onto
     * three lines and costs more room than the sentence it replaced. "P 149 g" fits, and the bar
     * beneath it carries the rest of the meaning.
     */
    fun macroBars(target: DailyTarget, remaining: Remaining): List<MacroBarModel> = listOf(
        bar("P", target.macros.proteinG, remaining.proteinG),
        bar("C", target.macros.carbsG, remaining.carbsG),
        bar("F", target.macros.fatG, remaining.fatG),
    )

    private fun bar(initial: String, targetG: Int, remainingG: Int): MacroBarModel {
        val eaten = targetG - remainingG
        return MacroBarModel(
            kicker = initial,
            figure = if (remainingG < 0) {
                "${remainingG.absoluteValue} g over"
            } else {
                "$remainingG g"
            },
            fractionEaten = if (targetG <= 0) 0f else (eaten.toFloat() / targetG).coerceIn(0f, 1f),
            overTarget = remainingG < 0,
        )
    }

    fun macros(remaining: Remaining): List<String> = listOf(
        macro("Protein", remaining.proteinG),
        macro("Carbs", remaining.carbsG),
        macro("Fat", remaining.fatG),
    )

    /**
     * The name alone — the food's CURRENT name, not the one typed on the day.
     *
     * Renaming a food re-labels every day it was ever eaten. The name as typed is still on the row
     * and still in every export, so nothing is lost; it is simply not what a screen showing today's
     * food list should be calling today's food. A row attached to no food falls back to what was
     * typed, which is every row logged before foods existed.
     *
     * Deliberately NOT joined to the numbers. A Hebrew name and a Latin figure in one string are two
     * runs of opposite direction, and the bidirectional algorithm is entitled to reorder them:
     * joined into one string, a row's calorie count can be dragged in front of its own name. Two
     * separate pieces of text cannot be interleaved by anything, so they are never joined.
     */
    fun itemName(item: FoodItem): String = item.label

    /**
     * A logged meal's own line: the name the owner gave the meal he built it from.
     *
     * Null for everything else, which is the ordinary case — typed, described, scanned, or one
     * food on its own. Those have no name, and inventing one for a row is the thing this app has
     * refused to do since the derived meal list went.
     *
     * Read through the pointer, so renaming the meal retitles every day it was eaten.
     */
    fun mealTitle(meal: Meal): String? = meal.title

    /**
     * When a meal was eaten, "09:30" — or null when that is not known (D33).
     *
     * The stored moment is when the meal was LOGGED, which is when it was eaten only if it was
     * logged on its own day. Something written down the next morning onto yesterday carries the next
     * morning: another date, and not a time on this day at all. Showing it would state a time he did
     * not eat at, so it is shown as not known — the same test, date against day, that the eating
     * window uses to leave such a meal out.
     */
    fun eatenAt(meal: Meal, zone: ZoneId): String? {
        if (!hasItsOwnTime(meal, zone)) return null
        val at = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone)
        return String.format(Locale.US, "%02d:%02d", at.hour, at.minute)
    }

    /**
     * What a screen reader hears for a meal's time, naming the meal.
     *
     * Two meals at 12:00 must not be two controls both called "12:00" — the agent walk of
     * 2026-09-17 could not tell eight identically named fields apart, and neither can anyone reading
     * the screen rather than seeing it (issue #12).
     */
    fun eatenAtDescription(meal: Meal, zone: ZoneId): String {
        val what = meal.title ?: itemName(meal.items.first())
        val time = eatenAt(meal, zone) ?: return "Time not known: $what. Tap to set it."
        return "Eaten at $time: $what. Tap to change the time."
    }

    /** Refused: a time that has not happened yet would hold a fast open that has closed (D33). */
    fun laterThanNow(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour, minute) +
            " has not happened yet. A meal can only be put at a time already past."

    /** What the whole meal came to, summed from its own rows rather than from any definition. */
    fun mealNumbers(meal: Meal): String = "${grouped(meal.items.sumOf { it.kcal })} kcal · " +
        "P ${meal.items.sumOf { it.proteinG }} · " +
        "C ${meal.items.sumOf { it.carbsG }} · " +
        "F ${meal.items.sumOf { it.fatG }}"

    /**
     * How many things are under a collapsed meal, so the row says what opening it would show.
     *
     * A title with a number beside it is a row somebody can decide about; a bare title is one they
     * have to open to find out.
     */
    fun mealSize(meal: Meal): String = when (meal.items.size) {
        1 -> "1 thing"
        else -> "${meal.items.size} things"
    }

    /**
     * What a handful of rows comes to, in calories.
     *
     * Summed from the rows themselves rather than from any definition, which is the same rule the
     * day's own total follows: a stored total would be a second answer that could disagree with its
     * own parts.
     */
    fun itemsTotal(items: List<FoodItem>): String = "${grouped(items.sumOf { it.kcal })} kcal"

    /**
     * The numbers alone, and the portion with them when there is one.
     *
     * [portion] is a parameter because the right words for it are not always the stored ones: a row
     * in the app's own "portion" stored "2 portion", and the plural is chosen on the screen, where
     * the plural resources are (`ui/portion/PortionWords.kt`, D37). It has no default on purpose: a
     * default of the stored words would be a silent way back to "2 portion" for any caller that
     * forgot to pass it. For every other unit the caller passes the words as stored.
     */
    fun itemNumbers(item: FoodItem, portion: String?): String = numbers(item, portion, ABOUT_MARK)

    /**
     * [itemNumbers] as a screen reader should say it: the mark read as *about*, since "≈" is read
     * as a symbol's name or not at all (D7a as amended by D58).
     */
    fun itemNumbersSpoken(item: FoodItem, portion: String?): String = numbers(item, portion, ABOUT_SPOKEN)

    /**
     * Whether the row's amount is marked as the model's estimate (D7a as amended by D58, §12.9):
     * the row's source is the model's, and its words begin with an amount.
     *
     * Read from the source every row already stores, so no row needs anything new. A row whose
     * amount he typed over a model's figures is marked too: the amount has no source of its own
     * (D53 §3), and the row's figures are still an estimate.
     */
    fun amountEstimated(item: FoodItem, portion: String?): Boolean =
        item.source == Source.AI_ESTIMATE && portion?.trimStart()?.firstOrNull()?.isDigit() == true

    private fun numbers(item: FoodItem, portion: String?, mark: String): String {
        val figures = "${grouped(item.kcal)} kcal · P ${item.proteinG} · " +
            "C ${item.carbsG} · F ${item.fatG}"
        val words = portion?.takeIf { it.isNotBlank() } ?: return figures
        val marked = if (amountEstimated(item, words)) mark + words.trimStart() else words
        return "$figures · $marked"
    }

    /** Before an amount the model estimated, on the day (D7a as amended by D58). */
    const val ABOUT_MARK = "≈"

    private const val ABOUT_SPOKEN = "about "

    /**
     * Where a number came from, for the screen that ASKS him to accept it.
     *
     * Deliberately not on the day's list any more. Decision D4 is about what the record stores and
     * D7 about a low guess not passing for a certain number — neither asks for a line of provenance
     * under every row of a daily list, and printing one there was transparency applied where it was
     * not wanted: he opens that screen to see what he has eaten, not to audit it.
     *
     * It still appears on the proposal screen, which is the moment it matters — when he is deciding
     * whether to accept a figure — and the source itself is still on the record, in the editor and
     * in every export.
     */
    fun origin(item: FoodItem): String? = when (item.source) {
        Source.TYPED -> null
        Source.AI_ESTIMATE -> "Estimated — ${confidenceWords(item.confidence)} confidence"
        // NOT "repeated from an earlier meal". That the owner ate something before is a fact
        // about his habits, not about the number, and it was displacing the fact that IS about the
        // number: a repeat keeps whatever confidence its original carried, so a repeated estimate
        // was hiding that it was an estimate at all. It now says what the figures actually are, and
        // an originally-typed repeat says nothing, exactly as a typed item does. So does a row
        // logged from a food whose figure was worked back from a scanned row (D43, #29): that
        // figure is filed as copied, with no confidence, because the row it came from was rounded
        // and is no longer the packet's statement — saying nothing here is the honest reading, not
        // a lost "From the package label".
        Source.REPEATED -> item.confidence?.let { "Estimated — ${confidenceWords(it)} confidence" }
        Source.LABEL -> "From the package label"
        Source.UNRECOGNISED -> "Origin not recognised by this version"
    }

    private fun macro(name: String, grams: Int): String = if (grams < 0) {
        "$name ${grams.absoluteValue} g over"
    } else {
        "$name $grams g left"
    }

    private fun confidenceWords(confidence: Confidence?): String = when (confidence) {
        Confidence.LOW -> "low"
        Confidence.MEDIUM -> "moderate"
        Confidence.HIGH -> "high"
        null -> "unstated"
    }

    private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)
}

/**
 * A macro, ready to draw: its kicker, its figure, the fraction, and whether it has been passed.
 *
 * Two pieces rather than one sentence, for the reason [DayTotalsWording.amount] and
 * [DayTotalsWording.unit] are two pieces: the column sets the kicker in the text face at 13 sp and
 * the figure in the display face at 28 sp (D49). [label] puts them back together for anything that
 * draws the macro on one line, and is the form the shortness rule is expressed in — a third of a
 * phone's width, where "Protein 149 g left" wrapped onto three lines.
 */
data class MacroBarModel(
    val kicker: String,
    val figure: String,
    val fractionEaten: Float,
    val overTarget: Boolean,
) {
    val label: String get() = "$kicker $figure"
}
