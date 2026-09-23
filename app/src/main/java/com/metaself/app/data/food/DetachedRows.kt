package com.metaself.app.data.food

import com.metaself.app.data.day.MealRepository

/**
 * Puts back the link between logged rows and their foods where corrections broke it (issue #22).
 *
 * Until #22 was fixed, correcting any figure on a logged row silently detached it from its food. The
 * fix stops that happening; this repairs what already happened, and is safe to run every time the app
 * opens, because it only ever touches rows attached to nothing and does nothing when there are none.
 *
 * **Conservative on purpose.** A row is put back only when exactly ONE food answers to the name it
 * was typed under, and no food is ever made:
 *
 * - A row whose food was DELETED is also detached, by design, and cannot be told from one a
 *   correction detached. Making a food for it would bring back a food the owner removed. Looking up
 *   instead of making leaves it detached unless a food of that name exists again.
 * - A name two foods share — an unbranded milk and a Dairyco one — leaves the row as it is. The brand
 *   the row was attached under is exactly what the detaching lost, and a guess would put it under the
 *   wrong one.
 *
 * Nothing about a row changes but its link: no number, no name, no day, no time.
 */
object DetachedRows {

    /** Put back every detached row that one food answers to. Returns how many were put back. */
    suspend fun reattach(meals: MealRepository, foods: FoodRepository): Int {
        var put = 0
        meals.rowsWithNoFood().forEach { row ->
            val named = foods.foodIdsNamed(row.name)
            if (named.size == 1) {
                meals.attachRow(row.itemId, named.single())
                put++
            }
        }
        return put
    }
}
