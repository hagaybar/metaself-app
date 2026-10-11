package com.metaself.app.ui.screen.trainer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.trainer.PlanPlace
import com.metaself.app.domain.trainer.GoalSport
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.GoalWording
import com.metaself.app.ui.trainer.PlanPageWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D93, D94, D124: Plan ahead's form, then the answer drawn as its weeks (D118) with Keep this plan — which, while a
 * plan runs, first says what it replaces — and Ask again; or the plan page: Month · Week · Session (D123), then
 * its buttons, Plan ahead always among them. Branches only — no early return out of an inline composable
 * (InlineComposableReturnGuardTest).
 */
@Composable
fun EvaluatePlanScreen(
    state: EvaluatePlanViewModel.State,
    onBack: () -> Unit,
    onChange: (EvaluatePlanViewModel.Form) -> Unit,
    onAsk: () -> Unit,
    onKeep: () -> Unit,
    onAskAgain: () -> Unit,
    modifier: Modifier = Modifier,
    /** D97, D114: Adjust the plan, from the running plan. */
    onAdjust: () -> Unit = {},
    /** D117: the plan's session action — Plan the open walk, or Plan my next session — the single-session form. */
    onPlan: () -> Unit = {},
    /** D124: Plan ahead, on the plan page always. */
    onPlanAhead: () -> Unit = {},
    /** D123: the switch. */
    onChoose: (EvaluatePlanViewModel.PlanView) -> Unit = {},
    /** D123: Get the steps, for the planned session at that place. */
    onGetSteps: (PlanPlace) -> Unit = {},
    /** D124: Not now, under the replace sentence. */
    onCancelKeep: () -> Unit = {},
    /** D121: Fill in next week, or Plan this week. */
    onFill: () -> Unit = {},
    /** D121: Plan the next block. */
    onNextBlock: () -> Unit = {},
    /** D126: Suggest goals. */
    onSuggest: () -> Unit = {},
    /** D126: a suggested goal's card, by its place in the list. */
    onPick: (Int) -> Unit = {},
) {
    val shown = state.shown
    val planShown = state.planShown
    val title = stringResource(
        when {
            state.onRunning -> R.string.weeks_running_title
            shown == null -> R.string.weeks_title
            else -> R.string.weeks_result_title
        },
    )
    MetaSelfScreen(title = title, modifier = modifier, onBack = onBack) {
        when {
            state.loading -> Unit
            state.onRunning -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                PlanSwitch(state.view, onChoose)
                when (state.view) {
                    EvaluatePlanViewModel.PlanView.WEEK -> if (planShown != null) {
                        FromTrainerLabel()
                        PlanShownView(planShown, state.today)
                    } else {
                        Text(stringResource(R.string.weeks_not_running))
                        // D122: with no plan, the kept single session is not one view further away.
                        state.nextSession?.let {
                            FromTrainerLabel()
                            NextSessionCard(it)
                        }
                    }
                    EvaluatePlanViewModel.PlanView.SESSION -> SessionView(state.sessions, onGetSteps)
                    EvaluatePlanViewModel.PlanView.MONTH -> {
                        val month = planShown?.month
                        if (month != null) {
                            FromTrainerLabel()
                            PlanMonthView(month, state.today)
                        } else {
                            Text(
                                if (planShown != null) PlanPageWording.NO_OUTLINE else stringResource(R.string.weeks_not_running),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                PlanButtons(
                    planShown, onAdjust, onPlan, onPlanAhead,
                    FillButton(state.offer, state.filling, state.fillFailure, state.filledLine, state.ceiling),
                    onFill, onNextBlock,
                )
            }
            shown == null -> EvaluateForm(state, onChange, onAsk, onSuggest, onPick)
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                FromTrainerLabel()
                state.offered?.let { offered ->
                    // D124: the answer drawn as D118's weeks, with the Month view's outline above them.
                    offered.month?.let { PlanMonthView(it, state.today) }
                    PlanShownView(offered, state.today)
                }
                when {
                    state.kept -> Text(stringResource(R.string.weeks_kept), style = MaterialTheme.typography.bodyMedium)
                    // D124: drawn in place, not as a dialog, so what it says is on the page.
                    state.confirming -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                        Text(PlanPageWording.replaces(state.startIfKept), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onKeep, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                            Text(PlanPageWording.REPLACE)
                        }
                        OutlinedButton(onClick = onCancelKeep, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                            Text(PlanPageWording.NOT_NOW)
                        }
                    }
                    else -> Button(onClick = onKeep, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.weeks_keep))
                    }
                }
                OutlinedButton(onClick = onAskAgain, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.plan_again))
                }
                if (!state.kept) {
                    Text(
                        stringResource(R.string.weeks_keep_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        state.refused?.let { refused -> Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun EvaluateForm(
    state: EvaluatePlanViewModel.State,
    onChange: (EvaluatePlanViewModel.Form) -> Unit,
    onAsk: () -> Unit,
    onSuggest: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val form = state.form
    val open = !state.asking
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Text(stringResource(R.string.weeks_intro), style = MaterialTheme.typography.bodyMedium)
        Suggestions(state, onSuggest, onPick)
        GoalRows(form, state.today, open, onChange)
        NumberRow(R.string.weeks_row_per_week, ProgrammeAsk.PER_WEEK.toList(), form.perWeek, { "$it" }, open) {
            onChange(form.copy(perWeek = it))
        }
        OutlinedTextField(
            value = form.words,
            onValueChange = { onChange(form.copy(words = it)) },
            enabled = open,
            label = { Text(stringResource(R.string.plan_words)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onAsk, enabled = state.canAsk, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.asking) R.string.plan_asking else R.string.plan_ask))
            }
            Text(
                TrainerWording.privacyEvaluate(state.ceiling),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.failure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * D119, D124, D127, D128: the goal — Keep a steady rhythm, One long session, or More time each week; for a distance,
 * what, how far, and by when; for more time, how many minutes a week (moved by ten) and by when.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalRows(form: EvaluatePlanViewModel.Form, today: Long, enabled: Boolean, onChange: (EvaluatePlanViewModel.Form) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        RowLabel(GoalWording.ROW_GOAL)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            EvaluatePlanViewModel.GoalChoice.entries.forEach { choice ->
                FilterChip(
                    selected = form.goal == choice,
                    onClick = { onChange(form.copy(goal = choice)) },
                    label = { Text(goalChoice(choice)) },
                    enabled = enabled,
                )
            }
        }
    }
    if (form.goal == EvaluatePlanViewModel.GoalChoice.MINUTES) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            RowLabel(GoalWording.ROW_MINUTES)
            Text(GoalWording.minutesLine(form.minutes), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                OutlinedButton(
                    onClick = { onChange(form.fewer()) },
                    enabled = enabled && form.minutes > PlanGoal.WeeklyMinutes.MINUTES.first,
                ) { Text(GoalWording.FEWER) }
                OutlinedButton(
                    onClick = { onChange(form.more()) },
                    enabled = enabled && form.minutes < PlanGoal.WeeklyMinutes.MINUTES.last,
                ) { Text(GoalWording.MORE) }
            }
        }
        ByRow(form, today, enabled, onChange)
    }
    if (form.goal == EvaluatePlanViewModel.GoalChoice.DISTANCE) {
        Text(GoalWording.DISTANCE_LINE, style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            RowLabel(GoalWording.ROW_SPORT)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                GoalSport.entries.forEach { sport ->
                    FilterChip(
                        selected = form.sport == sport,
                        onClick = { onChange(form.copy(sport = sport)) },
                        label = { Text(GoalWording.sport(sport)) },
                        enabled = enabled,
                    )
                }
            }
        }
        OutlinedTextField(
            value = form.km,
            onValueChange = { onChange(form.copy(km = it)) },
            enabled = enabled,
            label = { Text(GoalWording.ROW_KM) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        ByRow(form, today, enabled, onChange)
    }
}

private fun goalChoice(choice: EvaluatePlanViewModel.GoalChoice): String = when (choice) {
    EvaluatePlanViewModel.GoalChoice.STEADY -> GoalWording.STEADY
    EvaluatePlanViewModel.GoalChoice.DISTANCE -> GoalWording.DISTANCE
    EvaluatePlanViewModel.GoalChoice.MINUTES -> GoalWording.MINUTES
}

/** D119, D127: a dated goal's date — the Sunday a whole number of weeks ahead, 6 to 30, moved a week at a time. */
@Composable
private fun ByRow(form: EvaluatePlanViewModel.Form, today: Long, enabled: Boolean, onChange: (EvaluatePlanViewModel.Form) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        RowLabel(GoalWording.ROW_BY)
        Text(GoalWording.byLine(GoalWording.byDay(today, form.weeksAhead), today), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            OutlinedButton(
                onClick = { onChange(form.earlier()) },
                enabled = enabled && form.weeksAhead > EvaluatePlanViewModel.Form.WEEKS_AHEAD.first,
            ) { Text(GoalWording.EARLIER) }
            OutlinedButton(
                onClick = { onChange(form.later()) },
                enabled = enabled && form.weeksAhead < EvaluatePlanViewModel.Form.WEEKS_AHEAD.last,
            ) { Text(GoalWording.LATER) }
        }
    }
}

/**
 * D126: Suggest goals — the outlined button, what it sends, a failure, and the cards. A card says its title, its goal
 * line, sessions a week and why; tapping it fills the form, and it reads "Chosen" (so the choice is not only a colour).
 */
@Composable
private fun Suggestions(state: EvaluatePlanViewModel.State, onSuggest: () -> Unit, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        OutlinedButton(onClick = onSuggest, enabled = !state.suggesting && !state.asking, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.suggesting) GoalWording.SUGGESTING else GoalWording.SUGGEST)
        }
        Text(
            TrainerWording.privacySuggest(state.ceiling),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.suggestFailure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
        state.suggestions.forEachIndexed { index, suggestion ->
            val chosen = state.chosen == index
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = chosen, enabled = !state.asking, role = Role.Button, onClick = { onPick(index) }),
                colors = if (chosen) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.outlinedCardColors()
                },
                border = if (chosen) null else CardDefaults.outlinedCardBorder(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
                    GoalWording.goalLine(suggestion.goal, state.today)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(GoalWording.perWeek(suggestion.perWeek), style = MaterialTheme.typography.bodyMedium)
                    Text(suggestion.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (chosen) Text(GoalWording.CHOSEN, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberRow(
    @StringRes label: Int,
    choices: List<Int>,
    picked: Int?,
    word: (Int) -> String,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            choices.forEach { choice ->
                FilterChip(selected = picked == choice, onClick = { onPick(choice) }, label = { Text(word(choice)) }, enabled = enabled)
            }
        }
    }
}
