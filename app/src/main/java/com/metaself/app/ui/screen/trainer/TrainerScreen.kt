package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.Tick
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWording
import java.time.ZoneId

/**
 * The Trainer screen (D85), top to bottom: About me (D90), the session waiting for words, the weekly
 * plan's card (D95, D97) — an offer to evaluate, the running plan's week, or an ended plan's count —
 * Plan my next session with the plan's next session under it (D96), the kept plan while it is offered,
 * and earlier reviewed sessions. Branches only — no early return out of
 * an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun TrainerScreen(
    state: TrainerViewModel.State,
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onOpenKept: () -> Unit,
    onAboutMe: () -> Unit,
    onEvaluate: () -> Unit = {},
    onSeePlan: () -> Unit = {},
    onAdjust: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = stringResource(R.string.trainer_title), modifier = modifier, onBack = onBack) {
        // D90: under the title, the note's first two lines, or an invitation; the whole card opens its page.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onAboutMe),
        ) {
            Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    stringResource(R.string.trainer_about_me),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    TrainerWording.aboutMePreview(state.aboutMe),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
                PlanCardView(home.plan, onEvaluate, onSeePlan, onAdjust)
                Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.trainer_plan_next))
                        home.next?.let { Text(ProgrammeWording.nextInPlan(it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
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

/** D95, D97: the weekly plan's card — an offer, the running plan's week, or an ended plan's count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCardView(card: PlanCard, onEvaluate: () -> Unit, onSeePlan: () -> Unit, onAdjust: () -> Unit) {
    when (card) {
        PlanCard.None -> OutlinedButton(onClick = onEvaluate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.trainer_evaluate))
        }
        is PlanCard.Running -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    ProgrammeWording.cardHeading(card.programme.ask.weeks, card.weekIndex, card.programme.startEpochDay ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(card.programme.plan.title, style = MaterialTheme.typography.titleMedium)
                if (card.weekIndex in card.progress.weeks.indices) {
                    val week = card.progress.weeks[card.weekIndex]
                    Text(ProgrammeWording.weekDates(week.monday), style = MaterialTheme.typography.bodyMedium)
                    week.ticks.forEach { tick -> TickRow(tick) }
                    card.progress.weeks.take(card.weekIndex).forEach { past ->
                        Text(ProgrammeWording.pastWeek(past), style = MaterialTheme.typography.bodySmall)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    TextButton(onClick = onSeePlan) { Text(stringResource(R.string.trainer_see_plan)) }
                    TextButton(onClick = onAdjust) { Text(stringResource(R.string.trainer_adjust_plan)) }
                }
            }
        }
        is PlanCard.Ended -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    ProgrammeWording.endedHeading(card.programme.ask.weeks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(card.programme.plan.title, style = MaterialTheme.typography.titleMedium)
                Text(ProgrammeWording.endedLine(card.progress), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onEvaluate) { Text(stringResource(R.string.trainer_evaluate_again)) }
            }
        }
    }
}

/**
 * D95: one planned session of this week — a filled check for done, an empty circle for not yet, each
 * with its word for a screen reader — then what was planned and, once done, the session that did it.
 */
@Composable
private fun TickRow(tick: Tick) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tick.by != null) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.plan_tick_done),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            val notYet = stringResource(R.string.plan_tick_not_yet)
            Box(
                Modifier
                    .size(24.dp)
                    .padding(2.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .semantics { contentDescription = notYet },
            )
        }
        Column {
            Text(ProgrammeWording.plannedTitle(tick.planned), style = MaterialTheme.typography.titleSmall)
            Text(
                tick.by?.let(ProgrammeWording::tickLine) ?: tick.planned.what,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
