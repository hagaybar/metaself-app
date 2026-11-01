package com.metaself.app.ui.screen.repeat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.metaself.app.R
import com.metaself.app.domain.food.QuickTile
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What one press logs (D154): two across, drawn as tiles so that they cannot be taken for the rows
 * below, which open rather than log (public issue #21).
 *
 * Each says what it will write, so a press is never a surprise. When the day being looked at is not
 * today the heading names it, because that is where the row will go.
 */
@Composable
internal fun QuickAddTiles(
    tiles: List<QuickTile>,
    otherDay: LocalDate?,
    onTile: (QuickTile) -> Unit,
) {
    if (tiles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            text = otherDay?.let {
                stringResource(R.string.repeat_quick_title_on, it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)))
            } ?: stringResource(R.string.repeat_quick_title),
            style = MaterialTheme.typography.titleSmall,
        )
        tiles.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            ) {
                pair.forEach { tile ->
                    OutlinedCard(
                        onClick = { onTile(tile) },
                        modifier = Modifier.weight(1f).fillMaxHeight().semantics { role = Role.Button },
                    ) {
                        Column(modifier = Modifier.padding(Spacing.Related)) {
                            Text(text = tile.name, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                            Text(
                                text = tile.items.singleOrNull()
                                    ?.takeIf { tile.savedMealId == null }
                                    ?.let { stringResource(R.string.repeat_quick_food, it.portion.orEmpty(), tile.kcal) }
                                    ?: stringResource(R.string.repeat_quick_meal, tile.kcal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MetaSelfInk.two,
                            )
                        }
                    }
                }
                // An odd last tile keeps its width rather than stretching across both columns.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
