package com.metaself.app.ui.food

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.ReviewItem
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.domain.food.WeightAccepted

/** Where a review asked for in a food editor has got to (D54 §4, §12). */
sealed interface Review {

    /**
     * The request is out and nothing has come back. [withdrawn] are the items he has typed in
     * since he asked: he is answering those himself, so what comes back for them is not shown.
     */
    data class Asking(val withdrawn: Set<ReviewItem> = emptySet()) : Review

    /**
     * The answer, as it arrived less what he withdrew while it was out. What is still to be decided
     * is in [FormReview.pending]; this holds the confidences and the note.
     *
     * @property nothingSuggested the answer itself changed nothing, which is a real answer and is
     *   said: *No changes suggested.*
     * @property unusable the answer arrived, and every item it changed was set aside: said as that,
     *   above its set-aside lines — never as an answer that could not be understood (§8.3).
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

/** The eleven boxes of a food's form a review may write into (D54 §12.1) — all but the brand. */
enum class FormBox(val group: FactGroup?, val figure: Figure?) {
    NAME(null, null),
    KCAL_100G(FactGroup.PER_100G, Figure.KCAL),
    PROTEIN_100G(FactGroup.PER_100G, Figure.PROTEIN),
    CARBS_100G(FactGroup.PER_100G, Figure.CARBS),
    FAT_100G(FactGroup.PER_100G, Figure.FAT),
    UNIT(null, null),
    KCAL_UNIT(FactGroup.PER_UNIT, Figure.KCAL),
    PROTEIN_UNIT(FactGroup.PER_UNIT, Figure.PROTEIN),
    CARBS_UNIT(FactGroup.PER_UNIT, Figure.CARBS),
    FAT_UNIT(FactGroup.PER_UNIT, Figure.FAT),
    WEIGHT(null, null),
    ;

    companion object {
        fun of(group: FactGroup, figure: Figure): FormBox = values().first { it.group == group && it.figure == figure }

        /** A group's four figure boxes, calories first. */
        fun figures(group: FactGroup): List<FormBox> = values().filter { it.group == group }
    }
}

/** What [box] holds in this form, as text. */
fun FoodForm.textOf(box: FormBox): String = when (box) {
    FormBox.NAME -> name
    FormBox.KCAL_100G -> kcalPer100g
    FormBox.PROTEIN_100G -> proteinPer100g
    FormBox.CARBS_100G -> carbsPer100g
    FormBox.FAT_100G -> fatPer100g
    FormBox.UNIT -> unitName
    FormBox.KCAL_UNIT -> kcalPerUnit
    FormBox.PROTEIN_UNIT -> proteinPerUnit
    FormBox.CARBS_UNIT -> carbsPerUnit
    FormBox.FAT_UNIT -> fatPerUnit
    FormBox.WEIGHT -> gramsPerUnit
}

/** This form with [box] holding [text]. */
fun FoodForm.withText(box: FormBox, text: String): FoodForm = when (box) {
    FormBox.NAME -> copy(name = text)
    FormBox.KCAL_100G -> copy(kcalPer100g = text)
    FormBox.PROTEIN_100G -> copy(proteinPer100g = text)
    FormBox.CARBS_100G -> copy(carbsPer100g = text)
    FormBox.FAT_100G -> copy(fatPer100g = text)
    FormBox.UNIT -> copy(unitName = text)
    FormBox.KCAL_UNIT -> copy(kcalPerUnit = text)
    FormBox.PROTEIN_UNIT -> copy(proteinPerUnit = text)
    FormBox.CARBS_UNIT -> copy(carbsPerUnit = text)
    FormBox.FAT_UNIT -> copy(fatPerUnit = text)
    FormBox.WEIGHT -> copy(gramsPerUnit = text)
}

/**
 * One box a review wrote into, not yet accepted (D54 §12.6): drawn in the suggestion colour with
 * its reason and a way back.
 *
 * @property original the box's text when the answer arrived — what **Back** puts back, exactly.
 * @property suggested what the review wrote.
 * @property bundled part of the per-one bundle behind a unit the review named or renamed: it goes
 *   back with the unit, and has no button of its own while the unit is pending.
 */
data class PendingBox(
    val original: String,
    val suggested: String,
    val reason: String?,
    val bundled: Boolean = false,
)

/**
 * How a pending box is drawn (D54 §12.6).
 *
 * @property reason its reason, or null when a box before it in the same group says the same.
 * @property back what its button puts back — the original text, "" for **Clear** — or null when it
 *   has no button of its own (a figure of a pending unit's bundle).
 */
data class PendingView(val reason: String?, val back: String?)

/**
 * What **Accept changes and save** hands `FoodForm.toFacts` (D54 §12.7): each group accepted,
 * labelled by its weakest member, and the weight when he accepted one.
 */
data class Acceptance(
    val groups: Map<FactGroup, AcceptedGroup> = emptyMap(),
    val weight: WeightAccepted? = null,
)

/**
 * A food editor's review — the one piece both editors hold, the food's page and the meal
 * builder's *Make a food* (D54 §4, §12), so the two cannot drift in what typing, putting back,
 * accepting or cancelling does.
 *
 * Pure: every step returns a new value, and nothing here asks the model or writes anything. Only
 * **Accept changes and save** stores a suggestion, handing [accepted] to `FoodForm.toFacts`; there
 * is no state in which a suggestion is accepted and not saved (§12.6).
 *
 * @property modelAnswer the model's reply as it came, offered by **Show the model's answer** after a
 *   review that could not be read or used, proposed nothing, or set an item aside (§8.4). Held here
 *   to be shown and nothing else: it is never saved, and never written to the problem log.
 * @property pending every box the review wrote into and he has not put back or typed over, in the
 *   form's order.
 * @property before the form as it stood when he pressed **Review the figures** — what **Cancel**
 *   puts back, his own earlier typing included.
 * @property arrived the form as it stood when the answer arrived, before anything was written: a
 *   figure still reading as it did then is one the review kept.
 */
data class FormReview(
    val review: Review? = null,
    val modelAnswer: String? = null,
    val pending: Map<FormBox, PendingBox> = emptyMap(),
    val before: FoodForm? = null,
    val arrived: FoodForm? = null,
) {
    /** True while a request is out, when the button reads *Reviewing…* and does nothing. */
    val asking: Boolean get() = review is Review.Asking

    /** True while any suggestion waits in a box: Save gives way to Accept changes and save. */
    val hasPending: Boolean get() = pending.isNotEmpty()

    /** A request has gone, from [form] as it stands. Whatever was shown is taken down. */
    fun asked(form: FoodForm): FormReview = FormReview(review = Review.Asking(), before = form)

    /**
     * The answer has come back: every suggestion goes straight into its box, pending (D54 §12.6),
     * except what he withdrew by typing while it was out. If that leaves nothing of an answer that
     * did suggest something, it is still shown — never silent (§9.4) — as having no suggestions left.
     */
    fun answered(answer: FoodReview, raw: String?, form: FoodForm): Pair<FoodForm, FormReview> {
        val withdrawn = (review as? Review.Asking)?.withdrawn.orEmpty()
        val nothingSuggested = !answer.suggestsAnything && answer.setAside.isEmpty()
        val left = withdrawn.fold(answer) { it, item -> it.without(item) }

        var wrote = form
        val pending = linkedMapOf<FormBox, PendingBox>()
        fun put(box: FormBox, text: String, reason: String?, bundled: Boolean = false) {
            pending[box] = PendingBox(wrote.textOf(box), text, reason, bundled)
            wrote = wrote.withText(box, text)
        }

        left.name?.let { put(FormBox.NAME, it.name, it.reason) }
        left.per100g?.let { suggestion -> figures(FactGroup.PER_100G, suggestion, bundled = false, ::put) }
        val bundle = left.unit != null
        left.unit?.let { put(FormBox.UNIT, it.unitName, it.reason) }
        left.perUnit?.let { suggestion -> figures(FactGroup.PER_UNIT, suggestion, bundled = bundle, ::put) }
        left.weight?.let { put(FormBox.WEIGHT, FoodForm.shown(it.grams), it.reason, bundled = bundle && left.weightInBundle) }

        // A weight held per millilitre must not silently become one per glass (§12.4): renamed away
        // from ml with no weight proposed, the bundle clears it — visibly, and it goes back with it.
        // A weight he typed while the request was out is his, typed after the question: it stands.
        val unit = left.unit
        if (unit != null && left.weight == null && form.perHundredMl && ReviewItem.WEIGHT !in withdrawn &&
            !PerHundredMillilitres.applies(unit.unitName) && form.gramsPerUnit.isNotBlank()
        ) {
            put(FormBox.WEIGHT, "", ReviewWording.weightPerMillilitre(unit.unitName), bundled = true)
        }

        return wrote to copy(
            review = Review.Shown(left, nothingSuggested),
            modelAnswer = raw?.takeIf { nothingSuggested || answer.setAside.isNotEmpty() },
            pending = pending,
            arrived = form,
        )
    }

    /**
     * The answer arrived and nothing in it could be used (`ReviewResult.Unusable`): it is shown as
     * that, with what was set aside and its note, until dismissed. Nothing written.
     */
    fun unusable(answer: FoodReview, raw: String? = null): FormReview =
        copy(review = Review.Shown(answer, unusable = true), modelAnswer = raw, pending = emptyMap())

    /** The request failed: said under the button. [raw] is a reply that could not be read, kept to show. */
    fun failed(failure: EstimateResult?, raw: String? = null): FormReview =
        copy(review = Review.Failed(failure), modelAnswer = raw, pending = emptyMap())

    /**
     * He typed, from [before] to [after].
     *
     * **While the request is out**, typing withdraws what it answers (§4, §12.6): per 100 g its
     * group; the unit or a per-one box the bundle and any weight; the weight the weight; a new name
     * or brand the whole answer — the review was of the food as it was called when he asked.
     *
     * **While suggestions are pending**, a box he types in is his: it stops pending, and nothing else
     * moves — except that **a new unit takes down every per-one figure and weight still pending**,
     * back to what they held: they were stated for another unit.
     */
    fun typed(before: FoodForm, after: FoodForm): Pair<FoodForm, FormReview> {
        val now = review
        if (now is Review.Asking) {
            val renamed = before.name.trim() != after.name.trim() || before.brand.trim() != after.brand.trim()
            val touched = buildSet {
                if (renamed) addAll(ReviewItem.values())
                if (FormBox.figures(FactGroup.PER_100G).any { before.textOf(it) != after.textOf(it) }) {
                    add(ReviewItem.PER_100G)
                }
                if ((FormBox.figures(FactGroup.PER_UNIT) + FormBox.UNIT).any { before.textOf(it) != after.textOf(it) }) {
                    add(ReviewItem.PER_UNIT)
                    add(ReviewItem.WEIGHT)
                }
                if (before.gramsPerUnit != after.gramsPerUnit) add(ReviewItem.WEIGHT)
            }
            return after to copy(review = Review.Asking(now.withdrawn + touched))
        }
        if (pending.isEmpty()) return after to this

        val his = pending.filterKeys { before.textOf(it) != after.textOf(it) }.keys
        var form = after
        var left = pending - his
        // A new unit, whether or not the unit was a suggestion: what was stated for another goes.
        if (before.unitName != after.unitName) {
            val tied = left.keys.filter { it.group == FactGroup.PER_UNIT || it == FormBox.WEIGHT }
            tied.forEach { box -> form = form.withText(box, left.getValue(box).original) }
            left = left - tied.toSet()
        }
        return form to copy(pending = left)
    }

    /**
     * **Back** on [box] (D54 §12.6): the box goes back to exactly what it held. The unit takes its
     * whole bundle with it — and, when it goes back to empty, any weight suggested for it. A figure
     * of a pending unit's bundle has no Back of its own: null.
     */
    fun putBack(form: FoodForm, box: FormBox): Pair<FoodForm, FormReview>? {
        val held = pending[box] ?: return null
        if (held.bundled && FormBox.UNIT in pending) return null
        val going = buildSet {
            add(box)
            if (box == FormBox.UNIT) {
                addAll(pending.filterValues { it.bundled }.keys)
                if (held.original.isBlank() && FormBox.WEIGHT in pending) add(FormBox.WEIGHT)
            }
        }
        val restored = going.fold(form) { it, back -> it.withText(back, pending.getValue(back).original) }
        return restored to copy(pending = pending - going)
    }

    /**
     * **Cancel** (D54 §12.6): the page exactly as it stood when he pressed **Review the figures** —
     * his earlier typing included, typing since then gone — and the review taken down. Null when
     * there is nothing to cancel.
     */
    fun cancel(): Pair<FoodForm, FormReview>? {
        if (!hasPending) return null
        return (before ?: return null) to FormReview()
    }

    /** **Dismiss**, for an answer with nothing waiting in the boxes: what is left of it goes. */
    fun dismissed(): FormReview = if (hasPending) this else FormReview()

    /**
     * What **Accept changes and save** takes, from [form] as it stands (D54 §12.7): every group with
     * a box still pending, labelled by its weakest member — the review's confidence, or the source
     * the group was sent with when one of its four figures still reads as it did when the answer
     * arrived (put back, or kept by the model) and ranks lower. The weight, when it is pending, or
     * when it was kept under a unit he accepts, where it now states what one of that unit weighs.
     */
    fun accepted(form: FoodForm): Acceptance {
        val answer = (review as? Review.Shown)?.review ?: return Acceptance()
        val arrivedForm = arrived ?: return Acceptance()
        fun kept(group: FactGroup): Boolean = FormBox.figures(group).any { box ->
            box !in pending && form.textOf(box) == arrivedForm.textOf(box)
        }

        val groups = buildMap {
            val per100g = answer.per100g
            if (per100g != null && FormBox.figures(FactGroup.PER_100G).any { it in pending }) {
                put(
                    FactGroup.PER_100G,
                    AcceptedGroup(per100g.confidence, per100g.heldSource.takeIf { kept(FactGroup.PER_100G) }),
                )
            }
            val unitPending = FormBox.UNIT in pending
            if (unitPending || FormBox.figures(FactGroup.PER_UNIT).any { it in pending }) {
                val confidence = answer.perUnit?.confidence ?: answer.unit?.confidence ?: Confidence.LOW
                val heldSource = if (answer.unit != null) answer.unit.heldSource else answer.perUnit?.heldSource
                put(FactGroup.PER_UNIT, AcceptedGroup(confidence, heldSource.takeIf { kept(FactGroup.PER_UNIT) }))
            }
        }

        val weight = when {
            FormBox.WEIGHT in pending -> answer.weight?.let { WeightAccepted(it.confidence) }
            FormBox.UNIT in pending && form.gramsPerUnit.isNotBlank() &&
                form.gramsPerUnit == arrivedForm.gramsPerUnit && !form.perHundredMl ->
                WeightAccepted(answer.unit?.confidence ?: Confidence.LOW, echoed = true)
            else -> null
        }
        return Acceptance(groups, weight)
    }

    /**
     * How [box] is drawn, or null when nothing waits in it. A reason is said once per group — a
     * fill's one reason under its first box, not four times.
     */
    fun view(box: FormBox): PendingView? {
        val held = pending[box] ?: return null
        val earlier = FormBox.values().takeWhile { it != box }.filter { sameGroup(it, box) }
        val said = earlier.any { pending[it]?.reason == held.reason }
        val back = held.original.takeUnless { held.bundled && FormBox.UNIT in pending }
        return PendingView(reason = held.reason?.takeUnless { said }, back = back)
    }

    private fun sameGroup(a: FormBox, b: FormBox): Boolean =
        a.group != null && a.group == b.group

    private fun figures(
        group: FactGroup,
        suggestion: Suggestion,
        bundled: Boolean,
        put: (FormBox, String, String?, Boolean) -> Unit,
    ) {
        if (suggestion.filled) {
            val n = suggestion.nutrients
            listOf(n.kcal, n.proteinG, n.carbsG, n.fatG).zip(FormBox.figures(group)).forEach { (value, box) ->
                put(box, FoodForm.shown(value), suggestion.reason, bundled)
            }
        } else {
            suggestion.changes.forEach { change ->
                put(FormBox.of(group, change.figure), FoodForm.shown(change.to), change.reason, bundled)
            }
        }
    }

    private fun FoodReview.without(item: ReviewItem): FoodReview = when (item) {
        ReviewItem.NAME -> copy(name = null, setAside = setAside - item)
        ReviewItem.PER_100G -> copy(per100g = null, setAside = setAside - item)
        ReviewItem.PER_UNIT -> copy(
            perUnit = null,
            unit = null,
            weight = weight.takeUnless { weightInBundle },
            weightInBundle = false,
            setAside = setAside - item,
        )
        ReviewItem.WEIGHT -> copy(weight = null, weightInBundle = false, setAside = setAside - item)
    }
}
