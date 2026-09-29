package com.metaself.app.ui.screen.trainer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D86: the plan form (four single-choice rows and optional words), then the suggestion with Keep and
 * Ask again. The suggestion is always under "advice, not a measurement" (D4). Branches only — no early
 * return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun PlanSessionScreen(
    state: PlanSessionViewModel.State,
    onBack: () -> Unit,
    onChange: (PlanSessionViewModel.Form) -> Unit,
    onAsk: () -> Unit,
    onKeep: () -> Unit,
    onAskAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = state.shown
    val title = stringResource(if (shown == null) R.string.plan_title else R.string.plan_result_title)
    MetaSelfScreen(title = title, modifier = modifier, onBack = onBack) {
        when {
            // Opened for the kept plan: nothing until it is read, never the empty form first.
            state.loading -> Unit
            shown == null -> PlanForm(state, onChange, onAsk)
            else -> Suggestion(shown, state.kept, onKeep, onAskAgain)
        }
        state.refused?.let { refused ->
            Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PlanForm(
    state: PlanSessionViewModel.State,
    onChange: (PlanSessionViewModel.Form) -> Unit,
    onAsk: () -> Unit,
) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        state.next?.let { Text(ProgrammeWording.nextInPlan(it), style = MaterialTheme.typography.titleSmall) }
        // What the trainer is asked is what is on screen: nothing changes while it is asked.
        val open = !state.asking
        ChoiceRow(R.string.plan_row_what, PlanActivity.entries, form.activity, TrainerWording::activity, open) {
            onChange(form.copy(activity = it))
        }
        ChoiceRow(R.string.plan_row_time, TimeAvailable.entries, form.time, TrainerWording::time, open) {
            onChange(form.copy(time = it))
        }
        ChoiceRow(R.string.plan_row_feel, Feeling.entries, form.feeling, TrainerWording::feeling, open) {
            onChange(form.copy(feeling = it))
        }
        ChoiceRow(R.string.plan_row_want, Wish.entries, form.wish, TrainerWording::wish, open) {
            onChange(form.copy(wish = it))
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
                TrainerWording.privacyPlan(state.ceiling, withPlan = state.next != null),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.failure?.let { failure ->
                Text(TrainerWording.failure(failure), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** One single-choice row: a label and its chips, wrapping as the workout sheet's kinds do. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    @StringRes label: Int,
    choices: List<T>,
    picked: T?,
    word: (T) -> String,
    enabled: Boolean,
    onPick: (T) -> Unit,
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

@Composable
private fun Suggestion(plan: TrainerPlan, kept: Boolean, onKeep: () -> Unit, onAskAgain: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(plan.plan.title, style = MaterialTheme.typography.titleLarge)
            Text(
                TrainerWording.SUGGESTED,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            plan.plan.steps.forEach { step ->
                Row {
                    Text(
                        TrainerWording.minutes(step),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp),
                    )
                    Column {
                        Text(step.what, style = MaterialTheme.typography.titleSmall)
                        if (step.how.isNotBlank()) {
                            Text(step.how, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                stringResource(R.string.plan_why),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(plan.plan.why, style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            if (kept) {
                Text(stringResource(R.string.plan_kept), style = MaterialTheme.typography.bodyMedium)
            } else {
                Button(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_keep)) }
            }
            OutlinedButton(onClick = onAskAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_again)) }
        }
    }
}
