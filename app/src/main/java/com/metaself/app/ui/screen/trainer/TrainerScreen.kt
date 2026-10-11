package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.MainAction
import com.metaself.app.domain.trainer.MovementSummary
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.domain.trainer.WeekLedger
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekStrip
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.theme.MetaSelfTint
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.PlanPageWording
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWeekWording
import com.metaself.app.ui.trainer.TrainerWording
import java.time.ZoneId

/**
 * The Trainer screen (D85, D114), top to bottom: the week's header and the phone's verdict (D115); the week
 * strip on a card, with the shorter-session question under it (D105); the three boxes; the coach's note (D116);
 * one main button with Log a workout beside it (D132); the session waiting for words; and five plain rows —
 * Your plan, Your movement (D132), Weekly letters, Earlier sessions, About me. Branches only — no early return out of an inline composable
 * (InlineComposableReturnGuardTest).
 */
@Composable
fun TrainerScreen(
    state: TrainerViewModel.State,
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onAboutMe: () -> Unit,
    /** D124: Plan ahead — the weekly plan's form. */
    onPlanAhead: () -> Unit,
    onSeePlan: () -> Unit,
    /** D103: every weekly letter. */
    onLetters: () -> Unit,
    /** D114: the reviewed sessions, on their own page. */
    onEarlier: () -> Unit,
    /** D114, D132: the strip's tap, its movement line and the Your movement row — Your movement, at this week. */
    onMovement: () -> Unit,
    modifier: Modifier = Modifier,
    /** D105: the owner's answer to "count it for this?" — the session's id, and Yes (true) or No. */
    onAnswer: (Long, Boolean) -> Unit = { _, _ -> },
    /** D132 item 2: Your movement, with the log-a-workout sheet open for today. */
    onLogWorkout: () -> Unit = {},
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = stringResource(R.string.trainer_title), modifier = modifier, onBack = onBack) {
        val home = state.home
        when {
            state.unreadable -> Text(stringResource(R.string.trainer_unreadable), color = MaterialTheme.colorScheme.error)
            home == null -> Unit
            else -> {
                state.week?.let { week ->
                    WeekHeader(week.strip.monday, week.ledger)
                    StripCard(week.strip, week.movement, home.plan, state.answering, state.refused, onMovement, onAnswer)
                    val boxes = TrainerWeekWording.boxes(week.ledger)
                    if (boxes.isNotEmpty()) Boxes(boxes)
                }
                state.note?.let { NoteView(it, zone) }
                MainButton(home, onPlan, onPlanAhead, onSeePlan, onLogWorkout)
                home.waiting?.let { session -> WaitingCard(session, state.today, zone, onReview) }
                // D122: the kept single session is on the plan page now (Your plan → Session), not here.
                Rows(
                    listOf(
                        PageRow(TrainerWeekWording.planRow(home.plan), onSeePlan),
                        // D132 item 3: the day-by-day record, behind the Trainer now (D130).
                        PageRow(TrainerWeekWording.YOUR_MOVEMENT, onMovement, TrainerWeekWording.YOUR_MOVEMENT_LINE),
                        PageRow(LetterWording.LIST_TITLE, onLetters),
                        PageRow(TrainerWeekWording.EARLIER, onEarlier),
                        PageRow(TrainerWeekWording.ABOUT_ME, onAboutMe),
                    ),
                )
            }
        }
    }
}

/** D114 item 1: "THIS WEEK · MON 5 – SUN 11 OCT", then the verdict in the display face (D115). */
@Composable
private fun WeekHeader(monday: Long, ledger: WeekLedger) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            TrainerWeekWording.header(monday),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(TrainerWeekWording.verdict(ledger), style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * D114 item 2, D132 item 1: the strip, its day letters, the week's movement line ending "Days ›", and the legend —
 * one tap target, which opens Your movement on this week — then the shorter-session question (D105) for each
 * planned session this week holding one, with Yes and No.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StripCard(
    strip: WeekStrip,
    movement: MovementSummary,
    plan: PlanCard,
    answering: Set<Long>,
    refused: ActionRefused?,
    onMovement: () -> Unit,
    onAnswer: (Long, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = TrainerWeekWording.OPEN_MOVEMENT, onClick = onMovement)
                    .semantics { contentDescription = TrainerWeekWording.STRIP },
                verticalArrangement = Arrangement.spacedBy(Spacing.Related),
            ) {
                WeekStripChart(strip)
                MovementLine(movement)
                FlowRow(
                    modifier = Modifier.padding(top = Spacing.Tight),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Section),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    TrainerWeekWording.legend(strip.withPlan).forEachIndexed { index, entry ->
                        LegendEntry(entry, if (strip.withPlan) index else 2)
                    }
                }
            }
            val running = plan as? PlanCard.Running
            running?.progress?.weeks?.getOrNull(running.weekIndex)?.ticks.orEmpty().forEach { tick ->
                tick.candidate?.let { candidate ->
                    CandidateRow(ProgrammeWording.plannedTitle(tick.planned), candidate, candidate.id !in answering, onAnswer)
                }
            }
            refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** D132 item 1: "6 sessions · 21.4 km · 4 weeks: 78.0 km" (invented), each part only with something to say, then "Days ›". */
@Composable
private fun MovementLine(movement: MovementSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.Tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val line = TrainerWeekWording.movementLine(movement)
        if (line != null) {
            Text(line, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Ink, not the green: the theme's green on a card is under the text floor (as "To do"'s label).
        Text(TrainerWeekWording.DAYS, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** [kind]: 0 planned and done, 1 planned and open, 2 beyond the plan (or, with no plan, every session). */
@Composable
private fun LegendEntry(text: String, kind: Int) {
    val green = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val swatch = Modifier.size(10.dp)
        when (kind) {
            0 -> Box(swatch.background(green, RoundedCornerShape(3.dp)))
            1 -> Box(
                swatch.drawBehind {
                    drawRoundRect(
                        color = green,
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
                    )
                },
            )
            else -> Box(swatch.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(3.dp)))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * D105: a shorter session waiting for the owner's answer, under the planned session it may count for —
 * "Easy walk, 30 min", then "Walking, 20 min — count it for this?" (invented) — with Yes and No, each at
 * least 48 dp tall, not pressable while an answer is being written.
 */
@Composable
private fun CandidateRow(planned: String, candidate: Workout, enabled: Boolean, onAnswer: (Long, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(planned, style = MaterialTheme.typography.titleSmall)
        Text(ProgrammeWording.candidateLine(candidate), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            OutlinedButton(
                onClick = { onAnswer(candidate.id, true) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.plan_candidate_yes)) }
            OutlinedButton(
                onClick = { onAnswer(candidate.id, false) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.plan_candidate_no)) }
        }
    }
}

/** D114 item 3: steps, sleep and food side by side; the absent ones leave the others the row. */
@Composable
private fun Boxes(boxes: List<TrainerWeekWording.Box>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        boxes.forEach { box ->
            Card(
                modifier = Modifier.weight(1f),
                colors = if (box.under) {
                    CardDefaults.cardColors(containerColor = MetaSelfTint.caution, contentColor = MaterialTheme.colorScheme.onSurface)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(box.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(box.figure, style = MaterialTheme.typography.titleMedium)
                    Text(box.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    box.extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** D116: the coach's note, when it was written, and whose it is (D4). */
@Composable
private fun NoteView(note: WeekNote, zone: ZoneId) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
            Text(TrainerWeekWording.noteWhen(note, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(TrainerWording.FROM_TRAINER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * D114 item 5, D124: the next thing to do, filled — with Log a workout, outlined, beside it on one row (D132
 * item 2). When it is Plan ahead or a plan-page button, the session action stays under the row, outlined — "Plan
 * the open walk" in a plan's last week, else Plan my next session, the one way to a single session's plan with
 * no weekly plan running.
 */
@Composable
private fun MainButton(
    home: TrainerHome,
    onPlan: () -> Unit,
    onPlanAhead: () -> Unit,
    onSeePlan: () -> Unit,
    onLogWorkout: () -> Unit,
) {
    val main = home.main
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        // One height for both, so they read as one row whatever the main button holds.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            OutlinedButton(onClick = onLogWorkout, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.movement_log_workout), textAlign = TextAlign.Center)
            }
            val filled = Modifier.weight(1f).fillMaxHeight()
            when (main) {
                is MainAction.PlanOpen -> Button(onClick = onPlan, modifier = filled) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(TrainerWeekWording.planOpen(main.kind), textAlign = TextAlign.Center)
                        home.next?.let {
                            Text(ProgrammeWording.nextInPlan(it), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                MainAction.PlanNext -> Button(onClick = onPlan, modifier = filled) {
                    Text(stringResource(R.string.trainer_plan_next), textAlign = TextAlign.Center)
                }
                MainAction.PlanAhead -> Button(onClick = onPlanAhead, modifier = filled) {
                    Text(stringResource(R.string.trainer_evaluate), textAlign = TextAlign.Center)
                }
                // D121: the ask is on the plan page, beside its privacy line; this button takes him there.
                MainAction.PlanThisWeek -> Button(onClick = onSeePlan, modifier = filled) {
                    Text(PlanPageWording.PLAN_THIS_WEEK, textAlign = TextAlign.Center)
                }
                MainAction.FillNextWeek -> Button(onClick = onSeePlan, modifier = filled) {
                    Text(PlanPageWording.FILL_NEXT_WEEK, textAlign = TextAlign.Center)
                }
                MainAction.NextBlock -> Button(onClick = onSeePlan, modifier = filled) {
                    Text(PlanPageWording.NEXT_BLOCK, textAlign = TextAlign.Center)
                }
            }
        }
        if (main !is MainAction.PlanOpen && main != MainAction.PlanNext) {
            OutlinedButton(onClick = onPlan, modifier = Modifier.fillMaxWidth()) {
                val session = MainAction.session(home.next)
                Text(if (session is MainAction.PlanOpen) TrainerWeekWording.planOpen(session.kind) else stringResource(R.string.trainer_plan_next))
            }
        }
    }
}

/** D85, D114 item 6: the newest unreviewed session of the last three days, unchanged. */
@Composable
private fun WaitingCard(session: Workout, today: Long, zone: ZoneId, onReview: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                stringResource(R.string.trainer_waiting),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(TrainerWording.sessionTitle(session, today, zone), style = MaterialTheme.typography.titleMedium)
            Text(TrainerWording.sessionLine(session), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onReview(session.id) }, modifier = Modifier.padding(top = Spacing.Related)) {
                Text(TrainerWording.rowAction(null))
            }
        }
    }
}

/** One of the plain rows: its name, what it opens, and a smaller line under the name when it has one (D132). */
private class PageRow(val label: String, val open: () -> Unit, val line: String? = null)

/** D114 item 8: plain rows, each opening what it names, with hairlines between. */
@Composable
private fun Rows(rows: List<PageRow>) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = row.open)
                    .padding(vertical = Spacing.Related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    row.line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(Spacing.Tight))
    }
}
