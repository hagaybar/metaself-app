package com.metaself.app.ui.day

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.metaself.app.ui.theme.Spacing

/**
 * Whether the eating window is open, as a mark rather than a sentence.
 *
 * A ring with a dot in it while the window is open; the same ring empty when it is shut. Drawn
 * rather than borrowed, because the icon set here has nothing that means "you may eat now" — and
 * deliberately NOT a picture of a window, which would be a metaphor about the word rather than about
 * the thing.
 *
 * It reads at the size of a word, which is the point: the sentence it replaces was four times as
 * long and said the same thing. The number beside it is the count of days kept, and the whole mark
 * is the way in to changing the hours.
 *
 * [open] is a fact about NOW rather than about the day on screen: whether the FIXED hours in force
 * today contain this hour. It is null whenever nothing of that kind is in force — no rule at all,
 * or a ratio — and the row is then the tally by itself.
 *
 * **A ratio deliberately lends it nothing.** An eating stretch has an open-or-shut state of its
 * own, and for one release this drew it: paging back to a day the fixed hours had governed put a
 * ring beside that day's tally whose fill came from whether eating is going on right now. That is
 * a mark about a different thing from the one the day was judged by, and where it sits it reads as
 * belonging to that day (design §4, corrected 2026-09-17). The measured kind has [MeasuredMark].
 *
 * Note what this still does NOT cover: the ring reflects TODAY's hours while the tally beside it
 * belongs to the day on screen, so paging back to a day governed by OTHER fixed hours draws
 * today's state beside that day's count. Pre-existing, and unchanged here; the tally is that day's
 * only way into window settings and must not vanish with the state.
 */
@Composable
fun WindowMark(
    open: Boolean?,
    daysKept: Int,
    daysJudged: Int,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The tally in words, when it is a RATIO's — "Kept 10 of 14 stretches since 3 Sep" — drawn in
     * place of the bare fraction. A past fixed-hours day paged back to while a ratio governs today is
     * handed the ratio's count, in stretches; printed as "10/14" beside that day's ring it read as
     * days, which is the very kind of bare fraction D32 replaced.
     */
    tally: String? = null,
) {
    // No state to show and no count to show: an empty row that is nonetheless tappable is a target
    // with nothing in it, which is worse than nothing at all.
    if (open == null && daysJudged == 0) return

    Row(
        modifier = modifier
            .clickable(onClick = onOpenSettings)
            .padding(vertical = Spacing.Tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (open != null) {
            // Two signals rather than one, because the shut state has to be unmistakable and a
            // missing dot is easy to miss. Open is the app's green and filled;
            // shut is a quiet ring in the muted colour the rest of the app uses for things that
            // are not asking for attention.
            //
            // That colour is `onSurfaceVariant` — the ink ladder's caption step — and not
            // `outline`. `outline` is a border colour, toned for the 3:1 floor that applies to a
            // line around a field, not the 4.5:1 that applies to something meant to be read. This
            // mark is read: it is the whole of what tells the owner the window is shut. Drawing it
            // from the ladder keeps it at the same weight as the small print beside it.
            val colour = if (open) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Canvas(modifier = Modifier.size(MARK_SIZE)) {
                val edge = size.minDimension / 2f
                drawCircle(
                    color = colour,
                    radius = edge - RING_WIDTH,
                    center = Offset(edge, edge),
                    style = Stroke(width = RING_WIDTH),
                )
                // The dot is the whole difference between open and shut, so it is unmistakably
                // filled rather than a slightly heavier outline.
                if (open) {
                    drawCircle(color = colour, radius = edge / 2.2f, center = Offset(edge, edge))
                }
            }
        }

        if (tally != null) {
            Text(
                text = tally,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (daysJudged > 0) {
            Text(
                text = "$daysKept/$daysJudged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val MARK_SIZE = 18.dp
private const val RING_WIDTH = 4f
