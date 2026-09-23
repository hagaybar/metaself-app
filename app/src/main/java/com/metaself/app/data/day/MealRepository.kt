package com.metaself.app.data.day

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import kotlinx.coroutines.flow.Flow

/**
 * The meals of a day, and the two things step 3 can do to them.
 *
 * An interface with one implementation, for the reason decision D2 gives about the AI client: what
 * is behind it is expected to change — an export, a Drive restore, a different store entirely — and
 * the screens should not have to.
 */
interface MealRepository {

    fun observeDay(epochDay: Long): Flow<List<Meal>>

    /** Every calendar day holding at least one thing eaten, for the streak (D13). */
    fun observeLoggedDays(): Flow<Set<Long>>

    /** What each day held in calories since a date, for measuring what a day costs (D25). */
    suspend fun kcalByDaySince(fromEpochDay: Long): Map<Long, Int>

    /**
     * Write a meal down, and hand back the ids of the rows it wrote, in [Meal.items] order.
     *
     * The ids are what lets the caller act on **exactly these rows** a moment later — gathering what
     * was just described into a meal he names there and then (D46, issue #24). Read back off the day
     * instead they could not be told from rows logged earlier in the same second, and the meal would
     * quietly swallow them. Every caller that only logs ignores the answer.
     */
    suspend fun log(meal: Meal): List<Long>

    /**
     * Correct one item already stored.
     *
     * Whatever the owner changes, the item keeps its identity, its meal, its day and — critically —
     * its source and confidence. Correcting a model's guess does not turn it into a measurement;
     * decision D4 is about what the record may claim.
     */
    suspend fun updateItem(item: FoodItem)

    /**
     * Delete one row — and its meal, if that was the meal's last row — and hand back what was taken.
     *
     * The receipt is read in the same transaction as the delete, because the row's id is gone the
     * moment the row is, and the meal may go with it. It carries the meal even when the meal
     * survives, because by the time the owner presses Undo it may not have. Null when there was no
     * such row.
     */
    suspend fun deleteItem(itemId: Long): DeletedEntry?

    /**
     * Put back what [deleteItem] took, as it was (issue #25): the row under its own id, in its own
     * meal, so it is back at the time it was eaten, on the day it was eaten.
     *
     * The world may have moved between the delete and the undo, and each way has one answer:
     *
     * - **The row is already there** — Undo applied twice — and nothing happens.
     * - **Its meal is still there**, and the row goes back into it. The meal itself is not written:
     *   its time, note and link now are the owner's latest word on it, and the receipt's are older.
     * - **Its meal is gone**, and it comes back from the receipt under its own id, with its day, its
     *   time, its note, its saved-meal link and its "adjusted" flag.
     * - **Its food has been deleted**, and the row comes back attached to nothing — exactly what
     *   deleting a food did to every other row of it.
     * - **Its food lost a merge**, and the row likewise comes back attached to nothing, not to the
     *   food that won: the loser no longer exists and nothing records where it went. The startup
     *   repair that reattaches detached rows by name (issue #22) may then attach it to the winner.
     * - **Its saved meal has been deleted**, and the meal comes back unlinked with its "adjusted"
     *   flag as it was — exactly what deleting a saved meal did to every other meal from it.
     *
     * **Restoring is not logging.** No food is found, created, attached or offered any figures: the
     * row was resolved, or deliberately left unresolved, when it was first logged, and every stored
     * column comes back as it was, source and confidence included (D4).
     *
     * A receipt taken before a backup restore would bring its meal back beside the restored copy,
     * because a backup re-inserts every meal under a new id. Not reachable — Undo lives on the day
     * screen, and restoring a backup means leaving it — so accepted rather than guarded.
     */
    suspend fun restore(entry: DeletedEntry)

    /**
     * Say when a logged meal was eaten (D33).
     *
     * The whole meal moves, because the time belongs to the meal and its rows carry none of their
     * own: a salad was eaten at one time. The day it is on does not change, and nothing about what
     * was eaten does — only when. The caller keeps the time inside that day and not in the future.
     */
    suspend fun setEatenAt(mealId: Long, atMillis: Long)

    /**
     * Every logged row attached to no food, as its id and the name it was typed under (issue #22).
     *
     * Corrections detached rows until #22 was fixed, and deleting a food detaches its rows by
     * design. A scan whose label figure is no quantity of food logs its row detached when no food
     * of that barcode, or that name and brand, exists yet (#28) — the next scan of that packet with
     * real figures makes the food. Such rows could be logged only from 0.32.1 until D39 (#31,
     * 0.32.2) refused those packets at the door; older ones stay as they are. This is what the
     * repair reads; see [com.metaself.app.data.food.DetachedRows].
     */
    suspend fun rowsWithNoFood(): List<DetachedRow>

    /**
     * Attach one detached row to a food, and only if it is still detached.
     *
     * Nothing about the row changes but the link: no number, no name, no day. A row that has found a
     * food by some other route in the meantime is left with it.
     */
    suspend fun attachRow(itemId: Long, foodId: Long)

    /**
     * Gather rows already logged under a meal the owner has just named (design §3.5).
     *
     * **Not one stored number moves.** The rows keep their calories, their macros, their portion,
     * their source and their confidence; what changes is which meal they belong to, so the day shows
     * them as one titled row instead of several. The day's total is the same number afterwards,
     * because it is the sum of the same rows.
     *
     * The new meal sits where the earliest row in it already sat, rather than at the end of the day:
     * grouping is a labelling act and not a claim about when anything was eaten.
     *
     * A meal left with nothing in it goes, because a meal with nothing in it is not a record of
     * anything. That is what makes this the one irreversible act on this screen.
     */
    suspend fun gatherIntoSavedMeal(epochDay: Long, itemIds: List<Long>, savedMealId: Long)
}

/** A logged row attached to no food: which row, and the name it was typed under. */
data class DetachedRow(val itemId: Long, val name: String)

/**
 * What deleting one row took away: the row and the meal it was in, exactly as stored (issue #25).
 *
 * Stored rows rather than the domain `Meal` and `FoodItem`, because reading the domain form is lossy
 * in exactly the fields D4 protects: a source this version does not know reads as UNRECOGNISED, a
 * negative number as 0, a blank name as "(unnamed)". Written back from that, an undo would change
 * what the record claims about where a number came from.
 *
 * A receipt: the caller holds it and hands it back to [MealRepository.restore], and never reads
 * inside it. The day it goes back to travels inside [meal].
 */
data class DeletedEntry(val meal: MealEntity, val item: FoodItemEntity)
