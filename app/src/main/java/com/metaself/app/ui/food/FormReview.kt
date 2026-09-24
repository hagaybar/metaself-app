package com.metaself.app.ui.food

import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodForm

/** Where a review asked for in a food editor has got to (D54 §4). */
sealed interface Review {

    /**
     * The request is out and nothing has come back. [withdrawn] are the groups he has typed in
     * since he asked: he is answering those himself, so what comes back for them is not shown.
     */
    data class Asking(val withdrawn: Set<FactGroup> = emptySet()) : Review

    /**
     * The answer, as far as it is still to be acted on: a group he has accepted or typed in is
     * taken out of it.
     *
     * @property nothingSuggested the answer itself changed nothing, which is a real answer and is
     *   said: *No changes suggested.*
     * @property unusable the answer arrived, and every group it changed was set aside: said as
     *   that, above its set-aside lines — never as an answer that could not be understood (§8.3).
     */
    data class Shown(
        val review: FoodReview,
        val nothingSuggested: Boolean = false,
        val unusable: Boolean = false,
    ) : Review
}

/**
 * A food editor's review and what he has accepted from it this editing session — the one piece
 * both editors hold, My foods' and the meal builder's *Make a food* (D54 §4), so the two cannot
 * drift in what typing, accepting or dismissing does.
 *
 * Pure: every step returns a new value, and nothing here asks the model or writes anything. Save is
 * still the only thing that writes, handing [accepted] to `FoodForm.toFacts`.
 *
 * @property accepted each group accepted from a review, with that review's confidence for it and
 *   where the figures it kept came from, so Save can label it by its weakest member. Kept
 *   when he then types in the group — it is still a mix with a guess in it (D54 §5) — and when he
 *   dismisses what is left of the review.
 */
data class FormReview(
    val review: Review? = null,
    val accepted: Map<FactGroup, AcceptedGroup> = emptyMap(),
) {
    /** True while a request is out, when the button reads *Reviewing…* and does nothing. */
    val asking: Boolean get() = review is Review.Asking

    /** A request has gone. Whatever was shown is taken down; what was accepted stays accepted. */
    fun asked(): FormReview = copy(review = Review.Asking())

    /**
     * The answer has come back. A group he has typed in meanwhile is left out of it. If that leaves
     * nothing of an answer that did suggest something — no suggestion, no set-aside line, no note —
     * nothing is shown: "No changes suggested" would not be true. A note keeps it up, as it does
     * once the answer is shown.
     */
    fun answered(answer: FoodReview): FormReview {
        val withdrawn = (review as? Review.Asking)?.withdrawn.orEmpty()
        val nothingSuggested = answer.per100g == null && answer.perUnit == null &&
            answer.setAside.isEmpty()
        val left = withdrawn.fold(answer) { it, group -> it.without(group) }
        val nothingLeft = left.per100g == null && left.perUnit == null && left.setAside.isEmpty() &&
            left.note.isNullOrBlank()
        return copy(
            review = if (nothingLeft && !nothingSuggested) null else Review.Shown(left, nothingSuggested),
        )
    }

    /**
     * The answer arrived and nothing in it could be used ([com.metaself.app.domain.ai.ReviewResult.Unusable]):
     * it is shown as that, with what was set aside and its note, until dismissed. Nothing to accept.
     */
    fun unusable(answer: FoodReview): FormReview = copy(review = Review.Shown(answer, unusable = true))

    /** The request failed, or was dropped: nothing is shown, and what was accepted stays. */
    fun failed(): FormReview = copy(review = null)

    /**
     * He typed, from [before] to [after]. **Typing in a group withdraws its suggestion**, whether it
     * has arrived or is still out: he is answering that group himself. The per-one group includes
     * its unit name — a suggestion for one bowl is no answer for one cup. **Changing the name or
     * brand withdraws both groups**: the review was of the food as it was called when he asked, and
     * is no answer for another one.
     */
    fun typed(before: FoodForm, after: FoodForm): FormReview {
        val renamed = before.name.trim() != after.name.trim() || before.brand.trim() != after.brand.trim()
        val touched = if (renamed) {
            FactGroup.values().toSet()
        } else {
            FactGroup.values().filter { boxes(before, it) != boxes(after, it) }.toSet()
        }
        if (touched.isEmpty()) return this
        return when (val now = review) {
            null -> this
            is Review.Asking -> copy(review = Review.Asking(now.withdrawn + touched))
            is Review.Shown -> copy(review = now.minus(touched))
        }
    }

    /**
     * **Use these**: the suggestion's four figures go into [group]'s boxes — never the unit name,
     * never the weight — and the group is accepted with the review's confidence. Null when there is
     * no suggestion for it.
     */
    fun accept(group: FactGroup, form: FoodForm): Pair<FoodForm, FormReview>? {
        val shown = review as? Review.Shown ?: return null
        val suggestion = shown.review.suggestionFor(group) ?: return null
        return form.with(group, suggestion.nutrients) to copy(
            review = shown.minus(setOf(group)),
            accepted = accepted + (group to AcceptedGroup(suggestion.confidence, suggestion.keptFrom)),
        )
    }

    /** **Use all**: [accept] for every group with a suggestion. */
    fun acceptAll(form: FoodForm): Pair<FoodForm, FormReview> =
        FactGroup.values().fold(form to this) { (form, reviewing), group ->
            reviewing.accept(group, form) ?: (form to reviewing)
        }

    /** **Dismiss**: whatever is left is taken down; groups already accepted stay accepted. */
    fun dismissed(): FormReview = copy(review = null)

    /**
     * The answer without [groups]. Once nothing is left to act on — no suggestion, no set-aside
     * line, no note — it goes: there is nothing to dismiss. "No changes suggested" is kept until he
     * dismisses it, since it is the answer.
     */
    private fun Review.Shown.minus(groups: Set<FactGroup>): Review.Shown? {
        val left = groups.fold(review) { it, group -> it.without(group) }
        val nothingLeft = left.per100g == null && left.perUnit == null && left.setAside.isEmpty() &&
            left.note.isNullOrBlank()
        return if (nothingLeft && !nothingSuggested) null else copy(review = left)
    }

    private fun FoodReview.without(group: FactGroup): FoodReview = when (group) {
        FactGroup.PER_100G -> copy(per100g = null, setAside = setAside - group)
        FactGroup.PER_UNIT -> copy(perUnit = null, setAside = setAside - group)
    }

    private fun boxes(form: FoodForm, group: FactGroup): List<String> = when (group) {
        FactGroup.PER_100G ->
            listOf(form.kcalPer100g, form.proteinPer100g, form.carbsPer100g, form.fatPer100g)
        FactGroup.PER_UNIT -> listOf(
            form.unitName,
            form.kcalPerUnit,
            form.proteinPerUnit,
            form.carbsPerUnit,
            form.fatPerUnit,
        )
    }
}
