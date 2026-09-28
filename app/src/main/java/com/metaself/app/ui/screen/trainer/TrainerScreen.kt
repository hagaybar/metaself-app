package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording
import java.time.ZoneId

/**
 * The Trainer screen (D85), top to bottom: the session waiting for words, Plan my next session, the
 * kept plan while it is offered, and earlier reviewed sessions. Branches only — no early return out of
 * an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun TrainerScreen(
    state: TrainerViewModel.State,
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onOpenKept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = stringResource(R.string.trainer_title), modifier = modifier, onBack = onBack) {
        val home = state.home
        when {
            state.unreadable -> Text(stringResource(R.string.trainer_unreadable), color = MaterialTheme.colorScheme.error)
            home == null -> Unit
            else -> {
                home.waiting?.let { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                            Text(
                                stringResource(R.string.trainer_waiting),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(TrainerWording.sessionTitle(session, state.today, zone), style = MaterialTheme.typography.titleMedium)
                            Text(TrainerWording.sessionLine(session), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { onReview(session.id) }, modifier = Modifier.padding(top = Spacing.Related)) {
                                Text(TrainerWording.rowAction(null))
                            }
                        }
                    }
                }
                Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trainer_plan_next)) }
                // D85: a card with its title and Open, drawn as the waiting session's is.
                home.keptPlan?.let { kept ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                            Text(
                                stringResource(R.string.trainer_kept_plan),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(kept.plan.title, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onOpenKept) { Text(stringResource(R.string.trainer_open_plan)) }
                        }
                    }
                }
                if (home.earlier.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        Text(
                            stringResource(R.string.trainer_earlier),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        home.earlier.forEach { row ->
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
    }
}
