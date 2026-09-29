package com.metaself.app.ui.screen.trainer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.metaself.app.R
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D93, D94: the form, then the evaluation and the plan with Keep this plan and Ask again; or the running
 * plan. Branches only — no early return out of an inline composable (InlineComposableReturnGuardTest).
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
) {
    val shown = state.shown
    val running = state.running
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
            state.onRunning && running == null -> Text(stringResource(R.string.weeks_not_running))
            state.onRunning && running != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                FromTrainerLabel()
                state.evaluation?.let { EvaluationView(it) }
                WeeksPlanView(running.programme.plan, running.programme.ask, running.programme.startEpochDay ?: 0, running.progress)
            }
            shown == null -> EvaluateForm(state, onChange, onAsk)
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                FromTrainerLabel()
                shown.evaluation?.let { EvaluationView(it) }
                WeeksPlanView(shown.plan, shown.ask, state.startIfKept, null)
                if (state.kept) {
                    Text(stringResource(R.string.weeks_kept), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Button(onClick = onKeep, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
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
) {
    val form = state.form
    val open = !state.asking
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Text(stringResource(R.string.weeks_intro), style = MaterialTheme.typography.bodyMedium)
        NumberRow(R.string.weeks_row_weeks, ProgrammeAsk.WEEKS, form.weeks, { "$it weeks" }, open) { onChange(form.copy(weeks = it)) }
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
