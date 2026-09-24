package com.metaself.app.ui.screen.foods

import com.metaself.app.domain.food.Food
import com.metaself.app.ui.ActionRefused

/**
 * Joining two foods the owner has decided are one thing.
 *
 * @property keeping the food that survives. It keeps its name, its numbers and its brand; the other
 *   one's names become its aliases, so logging under either afterwards finds the one food. Stated
 *   this way round, and on screen, because a merge is not reversible and "which one wins" is not
 *   something to leave him guessing at.
 * @property losing the food it absorbs, once it is known. Null only while he is still picking it off
 *   the list — the way in from a single food's page (D55 §5). Set as soon as the other food is known,
 *   whether picked from the list or ticked beside the first, so both ways in end on the same
 *   question with the same answers (D36): the same irreversible act asks the same thing. Held rather
 *   than acted on at once because a merge cannot be undone, and a pick is one tap on a long list.
 */
data class Merging(val keeping: Food, val losing: Food? = null) {

    /**
     * True when both foods are settled — picked or ticked — so the question is "shall I?" rather
     * than "which?".
     */
    val bothChosen: Boolean get() = losing != null
}

/**
 * What the food list is showing: finding, choosing, and joining two duplicates into one. A food's
 * own page holds the rest (D55).
 *
 * @property onlyAPortionCount how many foods know nothing but that one unnamed portion of them had
 *   these calories — the ones the conversion could say least about. Derived from the data rather
 *   than counted once, so the number falls as he fixes them instead of going stale the first time
 *   he does.
 * @property refusal why the last thing he tried was not done. Always a sentence naming what stood in
 *   the way — the meals that use a food, or the food that already holds a name — because a refusal
 *   he cannot act on is just a failure.
 * @property chosen the foods he has picked out of the list, by id, to make a meal from or to join.
 *   Ids rather than foods, so a search that changes the list cannot lose them: he may be collecting
 *   the parts of a salad from three separate searches, which is the whole point of choosing rather
 *   than adding one at a time.
 * @property failed an action that threw rather than finishing, drawn in the same slot as [refusal]
 *   and never at the same time: there is nothing to act on, only the fact, and where it was written
 *   down.
 * @property hid a food its page has just hidden, as it is stored now, said at the top of the list
 *   with Show again (D55 §6). Null once answered, and on any search, filter, choosing or opening.
 */
data class FoodsUiState(
    val query: String = "",
    val onlyPortions: Boolean = false,
    val showHidden: Boolean = false,
    val foods: List<Food> = emptyList(),
    val onlyAPortionCount: Int = 0,
    val hiddenCount: Int = 0,
    val merging: Merging? = null,
    val refusal: String? = null,
    val chosen: Set<Long> = emptySet(),
    val failed: ActionRefused? = null,
    val hid: Food? = null,
) {
    /**
     * True while the list is in choosing mode, which is simply "something is chosen".
     *
     * Derived rather than stored beside the set, like the two below: a flag written down next to the
     * set can disagree with it, and then one part of the screen offers a meal while another shows
     * nothing ticked.
     */
    val choosing: Boolean get() = chosen.isNotEmpty()

    /** Merging is pairwise and stays pairwise, so two is the only size it is offered at. */
    val canJoin: Boolean get() = chosen.size == 2

    /** A meal is two foods or more. One food with an amount is a thing he logs, not a meal. */
    val canMakeAMeal: Boolean get() = chosen.size >= 2

    /** True when he has searched and nothing came back, which needs saying rather than showing. */
    val searchedAndFoundNothing: Boolean get() = query.isNotBlank() && foods.isEmpty()

    /**
     * True before there is anything to manage at all.
     *
     * [hiddenCount] is part of it because hiding every food used to satisfy the other three, and the
     * screen answered by drawing "No foods yet" and returning — above the Show-hidden chip, the one
     * control that could have brought them back. An empty list with something hidden behind it is
     * not an empty list; it is [everythingHidden].
     */
    val nothingAtAll: Boolean
        get() = query.isBlank() && !onlyPortions && foods.isEmpty() && hiddenCount == 0

    /** True when the filter is on and has nothing left to show, which is the good outcome. */
    val nothingLeftToFix: Boolean get() = onlyPortions && foods.isEmpty()

    /**
     * Every food there is has been hidden, and nothing is filtering or searching them away.
     *
     * Said out loud where the list would be, because the list being empty is otherwise indistinguishable
     * from having no foods, and the way out — the Show-hidden chip just above — has to be pointed at.
     */
    val everythingHidden: Boolean
        get() = query.isBlank() && !onlyPortions && !showHidden && foods.isEmpty() && hiddenCount > 0
}
