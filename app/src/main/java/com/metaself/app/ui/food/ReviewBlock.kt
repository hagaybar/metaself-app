package com.metaself.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.food.FactGroup
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
 * **Review the figures**, with its one line of small print saying what is sent, and under it what
 * the review said that belongs to no one group: that it changed nothing, or that nothing in it
 * could be used (§8.3), its note, a group whose suggestion could not be used, and **Use all** and
 * **Dismiss** (D54 §1, §4). Shared by My foods' editor and the meal builder's *Make a food*.
 *
 * No badge, colour or icon is added to anything for a review — the owner's "no new marks": the
 * words are drawn in the captions' own ink.
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
        Caption(stringResource(R.string.review_sends))

        val shown = reviewing.review as? Review.Shown ?: return@Column
        val review = shown.review
        if (shown.nothingSuggested) Caption(stringResource(R.string.review_no_changes))
        if (shown.unusable) Caption(stringResource(R.string.review_unusable))
        review.note?.takeIf { it.isNotBlank() }?.let { Caption(it) }
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
