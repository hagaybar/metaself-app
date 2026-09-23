package com.metaself.app.ui.food

import com.metaself.app.domain.food.FoodRetaught
import com.metaself.app.domain.food.Replaced
import com.metaself.app.domain.food.ReplacedFacts

/**
 * Why the app is saying this, which decides the sentence's closing clause.
 *
 * An argument with no default, so every call site states which it is rather than inheriting a
 * story about how the figures got there.
 */
enum class RetaughtBecause {
    /** He has just logged something. */
    JUST_LOGGED,

    /** He has just typed the numbers into *make a food*, inside the meal builder. */
    JUST_TYPED,
}

/**
 * What the app says when logging or typing replaces a figure a food already held (issue #13, D45).
 *
 * Shaped like `RevisionWording`: data in, one `String?` out, null when there is nothing to say, and
 * its English in Kotlin rather than `strings.xml` because the sentence is built from data — the
 * house pattern. The only thing in `strings.xml` is the button label the composable draws.
 *
 * A food is named once however many of its facts moved: the list handed in has already been
 * collapsed to one entry per food (`ReplacedFacts.collapse`), so a double line is not something a
 * caller here can reintroduce. Several foods get one line each, and there is no cap on how many —
 * a cap would hide a change, which is the defect this exists to close.
 */
object RetaughtWording {

    fun notice(retaught: List<FoodRetaught>, because: RetaughtBecause): String? =
        retaught
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { food -> sentence(food, because) }

    private fun sentence(food: FoodRetaught, because: RetaughtBecause): String {
        val phrases = food.replaced.joinToString(", and ") { phrase(it) }
        return "“${food.foodName}” now $phrases, ${closing(because)}"
    }

    private fun closing(because: RetaughtBecause): String = when (because) {
        RetaughtBecause.JUST_LOGGED -> "because you have just logged it with different numbers."
        RetaughtBecause.JUST_TYPED -> "because you have just typed different numbers for it."
    }

    /**
     * One fact, said in the way the food list would have said it.
     *
     * The "the same … from different figures" arms exist so the notice can never read "from 15 to
     * 15": the macros can move with the calories standing still, and two calorie figures a tenth
     * apart round to one printed number.
     */
    private fun phrase(replaced: Replaced): String = when (replaced) {
        is Replaced.Per100g -> {
            val held = FoodWording.grouped(replaced.before.nutrients.kcal)
            val holds = FoodWording.grouped(replaced.after.nutrients.kcal)
            if (held == holds) {
                "counts the same $holds kcal per 100 g from different figures"
            } else {
                "counts $holds kcal per 100 g, where it counted $held"
            }
        }

        is Replaced.PerOne -> {
            val held = FoodWording.grouped(replaced.before.nutrients.kcal)
            val holds = FoodWording.grouped(replaced.after.nutrients.kcal)
            val heldUnit = replaced.before.unitName
            val holdsUnit = replaced.after.unitName
            when {
                // What one of it IS has changed, so both sides say which "one" they mean. Compared
                // by `ReplacedFacts`' own rule, so a unit that only changed case is not introduced
                // here as a difference the comparison already decided was none.
                !ReplacedFacts.sameUnitName(heldUnit, holdsUnit) ->
                    "counts $holds kcal per $holdsUnit, where it counted $held per $heldUnit"
                held == holds -> "counts the same $holds kcal per $holdsUnit from different figures"
                else -> "counts $holds kcal per $holdsUnit, where it counted $held"
            }
        }

        is Replaced.WhatOneWeighs -> {
            val held = FoodWording.grouped(replaced.before.grams)
            val holds = FoodWording.grouped(replaced.after.grams)
            if (held == holds) {
                "says one ${replaced.unitName} weighs the same $holds g from a different figure"
            } else {
                "says one ${replaced.unitName} weighs $holds g, where it said $held"
            }
        }
    }
}
