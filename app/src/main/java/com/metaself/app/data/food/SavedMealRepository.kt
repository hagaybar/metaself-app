package com.metaself.app.data.food

import androidx.room.withTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Which meal already holds a name the owner has just tried to use. */
data class MealNameTaken(val existingMealId: Long)

/** Building a meal either happened, or was refused for a reason worth showing. */
sealed interface MealResult {
    data class Built(val mealId: Long) : MealResult
    data class NameTaken(val why: MealNameTaken) : MealResult
    data object Done : MealResult
}

/**
 * The meals the owner has built.
 *
 * **A meal is only something he built and named.** Nothing here promotes anything from what he
 * happens to have logged together, and nothing invents a name.
 */
interface SavedMealRepository {

    fun observeOffered(): Flow<List<SavedMeal>>

    suspend fun byId(id: Long): SavedMeal?

    /** Start one. It has no parts yet, which is a meal he has not finished rather than an error. */
    suspend fun create(name: String): MealResult

    suspend fun rename(mealId: Long, name: String): MealResult

    /**
     * Put a food in it, or change how much of it is in there.
     *
     * The same food twice in one meal is an editing accident rather than a thing he meant, so
     * adding one already there changes the amount instead of making a second row.
     */
    suspend fun put(mealId: Long, foodId: Long, amount: Double, countedAs: CountedAs)

    suspend fun remove(componentId: Long)

    /** The order is his. Identity no longer depends on it; the display does. */
    suspend fun reorder(mealId: Long, componentIdsInOrder: List<Long>)

    suspend fun hide(mealId: Long)

    suspend fun unhide(mealId: Long)

    suspend fun delete(mealId: Long)
}

class RoomSavedMealRepository @Inject constructor(
    private val database: MetaSelfDatabase,
    private val dao: SavedMealDao,
    private val foods: FoodDao,
    private val now: Now,
) : SavedMealRepository {

    override fun observeOffered(): Flow<List<SavedMeal>> =
        dao.observeOffered().map { rows -> rows.mapNotNull { toDomain(it) } }

    override suspend fun byId(id: Long): SavedMeal? = dao.byId(id)?.let { toDomain(it) }

    override suspend fun create(name: String): MealResult = database.withTransaction {
        val moment = now()
        val nameKey = FoodKeys.nameKey(name)
        dao.idNamed(nameKey)?.let { return@withTransaction MealResult.NameTaken(MealNameTaken(it)) }
        val id = dao.insertMeal(
            SavedMealEntity(
                name = FoodKeys.displayName(name),
                nameKey = nameKey,
                createdAtMillis = moment,
                updatedAtMillis = moment,
            ),
        )
        MealResult.Built(id)
    }

    override suspend fun rename(mealId: Long, name: String): MealResult =
        database.withTransaction {
            val nameKey = FoodKeys.nameKey(name)
            val taken = dao.idNamed(nameKey)
            if (taken != null && taken != mealId) {
                return@withTransaction MealResult.NameTaken(MealNameTaken(taken))
            }
            // The row is edited in place, so every day logged from this meal retitles without
            // anything being repointed — the same mechanism a renamed food uses, and the same
            // answer, which is what stops the two behaving differently for no reason.
            dao.rename(mealId, FoodKeys.displayName(name), nameKey, now())
            MealResult.Done
        }

    override suspend fun put(mealId: Long, foodId: Long, amount: Double, countedAs: CountedAs) {
        database.withTransaction {
            val moment = now()
            val existing = dao.componentFor(mealId, foodId)
            if (existing != null) {
                dao.setAmount(existing, amount, countedAs.name)
            } else {
                dao.insertComponent(
                    SavedMealComponentEntity(
                        savedMealId = mealId,
                        foodId = foodId,
                        position = dao.nextPosition(mealId),
                        amount = amount,
                        countedAs = countedAs.name,
                    ),
                )
            }
            dao.touch(mealId, moment)
        }
    }

    override suspend fun remove(componentId: Long) {
        dao.deleteComponent(componentId)
    }

    override suspend fun reorder(mealId: Long, componentIdsInOrder: List<Long>) {
        database.withTransaction {
            componentIdsInOrder.forEachIndexed { at, id -> dao.setPosition(id, at) }
            dao.touch(mealId, now())
        }
    }

    override suspend fun hide(mealId: Long) {
        dao.setHidden(mealId, now(), now())
    }

    override suspend fun unhide(mealId: Long) {
        dao.setHidden(mealId, null, now())
    }

    override suspend fun delete(mealId: Long) {
        dao.deleteMeal(mealId)
    }

    /**
     * A meal with its parts, each part carrying the whole food it points at.
     *
     * A component whose food cannot be read is dropped rather than shown as a blank: it is a
     * pointer into a table that says a food must know at least one way of counting, so a food that
     * reads back as nothing is a fault, not a meal the owner should be looking at.
     */
    private suspend fun toDomain(row: SavedMealWithComponents): SavedMeal? {
        val components = row.components.sortedBy { it.position }.mapNotNull { component ->
            val food = foods.byId(component.foodId)?.toDomain() ?: return@mapNotNull null
            MealComponent(
                id = component.id,
                food = food,
                amount = component.amount,
                countedAs = CountedAs.entries.firstOrNull { it.name == component.countedAs }
                    ?: CountedAs.GRAMS,
                position = component.position,
            )
        }
        return runCatching {
            SavedMeal(
                id = row.meal.id,
                name = row.meal.name,
                components = components,
                createdAtMillis = row.meal.createdAtMillis,
                updatedAtMillis = row.meal.updatedAtMillis,
                hidden = row.meal.hiddenAtMillis != null,
            )
        }.getOrNull()
    }
}
