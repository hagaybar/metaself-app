package com.metaself.app.data.food

import androidx.room.withTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.food.MealFromDay

/**
 * *Keep as a meal*: a described meal kept in My meals under a name, logging nothing (D58 §5.2).
 *
 * **All or nothing** (§12.7). Finding or making each food, teaching it the row's worth, making the
 * meal and its parts are one transaction. A storage failure anywhere ends it; so does a refusal —
 * the name taken, a part that cannot join — which is thrown inside so that everything is rolled
 * back, facts re-taught to a food he already had included, and answered afterwards.
 */
interface MealKeeper {

    sealed interface Kept {
        data class Made(val mealId: Long) : Kept

        /** A meal of that name exists; nothing was written. */
        data class NameTaken(val name: String) : Kept

        /** Parts that cannot join a meal, each a finished sentence; nothing was written. */
        data class Refused(val why: List<String>) : Kept
    }

    /** Keep [rows] as a meal called [name]. Throws only a storage failure, after rolling back. */
    suspend fun keep(name: String, rows: List<ToLog>): Kept
}

class RoomMealKeeper(
    private val database: MetaSelfDatabase,
    private val loggedFoods: LoggedFoods,
    private val foods: FoodRepository,
    private val savedMeals: SavedMealRepository,
) : MealKeeper {

    /** Thrown inside the transaction to roll it back, and answered outside it. */
    private class Refusal(val kept: MealKeeper.Kept) : Exception()

    override suspend fun keep(name: String, rows: List<ToLog>): MealKeeper.Kept {
        val typed = name.trim()
        return try {
            database.withTransaction {
                // Strict: a failure swallowed in here would roll everything back and report success.
                val attached = loggedFoods.attach(rows, strict = true).items
                val byId = attached.mapNotNull { it.foodId }.distinct()
                    .mapNotNull { foods.byId(it) }
                    .associateBy { it.id }
                val outcome = MealFromDay.fromDescribed(attached) { byId[it] }
                if (outcome.refusals.isNotEmpty()) throw Refusal(MealKeeper.Kept.Refused(outcome.refusals))

                when (
                    val made = savedMeals.createThen(typed) { mealId ->
                        outcome.components.forEach { part ->
                            savedMeals.put(mealId, part.food.id, part.amount, part.countedAs)
                        }
                    }
                ) {
                    is MealResult.Built -> MealKeeper.Kept.Made(made.mealId)
                    is MealResult.NameTaken -> throw Refusal(MealKeeper.Kept.NameTaken(typed))
                    // Unreachable: `create` answers only Built or NameTaken.
                    MealResult.Done -> throw Refusal(MealKeeper.Kept.Refused(emptyList()))
                }
            }
        } catch (refused: Refusal) {
            refused.kept
        }
    }
}
