package com.metaself.app.domain.day

/**
 * One logging event: a moment, and the things eaten at it.
 *
 * Two levels rather than a flat list of foods because "chicken shawarma in a pita, hummus, side
 * salad" is one meal and three items, each with its own numbers and its own record of where they
 * came from. Step 3 only ever creates one-item meals — typing is one thing at a time — but step 7's
 * model proposes several from one sentence, and adding the parent afterwards would mean migrating a
 * database that by then holds real food.
 *
 * A meal cannot be empty. Deleting the last item deletes the meal, because a meal with nothing in it
 * is not a record of anything.
 *
 * @property epochDay the local calendar date, as days since 1970-01-01. A date rather than a
 *   timestamp because "what did I eat on Tuesday" is a question about the calendar, and because a
 *   timestamp would make the answer depend on where the owner was standing.
 * @property savedMealId which meal the owner built this logging came from, when it came from one.
 * @property savedMealAdjusted whether what was logged differed from that meal's definition **as it
 *   stood at that moment** — a fact known exactly then and never again, because the definition may
 *   be edited afterwards. Written once, never updated.
 *
 *   **It must never be counted, aggregated, or used to offer to change a meal.** Nothing learns
 *   from what the owner does: drop the oil every day for a month and the salad still has oil in it.
 *   Its only reader is whatever labels the day's row.
 */
data class Meal(
    val id: Long = 0,
    val epochDay: Long,
    val loggedAtMillis: Long,
    val note: String? = null,
    val items: List<FoodItem>,
    val savedMealId: Long? = null,
    val savedMealName: String? = null,
    val savedMealAdjusted: Boolean = false,
) {
    init {
        require(items.isNotEmpty()) { "a meal cannot be empty" }
    }

    /**
     * What this logging is called, when it came from a meal the owner built.
     *
     * Read through the pointer, so a meal renamed tomorrow retitles every day it was ever eaten —
     * the same answer a food gets, which is what stops the two behaving differently for no reason.
     * Null for everything logged that did not come from a built meal.
     */
    val title: String? get() = savedMealName
}
