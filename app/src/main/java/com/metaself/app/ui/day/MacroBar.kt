package com.metaself.app.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metaself.app.ui.theme.Spacing

/**
 * One macro, as a column: a kicker, a figure, and a hairline under it (D49).
 *
 * It was a label and a 6 dp bar, both set at the same volume as everything else on the screen. The
 * column ranks the two parts instead — the kicker in the text face at 13 sp, letterspaced, and the
 * figure in the display face at 28 sp, where a figure belongs. The rule beneath is 1 dp, half the
 * day's own rule, which is what says these three are subordinate to the number at the top of the
 * page without needing a second colour to say it.
 *
 * The figure is allowed to wrap. At a third of a phone's width "12 g over" is wider than "149 g",
 * so pinning it to one line would clip the word that says the macro was passed; a second line
 * pushes that column's rule down by one line, on the days a macro goes over, and says the true
 * thing.
 */
@Composable
fun MacroBar(
    kicker: String,
    figure: String,
    fractionEaten: Float,
    overTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(
            text = kicker,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = figure,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ProportionRule(
            fraction = fractionEaten,
            thickness = 1.dp,
            // Not the error colour, on purpose. Red belongs to the two states the owner has to act
            // on — a refusal, and a typo in a field — and going over on protein is neither; it is
            // just what the day was. The rule filled to its end is what actually says "over"; the
            // change of tone only stops a full rule reading as "exactly on target", which is a
            // small job and is all it is asked to do.
            fill = if (overTarget) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}
