package com.metaself.app.ui.food

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.metaself.app.R
import com.metaself.app.domain.ai.Verdict
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.propose.ProposalWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/** What a food editor does with a review, as one value, so both editors wire the same four. */
data class ReviewActions(
    val onReview: () -> Unit,
    val onAccept: (FactGroup) -> Unit,
    val onAcceptAll: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        /** Nothing wired: for a render that only looks. */
        val NONE = ReviewActions(onReview = {}, onAccept = {}, onAcceptAll = {}, onDismiss = {})
    }
}

/**
 * **Review the figures**; directly under it, its one line of small print saying what is sent; then,
 * once a review has come back, together below the small print: one line saying what it came to,
 * whatever that was, with the model's verdict (§9.4, §10.4), then what the review said that belongs
 * to no one group — a group whose suggestion could not be used, **Use all** and **Dismiss** (D54
 * §1, §4) — and **Show the model's answer** when the editor holds it (§8.4). Shared by My foods'
 * editor and the meal builder's *Make a food*.
 *
 * No badge, colour or icon is added to anything for a review — the owner's "no new marks": the
 * outcome is in the body's own ink, everything else in the captions'.
 *
 * @param offered true when the name is one the form would take; the button is not drawn otherwise.
 * @param unitName the unit box as it stands, for naming the per-one group in a set-aside line.
 */
@Composable
fun ReviewTheFigures(
    reviewing: FormReview,
    offered: Boolean,
    unitName: String,
    actions: ReviewActions,
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
        outcome(reviewing.review)?.let { Outcome(it) }

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
private fun outcome(review: Review?): String? = when (review) {
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

/** Which groups' suggestions could not be used, and **Use all** and **Dismiss**. */
@Composable
private fun Answer(shown: Review.Shown, unitName: String, actions: ReviewActions) {
    val review = shown.review
    review.setAside.forEach { group ->
        Caption(
            when (group) {
                FactGroup.PER_100G -> stringResource(R.string.review_set_aside_per_100g)
                FactGroup.PER_UNIT -> stringResource(
                    R.string.review_set_aside_per_unit,
                    unitName.trim().ifEmpty { stringResource(R.string.review_one) },
                )
            },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        if (review.per100g != null && review.perUnit != null) {
            TextButton(onClick = actions.onAcceptAll) {
                Text(stringResource(R.string.review_use_all))
            }
        }
        TextButton(onClick = actions.onDismiss) {
            Text(stringResource(R.string.review_dismiss))
        }
    }
}

/**
 * **Show the model's answer** (D54 §8.4): the reply as it came, pretty-printed when it is JSON, in
 * text he can select, with **Copy** to put it on the clipboard. Shown only — the editor holds it
 * for this and nothing else.
 */
@Composable
private fun ModelAnswer(raw: String) {
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
 * Under a group's heading: what the review suggests for it, one line per changed figure, and
 * **Use these**. Nothing when the review has no suggestion for this group.
 */
@Composable
fun GroupSuggestion(reviewing: FormReview, group: FactGroup, onAccept: (FactGroup) -> Unit) {
    val suggestion = (reviewing.review as? Review.Shown)?.review?.suggestionFor(group) ?: return
    ReviewWording.lines(suggestion).forEach { Caption(it) }
    TextButton(onClick = { onAccept(group) }) {
        Text(stringResource(R.string.review_use_these))
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
}
