package com.metaself.app.ui.screen.mealbuilder

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.Logging
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.FormReview

/**
 * A food picked out of the list, and how much of it is going into the meal.
 *
 * The same question the logging screen asks, for the same reason and with the same answer: only the
 * ways the food actually knows are on offer, and the other is shown with its reason rather than
 * hidden.
 */
data class Adding(
    val food: Food,
    val countedAs: CountedAs,
    val amount: String = "",
    /**
     * What making this food just changed about the figures it already held (D45, issue #13).
     *
     * Defaulted, because only *make a food* can have anything to say and `copy` carries it through
     * the amount and counted-as edits. **It dies with the panel and has no dismiss of its own**:
     * the panel is dismissed by putting the food in or cancelling, and a dismissible notice inside
     * a panel that is already transient would be a second dismiss for one thing.
     */
    val retaughtNotice: String? = null,
    /**
     * The part this panel is changing, when it was opened on a food already in the meal (D53 §7,
     * #4); null for a food being put in. Only the button's words differ: both write through
     * `SavedMealRepository.put`, which changes a food already there in place, so the part keeps its
     * place in the list.
     */
    val changing: Long? = null,
) {
    /** The ceiling on how much of it, by how he is counting: 5000 g, or 100 of them (D42). */
    val most: Double get() = BelievableAmount.amountEaten(countedAs)

    /**
     * The amount to put in: above nothing and not past [most] (D42, issue #32). Past it there is no
     * preview, so an infinite component cannot go in, and "Infinity" on a food with a zero figure no
     * longer throws while the row is drawn (0 × ∞ is no number).
     */
    val amountOrNull: Double?
        get() = typedAmount?.takeIf { it > 0.0 && BelievableAmount.isBelievable(it, most) }

    /**
     * True only for a number past [most], which is what the box says out loud. A blank, a zero, a
     * word or a half-typed "1." just leave the button off, as they always have.
     */
    val amountTooMuch: Boolean
        get() = typedAmount?.let { BelievableAmount.isTooMuch(it, most) } == true

    private val typedAmount: Double? get() = amount.trim().replace(',', '.').toDoubleOrNull()

    val cannotWeigh get() = Logging.canWeigh(food.facts)

    val cannotCount get() = Logging.canCount(food.facts)

    val unitName: String get() = food.facts.perUnit?.unitName ?: FoodFacts.PORTION

    val preview: LoggedFrom.Numbers?
        get() = amountOrNull?.let { Logging.log(food.facts, it, countedAs) as? LoggedFrom.Numbers }

    val canAdd: Boolean get() = preview != null
}

/**
 * A food chosen in the food list, waiting here for an amount.
 *
 * A food knows what it is, not how much of it he puts in a salad — so a food arriving from the list
 * cannot be a component yet: a component needs an amount greater than zero. **Nothing fills one in.**
 * A default typed into a box he then saves is indistinguishable afterwards from a number he measured
 * (D4), so the amount starts empty, and the row shows what the amount so far comes to until he puts
 * it in — by hand, because typing arrives a character at a time and a row that went in the moment it
 * could be costed went in at the first digit.
 *
 * The same questions as [Adding], answered by [Adding] itself: which ways of counting this food
 * supports, why the other one is refused, and what the amount so far comes to. A second copy of that
 * arithmetic is a second chance for the two to disagree.
 */
data class Pending(
    val food: Food,
    val countedAs: CountedAs,
    val amount: String = "",
) {
    private val asAdding: Adding get() = Adding(food = food, countedAs = countedAs, amount = amount)

    val amountOrNull: Double? get() = asAdding.amountOrNull

    val most: Double get() = asAdding.most

    val amountTooMuch: Boolean get() = asAdding.amountTooMuch

    val cannotWeigh get() = asAdding.cannotWeigh

    val cannotCount get() = asAdding.cannotCount

    val unitName: String get() = asAdding.unitName

    val preview: LoggedFrom.Numbers? get() = asAdding.preview

    /** True when this much of this food can be costed, which is when it may be put in. */
    val canAdd: Boolean get() = asAdding.canAdd
}

/**
 * A food being made on the spot, in the meal builder's *Make a food* panel.
 *
 * Held by the view model rather than remembered by the panel, because a review has to outlive its
 * request (D54): the answer lands on this form, or is dropped if the panel has gone.
 *
 * @property showErrors false until he has pressed Make it, for the reason My foods' editor gives.
 * @property reviewing a review asked for in this panel, and the groups accepted from it — handed to
 *   the new food as estimates. Cancel forgets it with the panel.
 */
data class MakingFood(
    val form: FoodForm = FoodForm(),
    val showErrors: Boolean = false,
    val reviewing: FormReview = FormReview(),
) {
    val errors: Map<FoodField, String> get() = form.errors()

    fun errorFor(field: FoodField): String? = if (showErrors) errors[field] else null
}

/**
 * What the meal builder is showing.
 *
 * **There is no draft state and no "finish" button.** A half-built salad is simply a meal with fewer
 * things in it, and it waits — he can name it, put two vegetables in, go away, and come back to add
 * the tahini next week. That costs no flag, no state machine and no query that has to remember to
 * exclude it; the price is that a half-built meal is offered for logging like a finished one, which
 * logs what is actually in it.
 *
 * @property meal null only while a new meal has not been named yet, which is the one thing that has
 *   to happen before anything else can.
 * @property making a food being made on the spot, because the tahini is not in his list yet and
 *   sending him away to make it would lose the salad he is halfway through. Null while the panel is
 *   closed.
 * @property pending the foods he chose in the food list, each waiting for an amount. **Not
 *   persisted**: leaving the builder keeps every component already added and forgets the rest,
 *   because a row with no amount is not yet anything the record could hold.
 * @property alreadyIn foods the search found that the meal already holds — named, never offered,
 *   because a meal holds a food once (D41). Searched over the meal itself rather than over the foods
 *   on offer, so a food hidden since it went in is still named. Empty while the search box is.
 * @property alreadyWaiting foods the search found that are already waiting above for an amount.
 *   Named as that, never as in the meal: a food with no amount is not in it (D37). Empty while the
 *   search box is.
 * @property failed an action that threw rather than finishing, drawn in [refusal]'s slot and never
 *   beside it.
 */
data class MealBuilderUiState(
    val meal: SavedMeal? = null,
    val typedName: String = "",
    val query: String = "",
    val candidates: List<Food> = emptyList(),
    val adding: Adding? = null,
    val pending: List<Pending> = emptyList(),
    val making: MakingFood? = null,
    val refusal: String? = null,
    val alreadyIn: List<Food> = emptyList(),
    val alreadyWaiting: List<Food> = emptyList(),
    val failed: ActionRefused? = null,
) {
    /** True while *Make a food* is open. */
    val creating: Boolean get() = making != null

    /** True before the meal has a name, which is the only gate in this screen. */
    val needsAName: Boolean get() = meal == null

    val canName: Boolean get() = typedName.isNotBlank()

    /**
     * True when he has searched and nothing he has matched — not merely nothing he can add. A food
     * left out because it is already here is named instead (D41); saying nothing matched while it
     * sits a few lines up was the lie of issue #14.
     */
    val searchedAndFoundNothing: Boolean
        get() = query.isNotBlank() && candidates.isEmpty() && alreadyIn.isEmpty() &&
            alreadyWaiting.isEmpty()
}
