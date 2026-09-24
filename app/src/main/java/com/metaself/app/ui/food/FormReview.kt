package com.metaself.app.ui.food

import com.metaself.app.domain.ai.EstimateResult
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

    /**
     * The request failed, said under the button it answers (D54 §9.4) until a new request or
     * **Dismiss**: in [failure]'s existing words (`ProposalWording.failure`), or — null — as a
     * review that threw, which could not be opened. Nothing in the form is touched (D8).
     */
    data class Failed(val failure: EstimateResult?) : Review
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
 * @property modelAnswer the model's reply as it came, offered by **Show the model's answer** after a
 *   review that could not be read or used, proposed nothing, or set a group aside (§8.4). Held
 *   here to be shown and nothing else: it is never saved, and never written to the problem log.
 *   A new request or **Dismiss** takes it down.
 */
data class FormReview(
    val review: Review? = null,
    val accepted: Map<FactGroup, AcceptedGroup> = emptyMap(),
    val modelAnswer: String? = null,
) {
    /** True while a request is out, when the button reads *Reviewing…* and does nothing. */
    val asking: Boolean get() = review is Review.Asking

    /** A request has gone. Whatever was shown is taken down; what was accepted stays accepted. */
    fun asked(): FormReview = copy(review = Review.Asking(), modelAnswer = null)

    /**
     * The answer has come back. A group he has typed in meanwhile is left out of it. If that leaves
     * nothing of an answer that did suggest something, it is still shown — never silent (D54 §9.4)
     * — and said as having no suggestions left: "No changes suggested" would not be true.
     *
     * [raw] is kept to show when the answer proposed nothing or set a group aside; an answer whose
     * suggestions are all on screen speaks for itself.
     */
    fun answered(answer: FoodReview, raw: String? = null): FormReview {
        val withdrawn = (review as? Review.Asking)?.withdrawn.orEmpty()
        val nothingSuggested = answer.per100g == null && answer.perUnit == null &&
            answer.setAside.isEmpty()
        val left = withdrawn.fold(answer) { it, group -> it.without(group) }
        return copy(
            review = Review.Shown(left, nothingSuggested),
            modelAnswer = raw?.takeIf { nothingSuggested || answer.setAside.isNotEmpty() },
        )
    }

    /**
     * The answer arrived and nothing in it could be used (`ReviewResult.Unusable`): it is shown as
     * that, with what was set aside and its note, until dismissed. Nothing to accept.
     */
    fun unusable(answer: FoodReview, raw: String? = null): FormReview =
        copy(review = Review.Shown(answer, unusable = true), modelAnswer = raw)

    /**
     * The request failed: [failure] is said under the button, or — null — that the review threw
     * and could not be opened. What was accepted stays. [raw] is the reply of one that arrived and
     * could not be read, kept to show.
     */
    fun failed(failure: EstimateResult?, raw: String? = null): FormReview =
        copy(review = Review.Failed(failure), modelAnswer = raw)

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
            null, is Review.Failed -> this
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
    fun dismissed(): FormReview = copy(review = null, modelAnswer = null)

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
