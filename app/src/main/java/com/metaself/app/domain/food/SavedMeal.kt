package com.metaself.app.domain.food

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.portion.Portions

/**
 * One food in a meal the owner built, and how much of it.
 *
 * It holds no words for that amount: a screen draws them from [amount] and [countedAs], in the
 * unit logging would put on the day's row, so they are always the app's own form.
 *
 * @property countedAs which of the food's ways this amount is expressed in. Under the earlier
 *   design this was a GUARD, because a food could change which kind it was and a "150" authored as
 *   grams would silently become 150 slices. A food has no kind now, so the trap is gone with the
 *   thing that caused it and this is simply part of what the component says.
 */
data class MealComponent(
    val id: Long = 0,
    val food: Food,
    val amount: Double,
    val countedAs: CountedAs,
    val position: Int = 0,
) {
    init { require(amount > 0.0) { "a meal cannot contain none of something" } }

    /** What this much of this food is worth, or why it cannot be worked out. */
    val worth: LoggedFrom get() = Logging.log(food.facts, amount, countedAs)

    /**
     * True when the food no longer knows the thing this component counts it in.
     *
     * The one residue of the old guard column: emptying a food's per-unit numbers while a meal
     * counts it in slices leaves the meal unable to cost itself. The manager refuses that by name
     * rather than letting it happen, so this should never be true — it is here so that a meal that
     * somehow reaches this state says so instead of quietly showing a smaller number.
     */
    val cannotBeCosted: Boolean get() = worth is LoggedFrom.NotOnOffer
}

/**
 * A meal the owner built and named.
 *
 * **Only something he built.** Nothing is promoted from what he happens to have logged together, and
 * the app never invents a name — which is the whole reason the derived list this replaces refused to
 * label anything it offered.
 *
 * **No stored total.** What it is worth is the sum over its parts at the moment it is looked at, and
 * the numbers that go on the record are frozen onto the log rows at the moment it is logged. A total
 * stored on the definition would be a second answer that could disagree with its own parts.
 *
 * **There is no draft state, and that is what makes building resumable.** A half-built salad is
 * simply a meal with fewer things in it — no flag, no state machine, no query that has to remember
 * to exclude it. The cost is that a half-built meal is offered for logging like a finished one.
 */
data class SavedMeal(
    val id: Long = 0,
    val name: String,
    val components: List<MealComponent> = emptyList(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val hidden: Boolean = false,
) {
    init { require(name.isNotBlank()) { "a meal the owner built has a name he gave it" } }

    /**
     * What it is worth as its parts stand right now.
     *
     * This moves as the foods in it are corrected, and that is the design rather than a bug: what a
     * day's row shows was frozen when it was logged, and what the definition shows is today's sum
     * over today's foods. The two will differ for a meal whose foods have since been corrected.
     */
    val kcal: Int get() = components.sumOf { (it.worth as? LoggedFrom.Numbers)?.kcal ?: 0 }

    /** True when something in it can no longer be costed, which is worth saying rather than hiding. */
    val incomplete: Boolean get() = components.any { it.cannotBeCosted }

    val isEmpty: Boolean get() = components.isEmpty()
}

/**
 * Turning a meal he built into rows on the record.
 *
 * Everything happens once, here, at the moment of logging: each component asks its food what this
 * much of it is worth, and the answer is frozen onto its own row. Nothing recomputes it afterwards,
 * which is why correcting a food later cannot change what a past day was worth, and why a day's
 * total is simply the sum of its rows.
 */
object SavedMeals {

    /**
     * The rows this meal would put on the record, in the order he arranged them.
     *
     * A component whose food can no longer be costed the way the component counts it is left out
     * rather than logged as nothing: a zero on the record would be a claim that he ate something
     * worth nothing, which is a different and worse falsehood than a row that is not there.
     */
    fun toLoggableItems(meal: SavedMeal): List<FoodItem> = meal.components
        .sortedBy { it.position }
        .mapNotNull { component ->
            val numbers = component.worth as? LoggedFrom.Numbers ?: return@mapNotNull null
            FoodItem(
                name = component.food.name,
                portion = Portions.words(numbers.amount, numbers.unit),
                portionAmount = numbers.amount,
                portionUnit = numbers.unit,
                kcal = numbers.kcal,
                proteinG = numbers.proteinG,
                carbsG = numbers.carbsG,
                fatG = numbers.fatG,
                source = numbers.source,
                confidence = numbers.confidence,
                foodId = component.food.id,
            )
        }

    /**
     * Whether what is about to be logged differs from the meal's definition **as it stands now**.
     *
     * A fact known exactly at the moment of logging and never again: the definition may be edited
     * afterwards, so comparing a past day against a later definition would be comparing it against
     * something that did not exist yet.
     *
     * **This must never be counted, aggregated, or turned into an offer to change the meal.**
     * Nothing learns from what the owner does — drop the oil every day for a month and the salad
     * still has oil in it. It exists only so a day's row can be labelled honestly.
     */
    fun wasAdjusted(meal: SavedMeal, logging: List<MealComponent>): Boolean {
        val asDefined = meal.components.sortedBy { it.position }
            .map { it.food.id to it.amount to it.countedAs }
        val asLogged = logging.sortedBy { it.position }
            .map { it.food.id to it.amount to it.countedAs }
        return asDefined != asLogged
    }
}
