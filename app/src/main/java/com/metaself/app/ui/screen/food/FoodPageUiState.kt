package com.metaself.app.ui.screen.food

import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodUse
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.FormReview

/**
 * A food opened for editing, on its own page (D55).
 *
 * @property showErrors false until he has tried to save. A form that complains about empty fields
 *   the moment it opens is a form shouting at somebody who has not done anything yet.
 * @property reviewing a review of this food asked for here, and the groups accepted from it (D54).
 *   Lives and dies with the form: leaving it forgets it.
 * @property unitMeals how many saved meals count this food in units, read when a review's answer
 *   names or renames its unit — said under the unit, since the rename changes what their "2" means
 *   (D54 §12.6).
 */
data class Editing(
    val foodId: Long,
    val form: FoodForm,
    val showErrors: Boolean = false,
    val reviewing: FormReview = FormReview(),
    val unitMeals: Int = 0,
) {
    val errors: Map<FoodField, String> get() = form.errors()

    fun errorFor(field: FoodField): String? = if (showErrors) errors[field] else null
}

/**
 * Deleting one whole food: either the question, or the reason it cannot go (D36).
 *
 * @property food the food as STORED, not whatever is half-typed in its name field, because the stored
 *   food is what goes and the question names what goes.
 */
sealed interface Deleting {
    val food: Food

    /** "Delete it? This cannot be undone." — nothing has happened yet. */
    data class Asking(override val food: Food) : Deleting

    /**
     * A saved meal uses it, so it cannot go, and nothing was asked.
     *
     * Drawn directly above the food's own buttons, where Delete was pressed, never in a slot at the
     * top of the screen: Delete sits at the foot of a long form, and the top is off screen from
     * there, so a refusal drawn up there would make the tap look dead.
     */
    data class Refused(override val food: Food, val sentence: String) : Deleting
}

/**
 * Why a food's page is finished, for the nav host to act on once (D55).
 *
 * The page never navigates itself: it says why it is done, the nav host leaves it, and says so back
 * through [FoodPageViewModel.closed]. One value rather than a callback per way out, so a page that
 * is closing for one reason cannot also close for a second.
 */
sealed interface Closing {

    /** Save was done. */
    data object Saved : Closing

    /** Hidden; the list says so, with a way back (§6). The name is the stored one. */
    data class Hidden(val foodId: Long, val name: String) : Closing

    /** Delete, answered, was done. */
    data object Deleted : Closing

    /** *Join with a duplicate*: the pick is made on the list, for this food (§5). Nothing joined. */
    data class Join(val foodId: Long) : Closing

    /** The food is not stored — never was, or went while the page was open (§7). */
    data object Gone : Closing
}

/**
 * What one food's page is showing.
 *
 * @property food the food as stored, observed: the heading and summary say what is saved, not what
 *   is half-typed. Null before the first read, and when the food is gone.
 * @property use *Where it's used* (§3), observed, so it follows a join or a delete elsewhere.
 * @property editing the boxes and the review. Filled once, from the first read that has the food;
 *   a later change to the stored food never retypes what he has typed.
 * @property deleting the delete question, or its refusal. Null whenever he is not deleting.
 * @property refusal why the last Save was not done, naming what stood in the way.
 * @property failed an action that threw rather than finishing, drawn in the same slot as [refusal]
 *   and never at the same time.
 * @property closing set once when the page is finished; the nav host leaves it and acknowledges.
 */
data class FoodPageUiState(
    val food: Food? = null,
    val use: FoodUse? = null,
    val editing: Editing? = null,
    val deleting: Deleting? = null,
    val refusal: String? = null,
    val failed: ActionRefused? = null,
    val closing: Closing? = null,
)
