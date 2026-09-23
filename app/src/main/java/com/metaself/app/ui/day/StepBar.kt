package com.metaself.app.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.ui.movement.MovementWording
import com.metaself.app.ui.theme.Spacing

/**
 * Today's walking, on its own line directly under the macros, whether or not it earned anything.
 *
 * **The decision asks for this by name** (D49 item 6, 2026-09-21). The first draft of it
 * buried the count in the colophon, in the smallest type in the app, and the argument for moving it
 * up is stronger than preference: a thing that changes the number at the top of the page cannot be
 * a footnote. Both figures are drawn — the count and what it earned — because the earned calories
 * are what actually move that number, and D9 says the owner must be able to see exactly what
 * earned them.
 *
 * It takes the macros' treatment: the count as a display-face figure, a hairline under it filled to
 * a usual day, and the smaller comparison beneath. **It has no kicker**, which the three macros do.
 * A macro's figure is "149 g" and needs a letter to say which macro it is; this one is "3,100
 * steps" and names itself, so a kicker over it would print the word twice.
 *
 * The rule fills to a usual day and stops. A scale that kept a 25,000-step day in proportion would
 * make every ordinary day look like nothing, which is the opposite of the point.
 *
 * D12a is untouched: the count shows every day, and a day that earned nothing still says nothing
 * about the zero, because printing "0 kcal earned" would turn an ordinary day into a reproach.
 */
@Composable
fun StepBar(today: MovementToday, modifier: Modifier = Modifier) {
    val reached = today.aboveUsual

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = MovementWording.steps(today).orEmpty(),
                style = if (today.recorded) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onBackground,
            )
            MovementWording.earned(today.credit)?.let { earned ->
                Text(
                    text = earned,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Only drawn once there is a usual day to draw against; before that the count stands alone.
        if (today.normalSteps != null && today.recorded) {
            ProportionRule(
                fraction = today.fractionOfUsual,
                thickness = 1.dp,
                // A day past the usual one is the app's green, which on this screen means a thing
                // that went well. Everything short of it is ink, like every other rule on the page:
                // the brand green-grey this used to draw said "not yet" in a colour the eye had
                // already seen on ninety other words, and said it about a day that owes nothing.
                fill = if (reached) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }

        // What the band recorded as a session. A swim shows here and nowhere else,
        // because swimming moves no steps.
        MovementWording.sessions(today).forEach { session ->
            Text(
                text = session,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        MovementWording.againstUsual(today)?.let { against ->
            Text(
                text = against,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
