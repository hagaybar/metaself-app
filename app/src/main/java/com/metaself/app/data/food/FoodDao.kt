package com.metaself.app.data.food

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app asks of the food tables.
 *
 * **There is deliberately no `@Update` on [FoodEntity] and no `@Insert` that replaces one.** A
 * food's numbers can be written in exactly three ways — the three guarded statements below, one per
 * fact — and that is what makes "a better number wins" a property of the schema rather than of a
 * caller's good intentions. An entity-wide update would let any caller write all three facts at
 * once with nothing checking where they came from, which is the rule stated and then not enforced.
 *
 * `OnConflictStrategy.REPLACE` would be worse still: in SQLite it is a DELETE followed by an
 * INSERT, so on `foods` it would cascade every alias away, hit the restriction protecting a saved
 * meal's components, and null out the pointer from every logged row — losing the names, detaching
 * the history and silently reverting every past day's label, all for what reads in the code like an
 * ordinary upsert.
 *
 * A trigger would enforce the ranking even against a hand-written statement, and is deliberately not
 * used: Room does not model triggers, so one would have to be created identically in the migration
 * and in a callback for fresh installs, would not appear in the exported schema, and the schema
 * validation would not notice its absence. Belt-and-braces the test suite cannot see is not
 * belt-and-braces. The narrow statements plus the tests are the honest mechanism.
 */
@Dao
interface FoodDao {

    // --- Reading ---------------------------------------------------------------------------------

    /**
     * Every food that is still offered, in the order the owner should see them.
     *
     * **The later of when it was last logged and when it was last edited**, descending. A food can
     * now exist without ever having been eaten — made in the manager, or made on the spot inside a
     * half-built meal — and "most recently logged" would sort such a food last or nowhere, which is
     * precisely where something he just created must not be.
     *
     * When it was last logged is derived rather than cached. A few dozen foods make the join free,
     * and a cached column would be a second truth that can drift. If it ever gets slow the cache is
     * the fix, not the design.
     */
    @Transaction
    @Query(
        "SELECT f.* FROM foods f " +
            "WHERE f.hiddenAtMillis IS NULL " +
            "ORDER BY MAX(f.updatedAtMillis, COALESCE(" +
            "  (SELECT MAX(m.loggedAtMillis) FROM food_items i " +
            "     INNER JOIN meals m ON m.id = i.mealId WHERE i.foodId = f.id), 0)) DESC, " +
            "f.id DESC",
    )
    fun observeOffered(): Flow<List<FoodWithNames>>

    /** Every food, hidden ones included, for the manager — which is where hiding is undone. */
    @Transaction
    @Query("SELECT * FROM foods ORDER BY updatedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<FoodWithNames>>

    @Transaction
    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodWithNames?

    /** The identity rule, asked as a question. Any name the food answers to will find it. */
    @Query("SELECT foodId FROM food_names WHERE nameKey = :nameKey AND brandKey = :brandKey")
    suspend fun foodIdNamed(nameKey: String, brandKey: String): Long?

    /** Every food answering to a name, whatever its brand: see `FoodRepository.foodIdsNamed`. */
    @Query("SELECT DISTINCT foodId FROM food_names WHERE nameKey = :nameKey")
    suspend fun foodIdsNamed(nameKey: String): List<Long>

    @Transaction
    @Query("SELECT * FROM foods WHERE barcode = :barcode")
    suspend fun byBarcode(barcode: String): FoodWithNames?

    /**
     * How many foods know nothing but that one unnamed portion of them had these calories.
     *
     * **Derived, never a stored count.** The conversion could have written the number down once, and
     * it would have gone stale the first time the owner corrected one. Counted from the data, the
     * list shrinks as he fixes them, which is the only version of this that stays true.
     */
    @Query(
        "SELECT COUNT(*) FROM foods " +
            "WHERE kcalPer100g IS NULL AND gramsPerUnit IS NULL AND unitName = 'portion'",
    )
    fun observeOnlyAPortionCount(): Flow<Int>

    @Transaction
    @Query(
        "SELECT * FROM foods " +
            "WHERE kcalPer100g IS NULL AND gramsPerUnit IS NULL AND unitName = 'portion' " +
            "ORDER BY updatedAtMillis DESC",
    )
    fun observeOnlyAPortion(): Flow<List<FoodWithNames>>

    /** Which saved meals use this food, for saying why it cannot be deleted. */
    @Query(
        "SELECT m.name FROM saved_meals m " +
            "INNER JOIN saved_meal_components c ON c.savedMealId = m.id " +
            "WHERE c.foodId = :foodId ORDER BY m.name",
    )
    suspend fun mealsUsing(foodId: Long): List<String>

    // --- Creating --------------------------------------------------------------------------------

    @Insert
    suspend fun insertFood(food: FoodEntity): Long

    @Insert
    suspend fun insertName(name: FoodNameEntity): Long

    // --- The three guarded statements --------------------------------------------------------------

    /**
     * What 100 grams are worth, written only by something at least as credible as what is there.
     *
     * **The `WHERE` clause is the rule.** A guess arriving at a food the owner typed updates no
     * rows; a scan arriving at the same food updates one, silently, which is what was asked for.
     * `>=` rather than `>` so a fresh scan replaces an older scan and a re-typed number replaces an
     * older typed one. The `IS NULL` arm is what lets a fact be learned for the first time by
     * anything at all.
     *
     * **Not the signal for "say something changed" (D45, issue #13).** 0 rows is exactly the silent
     * case — the food kept what it had — and 1 row does not mean a figure moved, since an identical
     * figure passes the `>=` guard and is written again. What was replaced is decided by comparing
     * `FoundOrCreated.before` with what the food holds afterwards, never by this count.
     *
     * @return how many rows changed — 0 meaning the food already knew better.
     */
    @Query(
        "UPDATE foods SET " +
            "kcalPer100g = :kcal, proteinPer100g = :protein, " +
            "carbsPer100g = :carbs, fatPer100g = :fat, " +
            "per100gSource = :source, per100gSourceRank = :rank, " +
            "per100gConfidence = :confidence, per100gSetAtMillis = :nowMillis, " +
            "updatedAtMillis = :nowMillis " +
            "WHERE id = :id AND (per100gSourceRank IS NULL OR :rank >= per100gSourceRank)",
    )
    suspend fun writePer100g(
        id: Long,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        source: String,
        rank: Int,
        confidence: String?,
        nowMillis: Long,
    ): Int

    /**
     * What one of it is worth. The same shape against its own columns, and **it writes no column
     * belonging to another fact** — which is how "a scan never touches what a unit weighs" is
     * expressed as a narrow statement rather than as a rule somebody has to remember.
     */
    @Query(
        "UPDATE foods SET " +
            "unitName = :unitName, kcalPerUnit = :kcal, proteinPerUnit = :protein, " +
            "carbsPerUnit = :carbs, fatPerUnit = :fat, " +
            "perUnitSource = :source, perUnitSourceRank = :rank, " +
            "perUnitConfidence = :confidence, perUnitSetAtMillis = :nowMillis, " +
            "updatedAtMillis = :nowMillis " +
            "WHERE id = :id AND (perUnitSourceRank IS NULL OR :rank >= perUnitSourceRank)",
    )
    suspend fun writePerUnit(
        id: Long,
        unitName: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        source: String,
        rank: Int,
        confidence: String?,
        nowMillis: Long,
    ): Int

    /**
     * What one of it weighs — the fact nothing may compute and the barcode path never writes.
     *
     * The ranking says a label beats a number the owner typed, which for a weight is arguably
     * backwards: a bar on a kitchen scale beats a packet's declared serving. In practice the
     * question does not arise, because nothing on the barcode path writes this at all, so the value
     * is only ever typed or estimated and the ranking is right for those. Recorded here so nobody
     * later wires a serving size into it and quietly inverts this.
     */
    @Query(
        "UPDATE foods SET " +
            "gramsPerUnit = :grams, gramsPerUnitSource = :source, " +
            "gramsPerUnitSourceRank = :rank, gramsPerUnitConfidence = :confidence, " +
            "gramsPerUnitSetAtMillis = :nowMillis, updatedAtMillis = :nowMillis " +
            "WHERE id = :id AND (gramsPerUnitSourceRank IS NULL OR :rank >= gramsPerUnitSourceRank)",
    )
    suspend fun writeGramsPerUnit(
        id: Long,
        grams: Double,
        source: String,
        rank: Int,
        confidence: String?,
        nowMillis: Long,
    ): Int

    // --- Correcting, which is the owner's own hand and beats the guard ---------------------------

    /**
     * Empty a number group outright.
     *
     * Not guarded, because this is the owner in the manager saying the number is wrong and he has
     * nothing to put in its place — a guard that refused him would be the ranking applied to the
     * person it exists to protect. Whether a saved meal depends on the group is checked above this,
     * by name, before it is called.
     */
    @Query(
        "UPDATE foods SET kcalPer100g = NULL, proteinPer100g = NULL, carbsPer100g = NULL, " +
            "fatPer100g = NULL, per100gSource = NULL, per100gSourceRank = NULL, " +
            "per100gConfidence = NULL, per100gSetAtMillis = NULL, updatedAtMillis = :nowMillis " +
            "WHERE id = :id",
    )
    suspend fun clearPer100g(id: Long, nowMillis: Long)

    @Query(
        "UPDATE foods SET unitName = NULL, kcalPerUnit = NULL, proteinPerUnit = NULL, " +
            "carbsPerUnit = NULL, fatPerUnit = NULL, perUnitSource = NULL, " +
            "perUnitSourceRank = NULL, perUnitConfidence = NULL, perUnitSetAtMillis = NULL, " +
            "updatedAtMillis = :nowMillis WHERE id = :id",
    )
    suspend fun clearPerUnit(id: Long, nowMillis: Long)

    @Query(
        "UPDATE foods SET gramsPerUnit = NULL, gramsPerUnitSource = NULL, " +
            "gramsPerUnitSourceRank = NULL, gramsPerUnitConfidence = NULL, " +
            "gramsPerUnitSetAtMillis = NULL, updatedAtMillis = :nowMillis WHERE id = :id",
    )
    suspend fun clearGramsPerUnit(id: Long, nowMillis: Long)

    // --- Names, brands, hiding, deleting ------------------------------------------------------------

    @Query("UPDATE food_names SET displayName = :displayName, nameKey = :nameKey WHERE id = :id")
    suspend fun renameNameRow(id: Long, displayName: String, nameKey: String)

    @Query("UPDATE foods SET brand = :brand, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun setBrand(id: Long, brand: String, nowMillis: Long)

    /**
     * Editing a brand rewrites the key on every one of the food's names, in the same transaction.
     *
     * The cost of putting the brand key on the name row — which is where it has to be, because the
     * identity rule spans a name and a brand and a unique index cannot span two tables.
     */
    @Query("UPDATE food_names SET brandKey = :brandKey WHERE foodId = :foodId")
    suspend fun setBrandKeyOnNames(foodId: Long, brandKey: String)

    @Query("UPDATE foods SET barcode = :barcode, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun setBarcode(id: Long, barcode: String?, nowMillis: Long)

    @Query("UPDATE foods SET hiddenAtMillis = :hiddenAtMillis, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun setHidden(id: Long, hiddenAtMillis: Long?, nowMillis: Long)

    /**
     * Mark the food as edited without changing anything it knows.
     *
     * A rename and a merge both change the food while moving not one number, and both should send
     * it to the top of the list — the ordering is the later of when it was last logged and when it
     * was last edited, and those are edits.
     */
    @Query("UPDATE foods SET updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun touch(id: Long, nowMillis: Long)

    /**
     * Delete a food outright.
     *
     * Its names go with it. **Every day it was ever eaten keeps its own name, portion, numbers,
     * source and confidence** and simply stops pointing anywhere — which is the state every row was
     * in before any of this existed. A food a saved meal uses cannot be deleted at all: the database
     * refuses, and the app offers to hide it instead or names the meals that use it.
     */
    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: Long)

    // --- Merging ------------------------------------------------------------------------------------

    @Query("UPDATE food_items SET foodId = :winner WHERE foodId = :loser")
    suspend fun movePastRows(winner: Long, loser: Long)

    @Query("UPDATE saved_meal_components SET foodId = :winner WHERE foodId = :loser")
    suspend fun moveMealComponents(winner: Long, loser: Long)

    /**
     * The loser's names become the winner's, and none of them is preferred.
     *
     * **This is the step that makes merging worth having.** The Hebrew name becomes an alias of the
     * English food, so the next log in Hebrew finds the one food. Without it, merging is a
     * treadmill: join them today, log in Hebrew tomorrow, and there are two again.
     */
    @Query("UPDATE food_names SET foodId = :winner, isPreferred = 0 WHERE foodId = :loser")
    suspend fun moveNames(winner: Long, loser: Long)

    /** Which foods a merge would put twice into one meal, which has to be settled before it runs. */
    @Query(
        "SELECT m.name FROM saved_meals m " +
            "INNER JOIN saved_meal_components a ON a.savedMealId = m.id AND a.foodId = :winner " +
            "INNER JOIN saved_meal_components b ON b.savedMealId = m.id AND b.foodId = :loser",
    )
    suspend fun mealsHoldingBoth(winner: Long, loser: Long): List<String>
}
