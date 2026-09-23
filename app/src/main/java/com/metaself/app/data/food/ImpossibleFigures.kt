package com.metaself.app.data.food

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.amount.BelievableAmount.GRAMS
import com.metaself.app.domain.amount.BelievableAmount.KCAL_PER_100G
import com.metaself.app.domain.amount.BelievableAmount.KCAL_PER_UNIT
import com.metaself.app.domain.amount.BelievableAmount.MACRO_PER_100G
import com.metaself.app.domain.amount.BelievableAmount.MACRO_PER_UNIT

/**
 * Which of a stored food's number groups hold a figure today's rule would refuse (issue #7).
 *
 * Until 0.32.6 (D42) the food form and the label form took "Infinity", "1e999" and absurd finite
 * figures, and a food saved then still holds them: an infinite calorie figure logs a row of
 * 2,147,483,647 kcal. The rule is D42's, from [BelievableAmount], with the ceilings the food form
 * applies to each box — so a group is named here exactly when the form would refuse to save it.
 *
 * **A group is named only for a figure it holds.** A blank is "not known" (D4), never impossible,
 * and a group with some figures blank is already unreadable (`toDomain` drops it); it is named only
 * when what it does hold would be refused. Nothing but the three groups is judged: not the name,
 * the brand, the unit's name, or anything logged.
 *
 * Pure, over the stored row rather than the domain food, because `toDomain` hides a group holding a
 * negative figure — and a repair has to see what the phone actually holds.
 */
object ImpossibleFigures {

    /** A food's three independent number groups, each cleared on its own. */
    enum class Group { PER_100G, PER_UNIT, GRAMS_PER_UNIT }

    fun of(food: FoodEntity): Set<Group> = buildSet {
        val per100g = listOf(
            food.kcalPer100g to KCAL_PER_100G,
            food.proteinPer100g to MACRO_PER_100G,
            food.carbsPer100g to MACRO_PER_100G,
            food.fatPer100g to MACRO_PER_100G,
        )
        val perUnit = listOf(
            food.kcalPerUnit to KCAL_PER_UNIT,
            food.proteinPerUnit to MACRO_PER_UNIT,
            food.carbsPerUnit to MACRO_PER_UNIT,
            food.fatPerUnit to MACRO_PER_UNIT,
        )
        if (per100g.any { (figure, most) -> impossible(figure, most) }) add(Group.PER_100G)
        if (perUnit.any { (figure, most) -> impossible(figure, most) }) add(Group.PER_UNIT)
        if (impossible(food.gramsPerUnit, GRAMS)) add(Group.GRAMS_PER_UNIT)
    }

    private fun impossible(figure: Double?, most: Double): Boolean =
        figure != null && !BelievableAmount.isBelievable(figure, most)
}
