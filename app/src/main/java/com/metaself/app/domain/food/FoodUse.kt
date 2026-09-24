package com.metaself.app.domain.food

/**
 * Where a food is used, for its page's *Where it's used* (D55 §3).
 *
 * @property logged how many logged rows point at this food, on every day, past and future, however
 *   they were logged — a part of a saved meal logged whole is its own row, and two rows of it in
 *   one meal are two. **Exact**: no rounding and no cap, because it is a count of the record. A row
 *   attached to no food (its food deleted) is nobody's; after a join, the absorbed food's rows point
 *   at the food kept and are counted there.
 * @property savedMeals the saved meals that have this food as a part, by name, in name order — the
 *   same statement the delete refusal names them from, so the two cannot disagree. A saved meal's
 *   own hidden stamp is not consulted: a hidden meal would still hold the food.
 */
data class FoodUse(val logged: Int, val savedMeals: List<String>)
