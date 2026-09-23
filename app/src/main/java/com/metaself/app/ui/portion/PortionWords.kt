package com.metaself.app.ui.portion

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.metaself.app.R
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.portion.Portions

/*
 * A count of the app's own "portion", in words, with the plural when there are several (D37, #27).
 *
 * The one file that reaches the plural resource, so every screen drawing a count of portions draws
 * it the same way. The plural is chosen here, when the words are drawn, never when they are
 * stored: every row already logged reads right, and nothing stored is rewritten. The number is
 * formatted by the same call that wrote the stored words, so only the noun changes.
 */

@Composable
private fun portionsCount(amount: Double): String =
    pluralStringResource(
        R.plurals.portions_count,
        PortionWording.pluralQuantity(amount),
        Portions.format(amount),
    )

/**
 * Words someone wrote — a day's row, the model's answer — with the app's own "portion" in the
 * plural when there are several.
 *
 * Only words in the app's own form of the amount and unit are replaced
 * ([PortionWording.countedInPortions]); any other unit, or words that say anything else, are the
 * words as written.
 */
@Composable
fun portionWords(amount: Double, unit: String, words: String?): String? =
    PortionWording.countedInPortions(amount, unit, words)?.let { portionsCount(it) } ?: words

/**
 * Words that are always there — the model's proposal, whose words are never missing — through the
 * same rule, so the caller is not left with a null it can never get.
 *
 * Its own JVM name only because nullability is not part of a JVM signature: without it this and the
 * overload above compile to the same method.
 */
@Composable
@JvmName("portionWordsHeld")
fun portionWords(amount: Double, unit: String, words: String): String =
    PortionWording.countedInPortions(amount, unit, words)?.let { portionsCount(it) } ?: words

/** A day's row: its stored words, through the same rule. */
@Composable
fun portionWords(item: FoodItem): String? = portionWords(item.portionAmount, item.portionUnit, item.portion)

/**
 * A part of a meal he built.
 *
 * A part stores no words, only an amount and what it is counted in, so the words are built here
 * and are the app's own form by construction. There is nothing that could say more than the
 * numbers, so the question is asked of the numbers alone — comparing against words just built from
 * them would be a check that can only pass.
 */
@Composable
fun portionWords(component: MealComponent): String {
    val unit = PortionWording.unitOf(component)
    return PortionWording.inAppsOwnPortion(component.amount, unit)?.let { portionsCount(it) }
        ?: Portions.words(component.amount, unit)
}

/**
 * A unit on its own, beside a box that holds the number: the app's own "portion" takes its plural
 * from [amount] (D37), and any other unit is drawn exactly as written. A box holding no number yet
 * reads in the plural, as "0 portions" would.
 */
@Composable
fun unitWord(amount: Double?, unit: String): String =
    if (PortionWording.inAppsOwnPortion(1.0, unit) != null) {
        pluralStringResource(R.plurals.portions_unit, PortionWording.pluralQuantity(amount ?: 0.0))
    } else {
        unit
    }
