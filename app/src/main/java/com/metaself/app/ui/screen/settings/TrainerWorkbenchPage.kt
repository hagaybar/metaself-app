package com.metaself.app.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.screen.trainer.ChoiceRow
import com.metaself.app.ui.screen.trainer.EvaluatePlanViewModel
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording
import com.metaself.app.ui.trainer.WorkbenchWording
import java.time.ZoneId

/**
 * "Test the trainer's instructions" (D106): the path and its inputs, the instructions, Send, the reply.
 * Plain on purpose — a testing tool. The pickers and the clipboard are the destination's, so this stays a
 * pure composable. Branches only; no early return out of an inline composable.
 */
@Composable
fun TrainerWorkbenchPage(
    state: TrainerWorkbenchViewModel.State,
    onPath: (TrainerPath) -> Unit,
    onSession: (Long) -> Unit,
    onPlan: (PlanSessionViewModel.Form) -> Unit,
    onEvaluate: (EvaluatePlanViewModel.Form) -> Unit,
    onAdjustWords: (String) -> Unit,
    onLoad: () -> Unit,
    onUseAppOwn: () -> Unit,
    onSaveAppOwn: () -> Unit,
    onSend: () -> Unit,
    onCopyReply: () -> Unit,
    onCopySent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(title = stringResource(R.string.settings_page_trainer_instructions), modifier = modifier, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
            Text(WorkbenchWording.INTRO, style = MaterialTheme.typography.bodyMedium)
            if (state.unreadable) Text(WorkbenchWording.RECORD_NOT_READ, color = MaterialTheme.colorScheme.error)
            val open = !state.sending
            ChoiceRow(R.string.workbench_row_path, TrainerPath.entries, state.path, WorkbenchWording::path, open, onPath)
            PathInputs(state, open, onSession, onPlan, onEvaluate, onAdjustWords)

            Text(WorkbenchWording.willSend(state.fileName), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onLoad, enabled = open, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.LOAD) }
            if (state.fileName != null) {
                OutlinedButton(onClick = onUseAppOwn, enabled = open, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.USE_APP_OWN) }
            }
            OutlinedButton(onClick = onSaveAppOwn, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.SAVE_APP_OWN) }
            state.fileMessage?.let { Text(it) }

            Button(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.sending) WorkbenchWording.SENDING else WorkbenchWording.SEND)
            }
            Text(WorkbenchWording.PRIVACY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.failure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
            state.reply?.let { reply ->
                SelectionContainer { Text(reply) }
                OutlinedButton(onClick = onCopyReply, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.COPY_REPLY) }
            }
            if (state.sent != null) {
                OutlinedButton(onClick = onCopySent, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.COPY_SENT) }
            }
        }
    }
}

/** Each path's inputs, as its real screen asks them. */
@Composable
private fun PathInputs(
    state: TrainerWorkbenchViewModel.State,
    open: Boolean,
    onSession: (Long) -> Unit,
    onPlan: (PlanSessionViewModel.Form) -> Unit,
    onEvaluate: (EvaluatePlanViewModel.Form) -> Unit,
    onAdjustWords: (String) -> Unit,
) {
    when (state.path) {
        // Until the record has been read, an empty list or no plan is not yet a fact, so nothing is said.
        TrainerPath.FEEDBACK -> if (state.sessions.isEmpty()) {
            if (state.loaded) Text(WorkbenchWording.NO_SESSIONS)
        } else {
            val zone = ZoneId.systemDefault()
            ChoiceRow(
                R.string.workbench_row_session, state.sessions, state.sessions.firstOrNull { it.id == state.workoutId },
                { TrainerWording.sessionTitle(it, state.today, zone) }, open,
            ) { onSession(it.id) }
        }

        TrainerPath.PLAN -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
            val form = state.plan
            ChoiceRow(R.string.plan_row_what, PlanActivity.entries, form.activity, TrainerWording::activity, open) { onPlan(form.copy(activity = it)) }
            ChoiceRow(R.string.plan_row_time, TimeAvailable.entries, form.time, TrainerWording::time, open) { onPlan(form.copy(time = it)) }
            ChoiceRow(R.string.plan_row_feel, Feeling.entries, form.feeling, TrainerWording::feeling, open) { onPlan(form.copy(feeling = it)) }
            ChoiceRow(R.string.plan_row_want, Wish.entries, form.wish, TrainerWording::wish, open) { onPlan(form.copy(wish = it)) }
            Words(R.string.plan_words, form.words, open) { onPlan(form.copy(words = it)) }
        }

        TrainerPath.EVALUATE -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
            val form = state.evaluate
            ChoiceRow(R.string.weeks_row_weeks, ProgrammeAsk.WEEKS, form.weeks, { "$it weeks" }, open) { onEvaluate(form.copy(weeks = it)) }
            ChoiceRow(R.string.weeks_row_per_week, ProgrammeAsk.PER_WEEK.toList(), form.perWeek, { "$it" }, open) { onEvaluate(form.copy(perWeek = it)) }
            Words(R.string.plan_words, form.words, open) { onEvaluate(form.copy(words = it)) }
        }

        TrainerPath.ADJUST -> if (state.planRuns) {
            Words(R.string.adjust_words, state.adjustWords, open, onAdjustWords)
        } else if (state.loaded) {
            Text(WorkbenchWording.NO_PLAN)
        }
    }
}

/** The free-words field, under [label]: the label of the real screen the path stands in for. */
@Composable
private fun Words(@StringRes label: Int, words: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = words,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(stringResource(label)) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}
