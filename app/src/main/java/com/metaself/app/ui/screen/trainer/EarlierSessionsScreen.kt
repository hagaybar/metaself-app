package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWeekWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D114: Earlier sessions, a page of its own — the reviewed sessions, newest first, exactly as the Trainer page
 * listed them (D85); each opens its review. Branches only — no early return out of an inline composable
 * (InlineComposableReturnGuardTest).
 */
@Composable
fun EarlierSessionsScreen(
    state: TrainerViewModel.State,
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(title = TrainerWeekWording.EARLIER, modifier = modifier, onBack = onBack) {
        val earlier = state.home?.earlier
        when {
            state.unreadable -> Text(stringResource(R.string.trainer_unreadable), color = MaterialTheme.colorScheme.error)
            earlier == null -> Unit
            earlier.isEmpty() -> Text(
                TrainerWeekWording.NO_EARLIER,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                earlier.forEach { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = { onReview(row.workout.id) })
                            .heightIn(min = 48.dp)
                            .padding(vertical = Spacing.Related),
                    ) {
                        Text(
                            MovementWeekWording.name(row.workout) + " · " +
                                MovementWeekWording.dayHeading(row.workout.epochDay, state.today),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            TrainerWording.earlierLine(row.review),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
