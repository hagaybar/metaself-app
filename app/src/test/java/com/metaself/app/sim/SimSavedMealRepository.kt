package com.metaself.app.sim

import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.MealNameTaken
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Saved meals in memory, resolving foods out of the food repository the way the database does.
 *
 * **Why this exists, and it is the most instructive thing in this harness.** `FakeSavedMealRepository`
 * keeps its own little map of foods it has been TOLD about, through `knowsAbout(...)`, and its `put`
 * begins `val food = foodsById[foodId] ?: return` — an unknown food is silently dropped. For a unit
 * test that is fine and even helpful: the test names the foods it cares about in its own first line.
 *
 * For a walk it was a disaster. The walk creates its foods as it goes, so nothing ever told the fake
 * about them, so every single "Put it in" did nothing at all — no refusal, no message, the row folding
 * away exactly as though it had worked. The walking agent tried four separate routes, all four failed
 * identically, and it reported, in good faith and at the top of its findings:
 *
 * > "I never built the Greek salad. I do not think anyone could."
 *
 * That is a false finding manufactured entirely by a stand-in being dumber than the thing it stands
 * for, and it is the exact hazard the design names: a fake that is kinder OR stupider than Room makes
 * the walk say something untrue about the app. It would have been the headline of the report.
 *
 * So this one has no private map. It resolves the food from the same repository the app writes foods
 * into, which is what `RoomSavedMealRepository` does with a foreign key. Everything else keeps the
 * existing fake's two load-bearing behaviours: a name is taken or it is not, and the same food goes in
 * once — putting it in again changes the amount rather than adding a second row.
 */
class SimSavedMealRepository(private val foods: FakeFoodRepository) : SavedMealRepository {

    private val meals = MutableStateFlow(emptyList<SavedMeal>())
    private var nextMealId = 1L
    private var nextComponentId = 1_000L

    val current: List<SavedMeal> get() = meals.value

    /**
     * Every meal's name, hidden ones included.
     *
     * The day titles a logging from the meal it came from, and `MealDao` reads
     * `SELECT id, name FROM saved_meals` — all of them. Reading the OFFERED list instead would make a
     * day lose its title the moment its meal was hidden, which the app does not do.
     */
    fun observeAllNames(): Flow<Map<Long, String>> =
        meals.map { all -> all.associate { it.id to it.name } }

    /**
     * The meals, with every food inside them re-read as it is NOW.
     *
     * `RoomSavedMealRepository` re-reads each component's food on every read, because a component
     * stores a pointer and an amount, never a copy. Keeping the `Food` object captured at the moment
     * it was put in would make this harness say the app fails to update a meal when its foods are
     * corrected, renamed or joined — which is the very promise `SavedMeal.kcal` is built on. That is
     * the same defect this class was written to fix, one layer deeper.
     */
    override fun observeOffered(): Flow<List<SavedMeal>> =
        combine(meals, foods.observeAll()) { all, current ->
            all.filterNot { it.hidden }
                .map { it.refreshed(current) }
                // Newest change first, as the database orders them. Left unsorted, the list came out
                // in creation order and nothing ever moved to the top, so a walk would report that
                // editing a meal does not bring it to the front — when the app says it does.
                .sortedByDescending { it.updatedAtMillis }
        }

    override suspend fun byId(id: Long): SavedMeal? =
        meals.value.firstOrNull { it.id == id }?.refreshed(foods.current)

    /** Every name and number in the meal, as the food list holds them now. */
    private fun SavedMeal.refreshed(current: List<com.metaself.app.domain.food.Food>): SavedMeal {
        val byId = current.associateBy { it.id }
        return copy(
            components = components.mapNotNull { component ->
                // A food that has gone entirely takes its component with it, which is what the
                // database's cascade does rather than leaving a component pointing at nothing.
                byId[component.food.id]?.let { component.copy(food = it) }
            },
        )
    }

    override suspend fun create(name: String): MealResult {
        val key = FoodKeys.nameKey(name)
        meals.value.firstOrNull { FoodKeys.nameKey(it.name) == key }?.let {
            return MealResult.NameTaken(MealNameTaken(it.id))
        }
        val made = SavedMeal(
            id = nextMealId++,
            name = FoodKeys.displayName(name),
            updatedAtMillis = tick(),
        )
        meals.value = meals.value + made
        return MealResult.Built(made.id)
    }

    override suspend fun rename(mealId: Long, name: String): MealResult {
        val key = FoodKeys.nameKey(name)
        meals.value.firstOrNull { it.id != mealId && FoodKeys.nameKey(it.name) == key }?.let {
            return MealResult.NameTaken(MealNameTaken(it.id))
        }
        replace(mealId) { it.copy(name = FoodKeys.displayName(name)) }
        return MealResult.Done
    }

    /**
     * Puts a food in, looked up in the food repository rather than in a map somebody had to prime.
     *
     * A food that genuinely is not there is still dropped, because there is nothing to put; that case
     * is a real one the database would refuse too. What has changed is that a food the walk created a
     * moment ago is now findable, which it never was before.
     */
    override suspend fun put(mealId: Long, foodId: Long, amount: Double, countedAs: CountedAs) {
        val food = foods.byId(foodId) ?: return
        replace(mealId) { meal ->
            val existing = meal.components.firstOrNull { it.food.id == foodId }
            if (existing == null) {
                meal.copy(
                    components = meal.components + MealComponent(
                        id = nextComponentId++,
                        food = food,
                        amount = amount,
                        countedAs = countedAs,
                        position = meal.components.size,
                    ),
                )
            } else {
                meal.copy(
                    components = meal.components.map {
                        if (it.id == existing.id) {
                            it.copy(amount = amount, countedAs = countedAs)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    override suspend fun remove(componentId: Long) {
        meals.value = meals.value.map { meal ->
            meal.copy(components = meal.components.filterNot { it.id == componentId })
        }
    }

    override suspend fun reorder(mealId: Long, componentIdsInOrder: List<Long>) {
        replace(mealId) { meal ->
            meal.copy(
                components = componentIdsInOrder.mapIndexedNotNull { at, id ->
                    meal.components.firstOrNull { it.id == id }?.copy(position = at)
                },
            )
        }
    }

    override suspend fun hide(mealId: Long) = replace(mealId) { it.copy(hidden = true) }

    override suspend fun unhide(mealId: Long) = replace(mealId) { it.copy(hidden = false) }

    override suspend fun delete(mealId: Long) {
        meals.value = meals.value.filterNot { it.id == mealId }
    }

    /** Every change stamps the meal, because the list is ordered by when it was last touched. */
    private fun replace(id: Long, change: (SavedMeal) -> SavedMeal) {
        meals.value = meals.value.map {
            if (it.id == id) change(it).copy(id = id, updatedAtMillis = tick()) else it
        }
    }

    private var clock = 1L

    private fun tick(): Long = clock++
}
