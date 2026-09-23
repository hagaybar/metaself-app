package com.metaself.app.data.day

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app asks of the meal tables.
 *
 * [observeDay] returns a Flow, so the Today screen redraws itself when something is logged rather
 * than being told to.
 */
@Dao
interface MealDao {

    @Transaction
    @Query("SELECT * FROM meals WHERE epochDay = :epochDay ORDER BY loggedAtMillis ASC, id ASC")
    fun observeDay(epochDay: Long): Flow<List<MealWithItems>>

    /**
     * Every meal, newest first, for offering back to be repeated.
     *
     * Across all days rather than one: what the owner had for breakfast last Tuesday is as
     * repeatable as what he had this morning. The limit is generous — deduplication happens above
     * this, and it needs more rows than it will keep.
     */
    @Transaction
    /**
     * Every calendar day that holds at least one thing eaten (D13).
     *
     * Joined to the items rather than read from the meals table alone: a meal row with no items
     * left is not a day the owner logged, and the streak counts days that hold food, not rows.
     */
    @Query(
        "SELECT DISTINCT m.epochDay FROM meals m " +
            "INNER JOIN food_items f ON f.mealId = m.id",
    )
    fun observeLoggedDays(): Flow<List<Long>>

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    /**
     * Insert rows, and hand back the ids SQLite gave them, in the order they were passed.
     *
     * Room fills the answer from the insert itself, so it names exactly these rows — which is what
     * lets what was just described be gathered under a name a moment later (D46, issue #24). Callers
     * that only write ignore it.
     */
    @Insert
    suspend fun insertItems(items: List<FoodItemEntity>): List<Long>

    /**
     * Replace one item's row.
     *
     * `@Update` matches on the primary key, so the item stays in the meal and on the day it was
     * already in — correcting a number must not move food to another date.
     */
    @Update
    suspend fun updateItem(item: FoodItemEntity)

    /**
     * When a meal was eaten, corrected by the owner (D33).
     *
     * The time is the meal's alone — rows carry none — so this is the one statement that moves one,
     * and it moves every row under it together. A query rather than an `@Update`, which would rewrite
     * the whole meal row to change one field of it.
     */
    @Query("UPDATE meals SET loggedAtMillis = :atMillis WHERE id = :mealId")
    suspend fun setEatenAt(mealId: Long, atMillis: Long)

    /** Rows attached to no food, for the repair of issue #22. */
    @Query("SELECT id AS itemId, name FROM food_items WHERE foodId IS NULL")
    suspend fun rowsWithNoFood(): List<DetachedRow>

    /** The `IS NULL` is the guard: a row attached meanwhile by another route keeps what it has. */
    @Query("UPDATE food_items SET foodId = :foodId WHERE id = :itemId AND foodId IS NULL")
    suspend fun attachRow(itemId: Long, foodId: Long)

    @Query("SELECT mealId FROM food_items WHERE id = :itemId")
    suspend fun mealIdOf(itemId: Long): Long?

    /**
     * What every food is called now.
     *
     * Read as one small table rather than joined onto each row: the answer is the same for every row
     * on every day and is a few dozen entries wide. It is a Flow so that renaming a food redraws the
     * days it appears on without anything having to tell them.
     */
    @Query("SELECT foodId, displayName FROM food_names WHERE isPreferred = 1")
    fun observeCurrentNames(): Flow<List<CurrentFoodName>>

    /** What every meal the owner built is called now, for titling the days it was eaten on. */
    @Query("SELECT id, name FROM saved_meals")
    fun observeSavedMealNames(): Flow<List<CurrentSavedMealName>>

    /**
     * Remove one item, and the meal along with it if that was the last thing in it; hand back both
     * as they were stored (issue #25).
     *
     * A meal cannot be empty (see `Meal`), and this is where that invariant is kept for the stored
     * form. Both statements run in one transaction so no reader can observe an empty meal in
     * between.
     *
     * The row and its meal are read inside the same transaction, before the delete, because that is
     * the last moment they exist: the row's id is gone the moment the row is, and the meal may go
     * with it. Read as entities rather than domain items, so an undo writes back exactly what was
     * stored — a source this version cannot read included (D4). The meal is handed back even when
     * it survives the delete, because by the time Undo is pressed it may not have. Null when there
     * was no such row, and then nothing is deleted.
     */
    @Transaction
    suspend fun deleteItem(itemId: Long): DeletedEntry? {
        val item = itemById(itemId) ?: return null
        // A row cannot outlive its meal: `food_items.mealId` is a NOT NULL foreign key that
        // cascades, and both reads share this transaction. Should that ever break, fail loudly —
        // returning null here would leave the row in place and the swipe silently deleting nothing.
        val meal = checkNotNull(mealById(item.mealId)) {
            "food item $itemId has no meal ${item.mealId}"
        }
        deleteItemRow(itemId)
        deleteEmptyMeals()
        return DeletedEntry(meal = meal, item = item)
    }

    /**
     * Put back what [deleteItem] took, under its original ids, in one transaction (issue #25).
     *
     * The original ids are safe to write back: every table here is `AUTOINCREMENT`, which never
     * hands a freed id out again, so an id in [entry] is either free or still held by the very meal
     * the row was deleted from. That is what lets "is the meal still there?" be a primary-key read,
     * and it is what puts the meal back in its place: meals on a day are read ordered by time then
     * id. A meal's rows are read through `@Relation`, whose query has no `ORDER BY`, so their order
     * is SQLite's scan of the `mealId` index, which in practice is id order; nothing here promises
     * it. `RoomMealRepositoryTest` pins that a restored row comes back in its old place, and that
     * test runs in CI only.
     *
     * The checks are made here, inside the transaction that inserts, because the foreign keys they
     * answer are enforced here.
     */
    @Transaction
    suspend fun restore(entry: DeletedEntry) {
        // Undo applied twice, or the row came back by another route. Inserting would fail on the
        // primary key; inserting beside it would count the food twice.
        if (itemById(entry.item.id) != null) return

        // A meal still standing is left exactly as it is: its time, note and link now are the
        // owner's latest word on it (he may have re-timed it under D33), and the receipt's are older.
        if (mealById(entry.meal.id) == null) {
            insertMeal(
                entry.meal.copy(
                    // The saved meal may have been deleted since, and `meals.savedMealId` would refuse
                    // a dead id. Null is what `ON DELETE SET NULL` made of every other meal from it;
                    // `savedMealAdjusted` stays as it was, as it does on those.
                    savedMealId = entry.meal.savedMealId?.takeIf { savedMealExists(it) },
                ),
            )
        }
        insertItems(
            listOf(
                entry.item.copy(
                    mealId = entry.meal.id,
                    // Likewise the food: `food_items.foodId` would refuse a dead id, and detached is
                    // what deleting a food made of every other row of it. The repair of issue #22
                    // is what reattaches such a row.
                    foodId = entry.item.foodId?.takeIf { foodExists(it) },
                ),
            ),
        )
    }

    @Query("SELECT * FROM food_items WHERE id = :itemId")
    suspend fun itemById(itemId: Long): FoodItemEntity?

    @Query("SELECT * FROM meals WHERE id = :mealId")
    suspend fun mealById(mealId: Long): MealEntity?

    /** Whether a food still exists, so a restored row never points at one that does not. */
    @Query("SELECT EXISTS(SELECT 1 FROM foods WHERE id = :foodId)")
    suspend fun foodExists(foodId: Long): Boolean

    /** Whether a saved meal still exists, so a restored meal never points at one that does not. */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_meals WHERE id = :savedMealId)")
    suspend fun savedMealExists(savedMealId: Long): Boolean

    @Query("DELETE FROM food_items WHERE id = :itemId")
    suspend fun deleteItemRow(itemId: Long)

    /**
     * What each day held, in calories, since [fromEpochDay].
     *
     * Summed in the database rather than by reading every item into memory: this is asked for a
     * month at a time and the answer is one number per day.
     */
    @Query(
        "SELECT m.epochDay AS epochDay, SUM(f.kcal) AS kcal FROM meals m " +
            "INNER JOIN food_items f ON f.mealId = m.id " +
            "WHERE m.epochDay >= :fromEpochDay " +
            "GROUP BY m.epochDay",
    )
    suspend fun kcalByDaySince(fromEpochDay: Long): List<DayKcal>

    /** Every meal there has ever been, for an export. Not a flow: this is asked once. */
    @androidx.room.Transaction
    @Query("SELECT * FROM meals ORDER BY epochDay ASC, loggedAtMillis ASC, id ASC")
    suspend fun allMeals(): List<MealWithItems>

    /** Emptied before a restore. The foreign key cascades, so the items go with them. */
    @Query("DELETE FROM meals")
    suspend fun deleteAllMeals()

    @Query("DELETE FROM meals WHERE id NOT IN (SELECT DISTINCT mealId FROM food_items)")
    suspend fun deleteEmptyMeals()

    /**
     * Repoint rows at another meal.
     *
     * The only statement in this file that moves a row between meals, and it touches nothing else:
     * no calories, no macros, no portion, no source, no confidence. Grouping a day's rows under a
     * name is a labelling act, and the column list is where that is enforced rather than promised.
     */
    @Query("UPDATE food_items SET mealId = :mealId WHERE id IN (:itemIds)")
    suspend fun moveItemsTo(mealId: Long, itemIds: List<Long>)

    /**
     * When the earliest of these rows was logged, so a gathered meal can sit where it already was.
     *
     * Read from the meals the rows are in rather than from the rows, which carry no time of their
     * own. Null when none of the ids is a row, which is the caller's signal to do nothing.
     */
    @Query(
        "SELECT MIN(m.loggedAtMillis) FROM meals m " +
            "INNER JOIN food_items f ON f.mealId = m.id " +
            "WHERE f.id IN (:itemIds)",
    )
    suspend fun earliestLoggedAtOf(itemIds: List<Long>): Long?

    /**
     * Put a new meal row in front of rows already logged, and take away what is left empty.
     *
     * One transaction, because the three statements are one act: between the insert and the move a
     * reader would see a meal with nothing in it, and between the move and the sweep it would see
     * the meal the rows came out of still standing with nothing in it either.
     */
    @Transaction
    suspend fun gather(meal: MealEntity, itemIds: List<Long>): Long {
        val id = insertMeal(meal)
        moveItemsTo(id, itemIds)
        deleteEmptyMeals()
        return id
    }
}
