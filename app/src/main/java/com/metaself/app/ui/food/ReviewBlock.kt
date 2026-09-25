package com.metaself.app.ui.food

import androidx.annotation.PluralsRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.metaself.app.R
import com.metaself.app.domain.ai.Verdict
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.propose.ProposalWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * What a food editor does with a review, as one value, so both editors wire the same four:
 * **Review the figures**, **Apply these changes**, **Undo**, and **Keep mine** / **Dismiss**.
 */
data class ReviewActions(
    val onReview: () -> Unit,
    val onApply: () -> Unit,
    val onUndo: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        /** Nothing wired: for a render that only looks. */
        val NONE = ReviewActions(onReview = {}, onApply = {}, onUndo = {}, onDismiss = {})
    }
}

/**
 * **Review the figures**; directly under it, its one line of small print saying what is sent; then,
 * once a review has come back, together below the small print: one line saying what it came to,
 * whatever that was, with the model's verdict (§9.4, §10.4); what was applied and not yet saved,
 * with **Undo**; exactly what each group would change, old to new, with its reasons small, then
 * **Apply these changes** and **Keep mine** (§11); a group whose suggestion could not be used, and
 * **Dismiss** when there is nothing to apply; and **Show the model's answer** when the editor holds
 * it (§8.4). One place to look: nothing is drawn under the groups' headings. Shared by My foods'
 * editor and the meal builder's *Make a food*.
 *
 * The boxes a review changed are drawn in the teal accent by the editor ([changedBoxColors]); the
 * words here stay in the body's own ink, the reasons in the captions'.
 *
 * @param offered true when the name is one the form would take; the button is not drawn otherwise.
 * @param unitName the unit box as it stands, for naming the per-one group.
 * @param appliedLine the line said after **Apply these changes**, naming the editor's own button
 *   that saves: *Save* in My foods, *Make it* in the meal builder.
 */
@Composable
fun ReviewTheFigures(
    reviewing: FormReview,
    offered: Boolean,
    unitName: String,
    actions: ReviewActions,
    @PluralsRes appliedLine: Int = R.plurals.review_applied,
) {
    if (!offered && !reviewing.asking) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        // While it is out the button reads Reviewing… and does nothing.
        TextButton(onClick = actions.onReview, enabled = !reviewing.asking) {
            Text(
                stringResource(
                    if (reviewing.asking) R.string.review_asking else R.string.review_the_figures,
                ),
            )
        }
        // The small print belongs to the button; what the review came to follows it (§10.4).
        Caption(stringResource(R.string.review_sends))
        outcome(reviewing)?.let { Outcome(it) }

        // What was applied and is not saved yet, directly under the verdict (§11).
        val changed = reviewing.changedBoxes.size
        if (changed > 0) {
            Text(
                text = pluralStringResource(appliedLine, changed, changed),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = actions.onUndo) {
                Text(stringResource(R.string.review_undo))
            }
        }

        when (val review = reviewing.review) {
            is Review.Shown -> Answer(review, unitName, actions)
            is Review.Failed -> TextButton(onClick = actions.onDismiss) {
                Text(stringResource(R.string.review_dismiss))
            }
            is Review.Asking, null -> Unit
        }
        reviewing.modelAnswer?.let { ModelAnswer(it) }
    }
}

/**
 * The one line every review ends in (D54 §9.4): what it came to, with the model's verdict when it
 * gave one. Null before a review and while one is out — there is nothing to say yet.
 */
@Composable
private fun outcome(reviewing: FormReview): String? = when (val review = reviewing.review) {
    null, is Review.Asking -> null
    is Review.Failed -> review.failure?.let(ProposalWording::failure)
        ?: stringResource(ActionRefused.COULD_NOT_OPEN.sentence)
    is Review.Shown -> {
        val suggestions = listOfNotNull(review.review.per100g, review.review.perUnit).size
        val said = when {
            review.unusable -> stringResource(R.string.review_unusable)
            suggestions > 0 -> pluralStringResource(R.plurals.review_suggestions, suggestions, suggestions)
            // "No changes suggested" only when the model said the food is consistent (§10.3).
            review.nothingSuggested && review.review.verdict == Verdict.PROBLEM_FOUND ->
                stringResource(R.string.review_problem_found)
            review.nothingSuggested -> stringResource(R.string.review_no_changes)
            // Every suggestion went in by Apply these changes, and is still marked (§11).
            reviewing.changedBoxes.isNotEmpty() -> stringResource(R.string.review_changes_applied)
            else -> stringResource(R.string.review_none_left)
        }
        ReviewWording.outcome(said, review.review.note)
    }
}

/**
 * The outcome, under the button's small print, in the body's own type and ink — not the captions'
 * grey, which is the small print's and would read as more of it. Brought into view when it appears, which is
 * when an answer arrives: an answer drawn off screen is an answer he never gets.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Outcome(line: String) {
    val requester = remember { BringIntoViewRequester() }
    Text(
        text = line,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.bringIntoViewRequester(requester),
    )
    LaunchedEffect(Unit) {
        // bringIntoView() does nothing until the line has been placed, and an effect can start
        // before the first layout (FoodsScreen's join question has the same wait). One frame is enough.
        withFrameNanos { }
        requester.bringIntoView()
    }
}

/**
 * What each group would change, one line each in the body's ink with its reasons small under it;
 * then **Apply these changes** and **Keep mine** (D54 §11). Then which groups' suggestions could not
 * be used, and **Dismiss** when there is nothing to apply.
 */
@Composable
private fun Answer(shown: Review.Shown, unitName: String, actions: ReviewActions) {
    val review = shown.review
    // Per 100 ml for a food counted in millilitres, the scale it was reviewed in (D56).
    val one = PerHundredMillilitres.per(unitName).ifEmpty { stringResource(R.string.review_one) }
    FactGroup.values().forEach { group ->
        val suggestion = review.suggestionFor(group) ?: return@forEach
        val per = when (group) {
            FactGroup.PER_100G -> "100 g"
            FactGroup.PER_UNIT -> one
        }
        Text(
            text = ReviewWording.changes(per, suggestion),
            style = MaterialTheme.typography.bodyMedium,
        )
        ReviewWording.reasons(suggestion)?.let { Caption(it) }
    }
    review.setAside.forEach { group ->
        Caption(
            when (group) {
                FactGroup.PER_100G -> stringResource(R.string.review_set_aside_per_100g)
                FactGroup.PER_UNIT -> stringResource(R.string.review_set_aside_per_unit, one)
            },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        if (review.per100g != null || review.perUnit != null) {
            TextButton(onClick = actions.onApply) {
                Text(stringResource(R.string.review_apply))
            }
            TextButton(onClick = actions.onDismiss) {
                Text(stringResource(R.string.review_keep_mine))
            }
        } else {
            TextButton(onClick = actions.onDismiss) {
                Text(stringResource(R.string.review_dismiss))
            }
        }
    }
}

/**
 * **Show the model's answer** (D54 §8.4): the reply as it came, pretty-printed when it is JSON, in
 * text he can select, with **Copy** to put it on the clipboard. Shown only — the editor holds it
 * for this and nothing else. Also drawn by the describe screen when an answer's item could not be
 * used (issue #1).
 */
@Composable
internal fun ModelAnswer(raw: String) {
    var open by rememberSaveable(raw) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(stringResource(if (open) R.string.review_hide_answer else R.string.review_show_answer))
    }
    if (!open) return
    val text = remember(raw) { ReviewWording.modelAnswer(raw) }
    val clipboard = LocalClipboardManager.current
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MetaSelfInk.two,
        )
    }
    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
        Text(stringResource(R.string.review_copy_answer))
    }
}

/**
 * A box whose value came from the review and is not saved yet (D54 §11): drawn in the teal accent —
 * the palette's one family that means nothing else in the editor, the one the D52 milestone takes
 * — and told to a screen reader, since a colour alone says nothing to one.
 *
 * The border and the label are `tertiary`, the fill `tertiaryContainer`, and the figure keeps the
 * body's ink. Measured by `InkLadderTest`: the label in `tertiary` clears the 4.5:1 floor for text
 * both on the fill and on the page behind the border's notch, in both schemes, and so does the ink
 * on the fill.
 */
@Composable
fun changedBoxColors(): TextFieldColors {
    val teal = MaterialTheme.colorScheme.tertiary
    val fill = MaterialTheme.colorScheme.tertiaryContainer
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = teal,
        unfocusedBorderColor = teal,
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        focusedLabelColor = teal,
        unfocusedLabelColor = teal,
    )
}

/** What a screen reader says of a box [changedBoxColors] draws: *changed by the review, not saved*. */
@Composable
fun Modifier.changedByReview(changed: Boolean): Modifier {
    if (!changed) return this
    val said = stringResource(R.string.review_changed_box)
    return semantics { stateDescription = said }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
}
