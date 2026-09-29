package com.metaself.app.ui.screen.trainer

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording
import java.time.ZoneId

/**
 * D87: how did it go (the session's figures with their sources, the matched plan, how it felt and the
 * words), then the trainer's feedback under "advice, not a measurement" (D4). Branches only — no early
 * return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun ReviewSessionScreen(
    state: ReviewSessionViewModel.State,
    onBack: () -> Unit,
    onFeel: (Felt) -> Unit,
    onWords: (String) -> Unit,
    onNotThisPlan: () -> Unit,
    onSaveAndAsk: () -> Unit,
    onJustSave: () -> Unit,
    onPlanNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = state.feedback
    val workout = state.workout
    val title = stringResource(if (feedback != null) R.string.feedback_title else R.string.review_title)
    MetaSelfScreen(title = title, modifier = modifier, onBack = onBack) {
        when {
            state.gone && state.saved -> Text(stringResource(R.string.review_gone_saved))
            state.gone -> Text(stringResource(R.string.review_gone))
            feedback != null -> FeedbackView(feedback, onPlanNext, onDone)
            workout != null -> ReviewForm(state, workout, onFeel, onWords, onNotThisPlan, onSaveAndAsk, onJustSave)
            else -> Unit
        }
        state.refused?.let { refused ->
            Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewForm(
    state: ReviewSessionViewModel.State,
    workout: Workout,
    onFeel: (Felt) -> Unit,
    onWords: (String) -> Unit,
    onNotThisPlan: () -> Unit,
    onSaveAndAsk: () -> Unit,
    onJustSave: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(TrainerWording.sessionTitle(workout, state.today, zone), style = MaterialTheme.typography.titleMedium)
            TrainerWording.figures(workout).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.plan?.let { plan ->
            Column {
                Text(TrainerWording.planned(plan.plan.title), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onNotThisPlan, enabled = !state.working) { Text(stringResource(R.string.review_not_this_plan)) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                stringResource(R.string.review_felt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                Felt.entries.forEach { felt ->
                    FilterChip(
                        selected = state.felt == felt,
                        onClick = { onFeel(felt) },
                        enabled = !state.working,
                        label = { Text(TrainerWording.felt(felt)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.words,
            onValueChange = onWords,
            // What is saved is what is on screen: nothing changes while a save is under way.
            enabled = !state.working,
            label = { Text(stringResource(R.string.review_words)) },
            supportingText = { Text(stringResource(R.string.review_voice_hint)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onSaveAndAsk, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_save_ask))
            }
            OutlinedButton(onClick = onJustSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_just_save))
            }
            Text(
                TrainerWording.privacyReview(state.ceiling, withPlanned = state.planned != null),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val failure = state.failure
            when {
                state.working -> Text(
                    stringResource(if (state.askingTrainer) R.string.review_working else R.string.review_saving),
                    style = MaterialTheme.typography.bodyMedium,
                )
                failure != null -> Text(TrainerWording.savedWithoutFeedback(failure), color = MaterialTheme.colorScheme.error)
                state.saved -> Text(stringResource(R.string.review_saved), style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
        }
    }
}

@Composable
private fun FeedbackView(feedback: Feedback, onPlanNext: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(feedback.headline, style = MaterialTheme.typography.titleLarge)
            Text(
                TrainerWording.FROM_TRAINER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TrainerWording.parts(feedback).forEach { (heading, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onPlanNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback_plan_next)) }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback_done)) }
        }
    }
}
