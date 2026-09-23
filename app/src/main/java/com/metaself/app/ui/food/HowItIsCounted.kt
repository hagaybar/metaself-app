package com.metaself.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.food.CannotCount
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * Weigh it or count it: the two ways of saying how much of a food, as the two chips every other
 * two-way choice in the app already is (the sex and the fasting ratio are the same control).
 *
 * **An option the food cannot answer is drawn switched off, and looks it.** These were text buttons
 * whose label colour was set by hand, which overrode the faded colour a disabled button is given —
 * so a dead option was drawn exactly like a live one and a tap on it did nothing at all. A chip's
 * label is left to the chip, which fades it and its outline together.
 *
 * **The reason is said as that option's reason**, named by the option's own label and directly under
 * the pair, rather than as a free-standing sentence below the row that read as commentary on the
 * food.
 *
 * **Where the fix is one tap away, it is offered.** A food nobody has said a portion of cannot be
 * counted, and the place to say one is the food's own editor; [onGivePortion] goes there. Null where
 * the screen has no way there, in which case the reason stands alone as it always has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItIsCounted(
    countedAs: CountedAs,
    unitName: String,
    cannotWeigh: CannotCount?,
    cannotCount: CannotCount?,
    onCountAs: (CountedAs) -> Unit,
    onGivePortion: (() -> Unit)? = null,
) {
    val weighLabel = stringResource(R.string.food_in_grams)
    val countLabel = stringResource(R.string.food_in_units, unitName)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            FilterChip(
                selected = countedAs == CountedAs.GRAMS,
                onClick = { onCountAs(CountedAs.GRAMS) },
                enabled = cannotWeigh == null,
                label = { Text(weighLabel) },
            )
            FilterChip(
                selected = countedAs == CountedAs.UNITS,
                onClick = { onCountAs(CountedAs.UNITS) },
                enabled = cannotCount == null,
                label = { Text(countLabel) },
            )
        }
        cannotWeigh?.let { reason ->
            Text(
                text = FoodWording.whyNot(weighLabel, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }
        cannotCount?.let { reason ->
            Text(
                text = FoodWording.whyNot(countLabel, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            // Counting is off only when nothing has named a portion, which is exactly what the
            // editor's "What one of it is worth" group is for.
            if (onGivePortion != null) {
                TextButton(onClick = onGivePortion) {
                    Text(stringResource(R.string.food_give_a_portion))
                }
            }
        }
    }
}
