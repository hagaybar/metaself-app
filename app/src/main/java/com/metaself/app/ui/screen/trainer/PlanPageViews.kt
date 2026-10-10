package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.trainer.PlanPlace
import com.metaself.app.domain.trainer.PlanSessions
import com.metaself.app.domain.trainer.SessionCard
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.PlanPageWording
import com.metaself.app.ui.trainer.PlanTimelineWording

/** D123: Month · Week · Session, under the title; one chosen. */
@Composable
internal fun PlanSwitch(view: EvaluatePlanViewModel.PlanView, onChoose: (EvaluatePlanViewModel.PlanView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        EvaluatePlanViewModel.PlanView.entries.forEach { choice ->
            val label = when (choice) {
                EvaluatePlanViewModel.PlanView.MONTH -> PlanPageWording.MONTH
                EvaluatePlanViewModel.PlanView.WEEK -> PlanPageWording.WEEK
                EvaluatePlanViewModel.PlanView.SESSION -> PlanPageWording.SESSION
            }
            FilterChip(selected = view == choice, onClick = { onChoose(choice) }, label = { Text(label) })
        }
    }
}

/**
 * D122, D123: the Session view — the Next session, this week's done sessions folded, this week's open ones, then
 * next week's; each a card that opens to its steps, or offers Get the steps. Under the D4 label, since the steps
 * and the plan are the trainer's.
 */
@Composable
internal fun SessionView(sessions: PlanSessions, onGetSteps: (PlanPlace) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        if (!sessions.empty) FromTrainerLabel()
        sessions.next?.let { NextSessionCard(it) }
        if (sessions.done.isNotEmpty()) DoneFold(sessions.done, onGetSteps)
        if (sessions.thisWeek.isNotEmpty()) Group(PlanPageWording.THIS_WEEK, sessions.thisWeek, onGetSteps)
        if (sessions.nextWeek.isNotEmpty()) Group(PlanPageWording.NEXT_WEEK, sessions.nextWeek, onGetSteps)
        if (sessions.thisWeek.isEmpty() && sessions.nextWeek.isEmpty() && sessions.next == null) {
            Text(PlanPageWording.NOTHING_COMING, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Group(label: String, cards: List<SessionCard>, onGetSteps: (PlanPlace) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        cards.forEach { SessionCardView(it, onGetSteps) }
    }
}

/** "Done this week · 2", closed by default; its words ink, its arrow the green, as the evaluation's fold (D117). */
@Composable
private fun DoneFold(cards: List<SessionCard>, onGetSteps: (PlanPlace) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        FoldRow(PlanPageWording.doneFold(cards.size), open) { open = !open }
        if (open) cards.forEach { SessionCardView(it, onGetSteps) }
    }
}

/** D123: kind, effort, minutes; done, how; with steps, "Steps ready" and their fold; open without, Get the steps. */
@Composable
private fun SessionCardView(card: SessionCard, onGetSteps: (PlanPlace) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(PlanPageWording.cardTitle(card.planned), style = MaterialTheme.typography.titleSmall)
            val open = card.state == TileState.OPEN
            if (!open) {
                Text(PlanPageWording.doneLine(card), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val steps = card.steps
            when {
                steps != null -> {
                    if (open) Text(PlanTimelineWording.STEPS_READY, style = MaterialTheme.typography.bodyMedium)
                    StepsFold(steps.plan)
                }
                open -> OutlinedButton(onClick = { onGetSteps(card.place) }) { Text(PlanPageWording.GET_STEPS) }
            }
        }
    }
}

/** D122: the kept suggestion for no planned session, for seven days. */
@Composable
internal fun NextSessionCard(plan: TrainerPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(PlanPageWording.NEXT_SESSION, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(plan.plan.title, style = MaterialTheme.typography.titleSmall)
            StepsFold(plan.plan)
        }
    }
}

/** "Show the steps", closed by default: the suggestion's title, its steps and its why. */
@Composable
private fun StepsFold(plan: SessionPlan) {
    var open by rememberSaveable { mutableStateOf(false) }
    FoldRow(if (open) PlanPageWording.HIDE_STEPS else PlanPageWording.SHOW_STEPS, open) { open = !open }
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(plan.title, style = MaterialTheme.typography.titleMedium)
            StepsList(plan)
            Text(plan.why, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FoldRow(label: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
