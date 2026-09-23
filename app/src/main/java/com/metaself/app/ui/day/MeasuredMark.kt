package com.metaself.app.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.metaself.app.ui.theme.Spacing

/**
 * What a ratio has to say on the day screen: today's one sentence, and the tally in words (D32).
 *
 * The sentence is a TIME he can act on — "If you're keeping your fast, next meal from 12:00." — and
 * is said on today only; the view model has already decided which of the five situations applies.
 * The tally, "Kept 10 of 14 stretches since 3 Sep", is a fact about the record, so it is drawn on
 * every day. Until D32 this was a closing time and a bare fraction over the last fortnight, which
 * did not say what it counted.
 *
 * Deliberately NOT the ring [WindowMark] draws. That ring means "the hours you set are open now",
 * and a ratio sets no hours to be inside: the useful fact under a ratio is a time, not a state. So
 * the measured kind has its own mark, and the ring is left to the fixed hours alone (design §4,
 * corrected 2026-09-17).
 *
 * Both lines are silent when there is nothing true to say: no sentence before anything has been
 * logged under the ratio, no tally before a stretch has been judged. With neither, nothing draws —
 * and there is then no way into window settings from this day, which is the price of not printing
 * an empty mark.
 */
@Composable
fun MeasuredMark(
    sentence: String?,
    tally: String?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sentence == null && tally == null) return

    Column(
        modifier = modifier
            .clickable(onClick = onOpenSettings)
            .padding(vertical = Spacing.Tight),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        if (sentence != null) {
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // The same count the fixed window's mark shows, in stretches rather than days (design
        // §3.1): how much of what the ratio governed he kept, counted from the record (D13).
        if (tally != null) {
            Text(
                text = tally,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
