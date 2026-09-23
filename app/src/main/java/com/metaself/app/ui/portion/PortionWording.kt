package com.metaself.app.ui.portion

import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Logging
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.portion.Portions
import java.util.Locale

/**
 * The one rule for a count of the app's own "portion", for every screen that draws one: a day's
 * rows and its naming sheet, a saved meal's parts on Add something, the builder's parts, the
 * Just-for-today adjuster, and the model's proposal (D37, extended by #27).
 *
 * Pure and in `ui/`, the division this project has used since the version marker: the domain
 * produces numbers and never reaches for a phrase. One object rather than a copy per screen,
 * because five places choosing the plural separately are five chances for one of them to drift
 * back to "2 portion". The plural resource itself is reached only in `PortionWords.kt`.
 */
object PortionWording {

    /**
     * The amount when it is counted in the app's own "portion", asked of the numbers alone, or null.
     *
     * "Portion" is the one unit the app named itself — its word for one of a food when nothing said
     * what one is — so it is the one unit whose plural the app knows, whoever put it on the row, the
     * model included. "Slice" came from the model and "bar" from the owner; the app cannot know
     * another word's plural, least of all a Hebrew one, so those are drawn exactly as written (D37).
     *
     * On its own this is the whole question only where no words are held — a meal's part, whose
     * words the screen builds from these same numbers. Held words go through [countedInPortions].
     */
    fun inAppsOwnPortion(amount: Double, unit: String): Double? =
        amount.takeIf { it > 0.0 && unit.trim().lowercase(Locale.ROOT) == FoodFacts.PORTION }

    /**
     * The amount of words someone wrote — a day's row, the model's answer — when they are counted in
     * the app's own "portion", or null.
     *
     * **Only when the words are the app's own form of that amount and unit** — what
     * [Portions.words] writes, "2 portion". Every writer today writes exactly that, but it is a fact
     * about today's writers, not a guarantee about every row: words recovered from older text, or
     * written any other way, may say more than the amount and unit do ("2 portion (large)"), and
     * rebuilding them from the numbers would silently drop that. The words are what is displayed
     * (D5), so words that are anything else are drawn exactly as written.
     *
     * Takes the three fields rather than a row so the model's proposal can be asked without being
     * turned into a row first.
     */
    fun countedInPortions(amount: Double, unit: String, words: String?): Double? {
        val counted = inAppsOwnPortion(amount, unit) ?: return null
        val held = words?.trim() ?: return null
        return counted.takeIf { held.equals(Portions.words(amount, unit.trim()), ignoreCase = true) }
    }

    /**
     * The whole number a plural form is chosen by: the amount itself when it is whole, 0 when it is
     * not.
     *
     * Android chooses a plural form from a whole number only. 0 takes English's "other" form, which
     * is what "1.5 portions" needs; the one form a part-portion must never get is "one".
     * Hebrew's own rule for fractions cannot be said through this API; the translation (#9) is where
     * that is revisited.
     */
    fun pluralQuantity(amount: Double): Int =
        if (amount % 1.0 == 0.0) amount.toInt() else 0

    /**
     * The unit a meal's part is counted in, as the screen draws it.
     *
     * The same unit logging puts on the day's row when the meal is logged — grams, the food's own
     * named unit, or the app's fallback "portion" for a food that knows only 100 g and a weight — so
     * a part reads as the row it will become. Written once here because Add something and the
     * builder each had their own copy, and two copies of one expression are how two screens come to
     * disagree.
     */
    fun unitOf(component: MealComponent): String =
        if (component.countedAs == CountedAs.GRAMS) {
            Logging.GRAMS_UNIT
        } else {
            component.food.facts.perUnit?.unitName ?: FoodFacts.PORTION
        }
}
