package com.metaself.app.data.food

import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodUse
import kotlinx.coroutines.flow.Flow

/**
 * What happened when a food was asked for: it was already there, or it has just been made.
 *
 * @property before what the food held immediately BEFORE this call, read inside the same
 *   transaction that then offered it the caller's facts. Null when this call made the food, and
 *   null when an existing food held nothing — both mean the same thing to the comparison that
 *   reads it. **No default value**, deliberately (issue #13): the repository reports a snapshot and
 *   decides nothing, and every implementation has to fill this in rather than keep compiling while
 *   saying nothing.
 */
data class FoundOrCreated(val food: Food, val wasCreated: Boolean, val before: FoodFacts?)

/** Why an edit was refused, so the screen can say which rather than just failing. */
sealed interface EditRefused {

    /**
     * A food a saved meal uses cannot be deleted out from under it.
     *
     * The database refuses it; this is that refusal with the meals named, so the owner can go and
     * change them, or hide the food instead.
     */
    data class UsedBySavedMeals(val meals: List<String>) : EditRefused

    /**
     * Emptying this group would leave a saved meal unable to cost itself — "Vegetable salad counts
     * this in slices".
     *
     * A visible refusal at the moment he does it, rather than a silent hundredfold error found a
     * month later.
     */
    data class NeededBySavedMeals(val meals: List<String>) : EditRefused

    /**
     * The new name and brand already belong to another food.
     *
     * Never a silent overwrite. Putting a real brand on a food changes its identity, and if that
     * identity is taken, the honest answers are to merge the two or to leave it alone — both of
     * which are the owner's to choose.
     */
    data class AlreadyAnotherFood(val existingFoodId: Long, val name: String) : EditRefused

    /** A food must know at least one of the two ways of counting, or it is a name with nothing behind it. */
    data object WouldLeaveNothingKnown : EditRefused

    /** A merge would put the same food into one meal twice, which has to be settled first. */
    data class MealsHoldingBoth(val meals: List<String>) : EditRefused
}

/** An edit either happened, or was refused with a reason worth showing. */
sealed interface EditResult {
    data object Done : EditResult
    data class Refused(val why: EditRefused) : EditResult
}

/**
 * Everything the app does to foods.
 *
 * **[findOrCreate] is the only way a food may come into existence.** There are two entrances —
 * logging something, and making one in a manager — and a third will be added one day. They must
 * normalise identically and must find the same existing food rather than make a second one. Three
 * things make that true, in this order: one pure object holding the naming rule, this one method,
 * and the unique index underneath that refuses a duplicate even if somebody writes an insert by
 * hand. Convention, then one door, then a lock.
 */
interface FoodRepository {

    /** Every food still offered, most recently used or edited first. */
    fun observeOffered(): Flow<List<Food>>

    /** Every food, hidden ones included — the manager is where hiding is undone. */
    fun observeAll(): Flow<List<Food>>

    /** How many foods know nothing but that one unnamed portion of them had these calories. */
    fun observeOnlyAPortionCount(): Flow<Int>

    fun observeOnlyAPortion(): Flow<List<Food>>

    suspend fun byId(id: Long): Food?

    suspend fun byBarcode(barcode: String): Food?

    /**
     * Find the food this name and brand mean, or make it.
     *
     * Whatever facts the caller brings are offered to the food through the guarded statements, so a
     * guess arriving at a number the owner typed changes nothing and a packet's figure arriving at
     * the same food wins. Bringing a fact the food already knows better is not an error; it simply
     * does not happen.
     *
     * **It no longer wins silently** (D45, issue #13): [FoundOrCreated.before] carries what the
     * food held immediately before this call, so a caller can say which figure has been replaced.
     * Which figure wins is unchanged.
     */
    suspend fun findOrCreate(
        name: String,
        brand: String? = null,
        facts: FoodFacts,
        barcode: String? = null,
    ): FoundOrCreated

    /**
     * Offer a fact to a food already known, by the same rules.
     *
     * Nothing in the app calls this today — every door goes through [findOrCreate] — and it reports
     * nothing on purpose: a return value for no caller is exactly the result somebody forgets to
     * read. A caller that needs to know what it replaced should use [findOrCreate] and its
     * [FoundOrCreated.before] (issue #13).
     */
    suspend fun offerFacts(foodId: Long, facts: FoodFacts)

    /** Rename the food. Every day it was ever eaten says the new name; not one number moves. */
    suspend fun rename(foodId: Long, newName: String): EditResult

    /** Putting a real brand on a food splits it away from the plain one, which is correct. */
    suspend fun setBrand(foodId: Long, brand: String?): EditResult

    /**
     * The owner's own correction, which is not subject to the ranking: a group that changed
     * replaces what was there, whatever its rank; a group that did not change is not touched, so it
     * keeps its source, confidence and date (D54). Which is which is decided by `Correction`.
     */
    suspend fun correct(foodId: Long, facts: FoodFacts): EditResult

    /**
     * The food form's Save: [rename], [setBrand] and [correct], in that order, as one change.
     *
     * All or nothing. The first refusal is returned unchanged and undoes whatever the steps before
     * it had done, and so does an exception — so a Save that did not finish changed nothing, which
     * is what the screen then says.
     */
    suspend fun saveForm(foodId: Long, name: String, brand: String?, facts: FoodFacts): EditResult

    suspend fun hide(foodId: Long)

    suspend fun unhide(foodId: Long)

    suspend fun delete(foodId: Long): EditResult

    /**
     * The saved meals that use this food, by name.
     *
     * Asked BEFORE the owner is offered the chance to delete it, so he is never asked "Delete it?"
     * when the only possible answer is a refusal (D36). [delete] still refuses on its own: this check
     * is for the owner, the one inside the delete is for the data, and a meal could start using the
     * food between the question and the answer.
     */
    suspend fun savedMealsUsing(foodId: Long): List<String>

    /**
     * Where this food is used: how many logged rows point at it, and the saved meals holding it
     * (D55 §3). The meals come from the statement [savedMealsUsing] asks, so the page and the delete
     * refusal name the same ones.
     *
     * **Observed, not read once**: a count read once goes stale the first time the record moves
     * under it — a row deleted from a day, a join onto this food, a meal changed.
     */
    fun observeUse(foodId: Long): Flow<FoodUse>

    /**
     * Join two foods the owner has decided are one thing.
     *
     * The history moves across and **not one logged row's numbers, name, portion, source or
     * confidence is touched**: merging is about identity, not about rewriting the past. The loser's
     * names become the winner's aliases, which is what stops this being a treadmill.
     *
     * The winner's own numbers are not touched either. If the loser knew something better, that is a
     * separate and visible choice — silently preferring one side's numbers during an identity
     * operation would be applying the ranking where it was not asked for.
     */
    suspend fun merge(winnerId: Long, loserId: Long): EditResult

    /**
     * Every food that answers to [name], under any brand and by any of its names — looked up, never
     * made (issue #22).
     *
     * More than one is a real answer, not an error: an unbranded milk and a Dairyco milk are two foods
     * with one name, and a caller that needs a single food must decline to choose between them.
     */
    suspend fun foodIdsNamed(name: String): List<Long>

    /**
     * Clear every number group a food holds that today's rule would refuse — an infinite, negative
     * or absurd figure saved before 0.32.6 (D42) — and nothing else (issue #7).
     *
     * **Cleared, not flagged**, the owner's choice: a blank already means "not known" everywhere
     * (D4), so the food keeps its other groups and the box waits for him to fill it. A food left
     * knowing nothing at all is not deleted — its logged rows still point at it — but reads as no
     * food, so it leaves the lists until something teaches it a figure again under the same name.
     * No logged row is touched: what a past day holds is the record, and D42 has it keep its figures.
     *
     * One transaction, and idempotent: a second call finds nothing. Clearing is not an edit he made,
     * so a food does not move up the list for it.
     *
     * @return how many groups were cleared, across every food.
     */
    suspend fun clearImpossibleFigures(): Int
}
