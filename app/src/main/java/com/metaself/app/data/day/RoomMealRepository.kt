package com.metaself.app.data.day

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The meal repository over Room.
 *
 * The DAO is a constructor parameter so a test can build one over an in-memory database.
 */
class RoomMealRepository @Inject constructor(
    private val dao: MealDao,
) : MealRepository {

    /**
     * The day's meals, each row labelled with what its food is called NOW.
     *
     * Combined with the names rather than joined to them, so that renaming a food redraws every day
     * it appears on without anything having to tell them, and so the mapping stays a pure function
     * of two inputs.
     */
    override fun observeDay(epochDay: Long): Flow<List<Meal>> =
        combine(dao.observeDay(epochDay), currentNames(), savedMealNames()) { rows, names, titles ->
            rows.mapNotNull { it.toDomain(names, titles) }
        }

    private fun currentNames(): Flow<Map<Long, String>> =
        dao.observeCurrentNames().map { rows -> rows.associate { it.foodId to it.displayName } }

    private fun savedMealNames(): Flow<Map<Long, String>> =
        dao.observeSavedMealNames().map { rows -> rows.associate { it.id to it.name } }

    override fun observeLoggedDays(): Flow<Set<Long>> =
        dao.observeLoggedDays().map { it.toSet() }

    override suspend fun kcalByDaySince(fromEpochDay: Long): Map<Long, Int> =
        dao.kcalByDaySince(fromEpochDay).associate { it.epochDay to it.kcal }

    override suspend fun log(meal: Meal): List<Long> {
        val (mealEntity, itemEntities) = meal.toEntities()
        val mealId = dao.insertMeal(mealEntity)
        // The insert's own answer, in the order the rows were given: the ids name exactly these
        // rows, which reading the day back could not (D46, issue #24).
        return dao.insertItems(itemEntities.map { it.copy(mealId = mealId) })
    }

    override suspend fun setEatenAt(mealId: Long, atMillis: Long) {
        dao.setEatenAt(mealId, atMillis)
    }

    override suspend fun rowsWithNoFood(): List<DetachedRow> = dao.rowsWithNoFood()

    override suspend fun attachRow(itemId: Long, foodId: Long) {
        dao.attachRow(itemId, foodId)
    }

    override suspend fun updateItem(item: FoodItem) {
        val mealId = dao.mealIdOf(item.id) ?: return
        dao.updateItem(item.toEntity(mealId = mealId))
    }

    override suspend fun deleteItem(itemId: Long): DeletedEntry? = dao.deleteItem(itemId)

    override suspend fun restore(entry: DeletedEntry) = dao.restore(entry)

    /**
     * The new meal row, and the rows moved onto it, in one transaction.
     *
     * Its time is the earliest of the meals the chosen rows were in, so the gathered row keeps the
     * day's order instead of jumping to the end. That read is one statement before the transaction
     * rather than inside it: this app has one user and one writer, and the alternative is a DAO
     * method that takes the day and the meal apart and rebuilds them.
     *
     * `savedMealAdjusted` is false, and always will be here: nothing was logged differently from the
     * meal's definition, because the definition was made out of these very rows a moment ago.
     *
     * **When that read comes back null, nothing is gathered and nothing is said**, which is accepted
     * rather than overlooked. None of the ids is a row any more — every one was deleted between the
     * choice and this call — and the saved meal itself has already been created by the caller, so
     * what is left is a meal he named holding the parts he chose, with no day pointing at it. No
     * stored number is harmed by that, and the alternative — unpicking the caller's work from in
     * here — would put this repository in charge of a meal it did not make.
     */
    override suspend fun gatherIntoSavedMeal(
        epochDay: Long,
        itemIds: List<Long>,
        savedMealId: Long,
    ) {
        if (itemIds.isEmpty()) return
        val loggedAtMillis = dao.earliestLoggedAtOf(itemIds) ?: return
        dao.gather(
            meal = MealEntity(
                epochDay = epochDay,
                loggedAtMillis = loggedAtMillis,
                note = null,
                savedMealId = savedMealId,
                savedMealAdjusted = false,
            ),
            itemIds = itemIds,
        )
    }
}
