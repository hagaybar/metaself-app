package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D97: what is counted, what is rewritten, the words, Adjust; then the new version with Keep this version
 * and Keep the old one; Stop this plan behind a question. Branches only — no early return out of an inline
 * composable (InlineComposableReturnGuardTest).
 */
@Composable
fun AdjustPlanScreen(
    state: AdjustPlanViewModel.State,
    onBack: () -> Unit,
    onWords: (String) -> Unit,
    onAdjust: () -> Unit,
    onKeepNew: () -> Unit,
    onKeepOld: () -> Unit,
    onAskStop: () -> Unit,
    onCancelStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    val running = state.running
    val shown = state.shown
    MetaSelfScreen(title = stringResource(R.string.adjust_title), modifier = modifier, onBack = onBack) {
        when {
            state.loading -> Unit
            running == null -> Text(stringResource(R.string.weeks_not_running))
            shown != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                FromTrainerLabel()
                WeeksPlanView(shown.plan, shown.ask, running.programme.startEpochDay ?: 0, null)
                Button(onClick = onKeepNew, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.adjust_keep_new))
                }
                OutlinedButton(onClick = onKeepOld, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.adjust_keep_old))
                }
            }
            else -> AdjustForm(state, running, onWords, onAdjust, onAskStop)
        }
        state.refused?.let { refused -> Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error) }
    }
    if (state.confirmStop) {
        AlertDialog(
            onDismissRequest = onCancelStop,
            text = { Text(stringResource(R.string.adjust_stop_question)) },
            confirmButton = {
                TextButton(onClick = onConfirmStop, enabled = state.canStop) { Text(stringResource(R.string.adjust_stop_yes)) }
            },
            dismissButton = { TextButton(onClick = onCancelStop) { Text(stringResource(R.string.adjust_stop_no)) } },
        )
    }
}

@Composable
private fun AdjustForm(
    state: AdjustPlanViewModel.State,
    running: PlanCard.Running,
    onWords: (String) -> Unit,
    onAdjust: () -> Unit,
    onAskStop: () -> Unit,
) {
    val programme = running.programme
    val start = programme.startEpochDay ?: 0
    val current = running.weekIndex.coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                stringResource(R.string.adjust_so_far),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            running.progress.weeks.take(current).forEach { Text(ProgrammeWording.pastWeek(it)) }
            if (running.weekIndex >= 0) Text(ProgrammeWording.thisWeekSoFar(running.progress.weeks[current]))
            val firstRewritten = if (running.weekIndex >= 0) current + 2 else 1
            // In the last week no week after it is left: the rewritten part is this week's rest.
            val rewritten = when {
                firstRewritten <= programme.ask.weeks -> ProgrammeWording.rewritten(firstRewritten, programme.ask.weeks)
                else -> ProgrammeWording.restRewritten(current + 1)
            }
            Text(rewritten, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = state.words,
            onValueChange = onWords,
            enabled = !state.asking,
            label = { Text(stringResource(R.string.adjust_words)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(ProgrammeWording.adjustRule(start, programme.ask.weeks), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onAdjust, enabled = state.canAsk, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.asking) R.string.plan_asking else R.string.adjust_ask))
            }
            Text(
                TrainerWording.privacyAdjust(state.ceiling),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.failure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = onAskStop, enabled = state.canStop) { Text(stringResource(R.string.adjust_stop)) }
    }
}
