package com.metaself.app.domain.food

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.portion.Portions

/**
 * One row of the record, with the two things about its meal that deciding a tile needs (D154).
 *
 * The meal's `savedMealAdjusted` is deliberately not here: that flag is never counted.
 */
data class LoggedRow(
    val mealId: Long,
    val savedMealId: Long?,
    val foodId: Long?,
    val amount: Double,
    val unit: String,
    val loggedAtMillis: Long,
)

/** The last time a food was logged on its own: how much, in the unit it was logged in (D155). */
data class LastAmount(
    val foodId: Long,
    val amount: Double,
    val unit: String,
    val loggedAtMillis: Long,
)

/** An amount of a food, in one of the ways that food can be counted right now. */
data class QuickAmount(val amount: Double, val countedAs: CountedAs)

/**
 * Something one press on Add something will write (D154).
 *
 * @property items the rows, costed from the food's or meal's figures as they stand now.
 * @property savedMealId which built meal this is, or null for a food.
 * @property foodId which food this is, or null for a meal.
 * @property usual the amount a food's tile logs, or null for a meal.
 */
data class QuickTile(
    val name: String,
    val items: List<FoodItem>,
    val savedMealId: Long? = null,
    val foodId: Long? = null,
    val usual: QuickAmount? = null,
) {
    val kcal: Int get() = items.sumOf { it.kcal }
}

/**
 * Which foods and meals get a tile, and how much of a food a tile logs (D154, D155).
 *
 * All of it is worked out from the record on every read. Nothing is stored, so there is nothing to
 * drift and nothing for a backup to carry. The cut-offs are chosen starting points, not measurements.
 */
object QuickAdd {

    const val MOST_TILES = 8

    /** How far back the record is read, by the day a meal is on. */
    const val WINDOW_DAYS = 30L

    /** Fewer loggings than this in the window is not a habit. */
    const val AT_LEAST_LOGGINGS = 3

    /**
     * The tiles, in order of name: usage decides which appear, and a tile moves only when the set
     * changes, so that its place can be learned.
     *
     * @param foods the foods still offered; one hidden, deleted or joined away is simply not here.
     * @param meals the built meals still offered.
     * @param rows every row of the record in the window.
     */
    fun tiles(foods: List<Food>, meals: List<SavedMeal>, rows: List<LoggedRow>): List<QuickTile> =
        (foodCandidates(foods, rows) + mealCandidates(meals, rows))
            .sortedWith(compareByDescending<Candidate> { it.loggings }.thenByDescending { it.latestMillis })
            .take(MOST_TILES)
            .map { it.tile }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /**
     * What a food's amount question opens holding (D155): the tile's amount if it has a tile,
     * otherwise the last time it was logged on its own, or null when neither can be costed now.
     */
    fun amountToOffer(food: Food, tiles: List<QuickTile>, last: LastAmount?): QuickAmount? =
        tiles.firstOrNull { it.foodId == food.id }?.usual
            ?: last?.let { costed(food, it.amount, it.unit)?.first }

    /** The row a logging of this food writes. Numbers are computed once and frozen onto it. */
    fun rowOf(food: Food, numbers: LoggedFrom.Numbers): FoodItem = FoodItem(
        name = food.name,
        portion = Portions.words(numbers.amount, numbers.unit),
        portionAmount = numbers.amount,
        portionUnit = numbers.unit,
        kcal = numbers.kcal,
        proteinG = numbers.proteinG,
        carbsG = numbers.carbsG,
        fatG = numbers.fatG,
        source = numbers.source,
        confidence = numbers.confidence,
        foodId = food.id,
    )

    private class Candidate(val tile: QuickTile, val loggings: Int, val latestMillis: Long)

    /**
     * A food counts a row only when it was logged on its own: the only row of a meal that did not
     * come from a built meal. So a part of a built meal never becomes a favourite through that meal.
     */
    private fun foodCandidates(foods: List<Food>, rows: List<LoggedRow>): List<Candidate> {
        val onItsOwn = rows.groupBy { it.mealId }.values
            .mapNotNull { meal -> meal.singleOrNull()?.takeIf { it.savedMealId == null && it.foodId != null } }
            .groupBy { it.foodId }
        return foods.mapNotNull { food ->
            val logged = onItsOwn[food.id]?.takeIf { it.size >= AT_LEAST_LOGGINGS } ?: return@mapNotNull null
            val usual = usualAmount(logged) ?: return@mapNotNull null
            val (amount, numbers) = costed(food, usual.amount, usual.unit) ?: return@mapNotNull null
            Candidate(
                tile = QuickTile(
                    name = food.name,
                    items = listOf(rowOf(food, numbers)),
                    foodId = food.id,
                    usual = amount,
                ),
                loggings = logged.size,
                latestMillis = logged.maxOf { it.loggedAtMillis },
            )
        }
    }

    /**
     * The most common amount, the most recent breaking a tie — or null when no amount makes up at
     * least half of them, because one press would then log a wrong number more often than not.
     */
    private fun usualAmount(logged: List<LoggedRow>): LoggedRow? {
        val sameAmount = logged.groupBy { it.amount to it.unit.lowercase() }.values
            .maxWithOrNull(compareBy<List<LoggedRow>> { it.size }.thenBy { rows -> rows.maxOf { it.loggedAtMillis } })
            ?: return null
        return sameAmount.first().takeIf { sameAmount.size * 2 >= logged.size }
    }

    /** Each logging of a built meal counts once, however many parts it has. */
    private fun mealCandidates(meals: List<SavedMeal>, rows: List<LoggedRow>): List<Candidate> {
        val bySavedMeal = rows.filter { it.savedMealId != null }.groupBy { it.savedMealId }
        return meals.mapNotNull { meal ->
            val logged = bySavedMeal[meal.id] ?: return@mapNotNull null
            val loggings = logged.distinctBy { it.mealId }.size
            if (loggings < AT_LEAST_LOGGINGS) return@mapNotNull null
            val items = SavedMeals.toLoggableItems(meal)
            if (items.isEmpty()) return@mapNotNull null
            Candidate(
                tile = QuickTile(name = meal.name, items = items, savedMealId = meal.id),
                loggings = loggings,
                latestMillis = logged.maxOf { it.loggedAtMillis },
            )
        }
    }

    /**
     * This amount of this food as it can be logged now, or null when it cannot: a food that has lost
     * the unit, or an amount past its ceiling (D42). A row stores its unit as words, so the way of
     * counting is found by asking which one writes those words.
     */
    private fun costed(food: Food, amount: Double, unit: String): Pair<QuickAmount, LoggedFrom.Numbers>? {
        if (amount <= 0.0) return null
        return CountedAs.entries.firstNotNullOfOrNull { countedAs ->
            val numbers = Logging.log(food.facts, amount, countedAs) as? LoggedFrom.Numbers
            val believable = BelievableAmount.isBelievable(amount, BelievableAmount.amountEaten(countedAs, food.facts))
            numbers?.takeIf { believable && it.unit.equals(unit, ignoreCase = true) }
                ?.let { QuickAmount(amount, countedAs) to it }
        }
    }
}
