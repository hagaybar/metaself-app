package com.metaself.app.data.food

import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The saved-meal repository, in memory, for screens and view models under test.
 *
 * Faithful about the two things a screen can get wrong if this lies: **a name is taken or it is
 * not**, so a test cannot show two meals with one name, and **the same food goes in once**, so a
 * test cannot show a salad with two cucumbers in it where the real one would have changed the
 * amount. Everything else is the simplest thing that behaves.
 */
class FakeSavedMealRepository(initial: List<SavedMeal> = emptyList()) : SavedMealRepository {

    private val meals = MutableStateFlow(initial.mapIndexed { at, meal -> meal.copy(id = at + 1L) })
    private var nextMealId: Long = initial.size + 1L
    private var nextComponentId: Long = 1_000L

    val current: List<SavedMeal> get() = meals.value

    override fun observeOffered(): Flow<List<SavedMeal>> =
        meals.map { all -> all.filterNot { it.hidden } }

    override suspend fun byId(id: Long): SavedMeal? = meals.value.firstOrNull { it.id == id }

    override suspend fun create(name: String): MealResult {
        val nameKey = FoodKeys.nameKey(name)
        meals.value.firstOrNull { FoodKeys.nameKey(it.name) == nameKey }?.let {
            return MealResult.NameTaken(MealNameTaken(it.id))
        }
        val made = SavedMeal(id = nextMealId++, name = FoodKeys.displayName(name))
        meals.value = meals.value + made
        return MealResult.Built(made.id)
    }

    /**
     * Not one transaction — this fake has none. It runs the same steps in the same order, so a
     * caller's own behaviour can be tested here; that a failure in [then] leaves no meal behind is
     * the real repository's promise, and `RoomSavedMealRepositoryTest` checks it in CI.
     */
    override suspend fun createThen(
        name: String,
        then: suspend (mealId: Long) -> Unit,
    ): MealResult {
        val made = create(name)
        if (made is MealResult.Built) then(made.mealId)
        return made
    }

    override suspend fun rename(mealId: Long, name: String): MealResult {
        val nameKey = FoodKeys.nameKey(name)
        meals.value.firstOrNull { it.id != mealId && FoodKeys.nameKey(it.name) == nameKey }?.let {
            return MealResult.NameTaken(MealNameTaken(it.id))
        }
        replace(mealId) { it.copy(name = FoodKeys.displayName(name)) }
        return MealResult.Done
    }

    override suspend fun put(mealId: Long, foodId: Long, amount: Double, countedAs: CountedAs) {
        val food = foodsById[foodId] ?: return
        replace(mealId) { meal ->
            val existing = meal.components.firstOrNull { it.food.id == foodId }
            if (existing != null) {
                meal.copy(
                    components = meal.components.map {
                        if (it.id == existing.id) it.copy(amount = amount, countedAs = countedAs) else it
                    },
                )
            } else {
                meal.copy(
                    components = meal.components + MealComponent(
                        id = nextComponentId++,
                        food = food,
                        amount = amount,
                        countedAs = countedAs,
                        position = meal.components.size,
                    ),
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

    /** The foods this fake can put into a meal, since it has no food table of its own. */
    private val foodsById = mutableMapOf<Long, Food>()

    fun knowsAbout(vararg foods: Food) = apply {
        foods.forEach { foodsById[it.id] = it }
    }

    private fun replace(id: Long, change: (SavedMeal) -> SavedMeal) {
        meals.value = meals.value.map { if (it.id == id) change(it).copy(id = id) else it }
    }
}
