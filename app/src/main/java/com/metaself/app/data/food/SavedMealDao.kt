package com.metaself.app.data.food

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app asks of the saved-meal tables.
 *
 * A saved meal is only ever read with what is in it, so every read is a `@Transaction` over the
 * relation: reading the definition and its parts in two goes would let a reader see a salad that
 * has just lost its oil and not yet gained its tahini.
 */
@Dao
interface SavedMealDao {

    /**
     * Every meal still offered, most recently used or edited first.
     *
     * The same ordering a food gets and for the same reason: a meal can exist before it has ever
     * been logged — he can build one now and eat it next week — and "most recently logged" would
     * sort the one he has just built nowhere.
     */
    @Transaction
    @Query(
        "SELECT s.* FROM saved_meals s " +
            "WHERE s.hiddenAtMillis IS NULL " +
            "ORDER BY MAX(s.updatedAtMillis, COALESCE(" +
            "  (SELECT MAX(m.loggedAtMillis) FROM meals m WHERE m.savedMealId = s.id), 0)) DESC, " +
            "s.id DESC",
    )
    fun observeOffered(): Flow<List<SavedMealWithComponents>>

    @Transaction
    @Query("SELECT * FROM saved_meals WHERE id = :id")
    suspend fun byId(id: Long): SavedMealWithComponents?

    @Query("SELECT id FROM saved_meals WHERE nameKey = :nameKey")
    suspend fun idNamed(nameKey: String): Long?

    @Insert
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Insert
    suspend fun insertComponent(component: SavedMealComponentEntity): Long

    @Query("UPDATE saved_meals SET name = :name, nameKey = :nameKey, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun rename(id: Long, name: String, nameKey: String, nowMillis: Long)

    @Query("UPDATE saved_meals SET updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun touch(id: Long, nowMillis: Long)

    @Query("UPDATE saved_meals SET hiddenAtMillis = :hiddenAtMillis, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun setHidden(id: Long, hiddenAtMillis: Long?, nowMillis: Long)

    @Query(
        "UPDATE saved_meal_components SET amount = :amount, countedAs = :countedAs " +
            "WHERE id = :id",
    )
    suspend fun setAmount(id: Long, amount: Double, countedAs: String)

    @Query("UPDATE saved_meal_components SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM saved_meal_components WHERE id = :id")
    suspend fun deleteComponent(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM saved_meal_components WHERE savedMealId = :mealId")
    suspend fun nextPosition(mealId: Long): Int

    @Query("SELECT id FROM saved_meal_components WHERE savedMealId = :mealId AND foodId = :foodId")
    suspend fun componentFor(mealId: Long, foodId: Long): Long?

    /**
     * Deleting the meal deletes its parts list, and touches no food and no past day.
     *
     * A day that was logged from this meal loses its title and shows its items, which is what every
     * day looked like before meals he built existed.
     */
    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)
}
