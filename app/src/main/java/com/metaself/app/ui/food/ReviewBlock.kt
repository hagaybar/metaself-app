package com.metaself.app.ui.food

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.metaself.app.domain.ai.ReviewItem
import com.metaself.app.domain.ai.Verdict
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.propose.ProposalWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * What a food editor does with a review, as one value, so both editors wire the same (D54 §12):
 * **Review the figures**, a box's **Back**, **Accept changes and save** (or *…and make it*),
 * **Cancel**, and **Dismiss** for an answer with nothing waiting in the boxes.
 */
data class ReviewActions(
    val onReview: () -> Unit,
    val onPutBack: (FormBox) -> Unit,
    val onAcceptAndSave: () -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        /** Nothing wired: for a render that only looks. */
        val NONE = ReviewActions(onReview = {}, onPutBack = {}, onAcceptAndSave = {}, onCancel = {}, onDismiss = {})
    }
}

/**
 * **Review the figures**; directly under it, its one line of small print saying what is sent; then,
 * once a review has come back, together below the small print: one line saying what it came to,
 * whatever that was, with the model's verdict (§9.4, §10.4); the items that could not be used, and
 * **Dismiss** when nothing waits in the boxes; and **Show the model's answer** when the editor
 * holds it (§8.4). The suggestions themselves are in the boxes (§12.6), each with its reason and
 * its **Back**; accepting or cancelling them is at the foot, in place of Save. Shared by the
 * food's page and the meal builder's *Make a food*.
 *
 * @param offered true when the name is one the form would take; the button is not drawn otherwise,
 *   nor while a suggestion waits in a box (a second review would be sent the model's own figures).
 * @param unitName the unit box as it stands, for naming the per-one group.
 */
@Composable
fun ReviewTheFigures(
    reviewing: FormReview,
    offered: Boolean,
    unitName: String,
    actions: ReviewActions,
) {
    val button = (offered && !reviewing.hasPending) || reviewing.asking
    if (!button && reviewing.review == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        if (button) {
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
        }
        outcome(reviewing)?.let { Outcome(it) }

        when (val review = reviewing.review) {
            is Review.Shown -> Answer(review, unitName, reviewing.hasPending, actions)
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
        val waiting = reviewing.pending.size
        val said = when {
            review.unusable -> stringResource(R.string.review_unusable)
            waiting > 0 -> pluralStringResource(R.plurals.review_suggestions, waiting, waiting)
            // "No changes suggested" only when the model said the food is consistent (§10.3).
            review.nothingSuggested && review.review.verdict == Verdict.PROBLEM_FOUND ->
                stringResource(R.string.review_problem_found)
            review.nothingSuggested -> stringResource(R.string.review_no_changes)
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
 * Which items could not be used, one line each, and **Dismiss** when nothing waits in the boxes —
 * while something does, the answer is settled at the foot, by accepting or cancelling (§12.6).
 */
@Composable
private fun Answer(shown: Review.Shown, unitName: String, pending: Boolean, actions: ReviewActions) {
    // Per 100 ml for a food counted in millilitres, the scale it was reviewed in (D56).
    val one = PerHundredMillilitres.per(unitName).ifEmpty { stringResource(R.string.review_one) }
    shown.review.setAside.forEach { item ->
        Caption(
            when (item) {
                ReviewItem.NAME -> stringResource(R.string.review_set_aside_name)
                ReviewItem.PER_100G -> stringResource(R.string.review_set_aside_per_100g)
                ReviewItem.PER_UNIT -> stringResource(R.string.review_set_aside_per_unit, one)
                ReviewItem.WEIGHT -> stringResource(R.string.review_set_aside_weight)
            },
        )
    }
    if (!pending) {
        TextButton(onClick = actions.onDismiss) {
            Text(stringResource(R.string.review_dismiss))
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
 * A box the review wrote into and he has not accepted (D54 §12.6): drawn in the teal accent — the
 * palette's one family that means nothing else in the editor, the one the D52 milestone takes, and
 * here it means only *suggested, not yet accepted* — and told to a screen reader, since a colour
 * alone says nothing to one.
 *
 * The border and the label are `tertiary`, the fill `tertiaryContainer`, and the figure keeps the
 * body's ink. Measured by `InkLadderTest`: the label in `tertiary` clears the 4.5:1 floor for text
 * both on the fill and on the page behind the border's notch, in both schemes, and so does the ink
 * on the fill.
 */
@Composable
fun suggestedBoxColors(): TextFieldColors {
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

/** What a screen reader says of a box [suggestedBoxColors] draws: *suggested by the review, not accepted*. */
@Composable
fun Modifier.suggestedByReview(suggested: Boolean): Modifier {
    if (!suggested) return this
    val said = stringResource(R.string.review_suggested_box)
    return semantics { stateDescription = said }
}

@Composable
internal fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
}
