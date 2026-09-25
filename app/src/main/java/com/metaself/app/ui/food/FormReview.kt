package com.metaself.app.ui.food

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.ReviewItem
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

/** One of the eight figure boxes: which group, which figure. */
data class ReviewedBox(val group: FactGroup, val figure: Figure)

/**
 * What **Apply these changes** did, held until he saves, cancels, undoes it or types over every box
 * it changed (D54 §11).
 *
 * @property boxes the boxes whose value came from the review and that he has not typed in since —
 *   each drawn in the teal accent, and counted in the line under the verdict.
 * @property groups the groups it wrote into.
 * @property before the form as it stood when he pressed it, and [wrote] as it stood after: **Undo**
 *   puts [before]'s text back into every box of [groups] that still holds what the review wrote.
 * @property previous the review as it stood when he pressed it, kept up to date with his typing
 *   since (a group he has typed in stays withdrawn), for **Undo** to return to.
 */
data class Applied(
    val boxes: Set<ReviewedBox>,
    val groups: Set<FactGroup>,
    val before: FoodForm,
    val wrote: FoodForm,
    val previous: FormReview,
)

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
 * @property applied what **Apply these changes** did and **Undo** would take back; null when there
 *   is nothing to undo.
 */
data class FormReview(
    val review: Review? = null,
    val accepted: Map<FactGroup, AcceptedGroup> = emptyMap(),
    val modelAnswer: String? = null,
    val applied: Applied? = null,
) {
    /** The boxes drawn as changed by the review and not saved (D54 §11). */
    val changedBoxes: Set<ReviewedBox> get() = applied?.boxes.orEmpty()

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
        val marked = typedMarks(before, after)
        val renamed = before.name.trim() != after.name.trim() || before.brand.trim() != after.brand.trim()
        val touched = if (renamed) {
            FactGroup.values().toSet()
        } else {
            FactGroup.values().filter { boxes(before, it) != boxes(after, it) }.toSet()
        }
        if (touched.isEmpty()) return marked
        return when (val now = review) {
            null, is Review.Failed -> marked
            is Review.Asking -> marked.copy(review = Review.Asking(now.withdrawn + touched))
            is Review.Shown -> marked.copy(review = now.minus(touched))
        }
    }

    /**
     * **A box he types in is his again**: its mark comes off (D54 §11). What Undo would return to
     * follows his typing too, so a group he answered is not brought back. With no mark left, there
     * is nothing to undo.
     */
    private fun typedMarks(before: FoodForm, after: FoodForm): FormReview {
        val applied = applied ?: return this
        val left = applied.boxes.filterTo(mutableSetOf()) { textOf(before, it) == textOf(after, it) }
        if (left.isEmpty()) return copy(applied = null)
        return copy(applied = applied.copy(boxes = left, previous = applied.previous.typed(before, after)))
    }

    /**
     * The suggestion's four figures go into [group]'s boxes — never the unit name, never the
     * weight — and the group is accepted with the review's confidence. Null when there is no
     * suggestion for it.
     */
    private fun accept(group: FactGroup, form: FoodForm): Pair<FoodForm, FormReview>? {
        val shown = review as? Review.Shown ?: return null
        val suggestion = shown.review.suggestionFor(group) ?: return null
        return form.with(group, suggestion.nutrients) to copy(
            review = shown.minus(setOf(group)),
            accepted = accepted + (group to AcceptedGroup(suggestion.confidence, suggestion.keptFrom)),
        )
    }

    /**
     * **Apply these changes** (D54 §11): every group with a suggestion is accepted, as the one accept
     * path has always done it, and the boxes it changed are marked — every box of a group it filled,
     * the changed figures of the others. Nothing is saved: Save still writes, and **Undo** takes it
     * back. Marks from an earlier apply that he has not typed over stay marked.
     */
    fun apply(form: FoodForm): Pair<FoodForm, FormReview> {
        val shown = (review as? Review.Shown)?.review ?: return form to this
        val groups = FactGroup.values().filter { shown.suggestionFor(it) != null }.toSet()
        if (groups.isEmpty()) return form to this
        val changed = groups.flatMap { group ->
            val suggestion = shown.suggestionFor(group)!!
            val figures = if (suggestion.filled) Figure.values().toList() else suggestion.changes.map { it.figure }
            figures.map { ReviewedBox(group, it) }
        }
        val (wrote, accepting) = groups.fold(form to this) { (form, reviewing), group ->
            reviewing.accept(group, form) ?: (form to reviewing)
        }
        return wrote to accepting.copy(
            applied = Applied(
                boxes = changedBoxes + changed,
                groups = groups,
                before = form,
                wrote = wrote,
                previous = this,
            ),
        )
    }

    /**
     * **Undo** (D54 §11): every box the apply wrote goes back to what it held — except one he has
     * typed in since, which is his answer — and the review returns to where it stood, its
     * suggestions and what was accepted with it. Null when there is nothing to undo.
     */
    fun undo(form: FoodForm): Pair<FoodForm, FormReview>? {
        val applied = applied ?: return null
        val restored = applied.groups.fold(form) { restoring, group ->
            Figure.values().fold(restoring) { it, figure ->
                val box = ReviewedBox(group, figure)
                val untouched = textOf(it, box) == textOf(applied.wrote, box)
                if (untouched) it.withBox(box, textOf(applied.before, box)) else it
            }
        }
        return restored to applied.previous
    }

    /**
     * **Dismiss**, or **Keep mine**: whatever is left is taken down; groups already accepted stay
     * accepted, and a box changed by the review stays marked. Undo after it does not bring the
     * dismissed review back.
     */
    fun dismissed(): FormReview = copy(
        review = null,
        modelAnswer = null,
        applied = applied?.let { it.copy(previous = it.previous.dismissed()) },
    )

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
        FactGroup.PER_100G -> copy(per100g = null, setAside = setAside - ReviewItem.PER_100G)
        FactGroup.PER_UNIT -> copy(perUnit = null, setAside = setAside - ReviewItem.PER_UNIT)
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

    private fun textOf(form: FoodForm, box: ReviewedBox): String = when (box.group) {
        FactGroup.PER_100G -> when (box.figure) {
            Figure.KCAL -> form.kcalPer100g
            Figure.PROTEIN -> form.proteinPer100g
            Figure.CARBS -> form.carbsPer100g
            Figure.FAT -> form.fatPer100g
        }
        FactGroup.PER_UNIT -> when (box.figure) {
            Figure.KCAL -> form.kcalPerUnit
            Figure.PROTEIN -> form.proteinPerUnit
            Figure.CARBS -> form.carbsPerUnit
            Figure.FAT -> form.fatPerUnit
        }
    }

    private fun FoodForm.withBox(box: ReviewedBox, text: String): FoodForm = when (box.group) {
        FactGroup.PER_100G -> when (box.figure) {
            Figure.KCAL -> copy(kcalPer100g = text)
            Figure.PROTEIN -> copy(proteinPer100g = text)
            Figure.CARBS -> copy(carbsPer100g = text)
            Figure.FAT -> copy(fatPer100g = text)
        }
        FactGroup.PER_UNIT -> when (box.figure) {
            Figure.KCAL -> copy(kcalPerUnit = text)
            Figure.PROTEIN -> copy(proteinPerUnit = text)
            Figure.CARBS -> copy(carbsPerUnit = text)
            Figure.FAT -> copy(fatPerUnit = text)
        }
    }
}
