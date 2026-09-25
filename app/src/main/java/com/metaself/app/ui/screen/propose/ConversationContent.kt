package com.metaself.app.ui.screen.propose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.ModelAnswer
import com.metaself.app.ui.theme.Spacing

/**
 * What the describe screen does in a conversation (D58 §2), one stage at a time. Every button is
 * the owner's; nothing here sends anything by itself.
 */
data class ConversationActions(
    val onAcceptQuestions: () -> Unit,
    val onBestGuess: () -> Unit,
    val onAnswer: (String) -> Unit,
    val onEnough: () -> Unit,
    /** One stage back; false when there is none and the screen is to be left (D58 §2.5). */
    val onStepBack: () -> Boolean,
    val onRetry: () -> Unit,
    val onBestGuessSoFar: () -> Unit,
)

/** *I'd like to ask up to N questions to get this right.* — OK, or the best guess now. */
@Composable
internal fun OfferContent(state: ProposalUiState.Offer, actions: ConversationActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            text = pluralStringResource(R.plurals.conversation_offer, state.chat.cap, state.chat.cap),
            style = MaterialTheme.typography.bodyLarge,
        )
        Refused(state.refused)
        Button(onClick = actions.onAcceptQuestions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.conversation_ok))
        }
        OutlinedButton(onClick = actions.onBestGuess, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.conversation_best_guess))
        }
    }
}

/**
 * One question: its ready-made answers, *Other*, *That's enough* and *Back* (D58 §2.3). Each
 * answer is a whole-width button, so a long one wraps inside its own button rather than beside
 * another. His remembered answer, after Back, is said under the question and, for his own words,
 * already in the *Other* box.
 */
@Composable
internal fun QuestionContent(state: ProposalUiState.Asking, actions: ConversationActions) {
    val chat = state.chat
    val chosen = chat.chosen
    val ownWords = chosen?.takeIf { answer -> chat.shown.options.none { it.equals(answer, ignoreCase = true) } }
    // Keyed on the question, so each question starts from its own remembered words, or none.
    var other by rememberSaveable(chat.at, chat.shown.text) { mutableStateOf(ownWords.orEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(R.string.conversation_heading, chat.number, chat.ofUpTo),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = chat.shown.text, style = MaterialTheme.typography.titleMedium)
        chosen?.let {
            Text(
                text = stringResource(R.string.conversation_your_answer, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Refused(state.refused)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        chat.shown.options.forEach { option ->
            if (chosen != null && option.equals(chosen, ignoreCase = true)) {
                Button(onClick = { actions.onAnswer(option) }, modifier = Modifier.fillMaxWidth()) {
                    Text(option)
                }
            } else {
                OutlinedButton(onClick = { actions.onAnswer(option) }, modifier = Modifier.fillMaxWidth()) {
                    Text(option)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        OutlinedTextField(
            value = other,
            onValueChange = { other = it },
            label = { Text(stringResource(R.string.conversation_other)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { actions.onAnswer(other) }, enabled = other.isNotBlank()) {
            Text(stringResource(R.string.conversation_other_send))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        TextButton(onClick = actions.onEnough, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.conversation_enough))
        }
        TextButton(onClick = { actions.onStepBack() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.conversation_back))
        }
    }
}

/**
 * A step or the final analysis failed, or the day ran out (D58 §9): what happened, what he said —
 * kept, since nothing is stored — and the ways on.
 */
@Composable
internal fun ConversationFailedContent(
    state: ProposalUiState.ConversationFailed,
    description: String,
    actions: ConversationActions,
    onTypeItMyself: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = state.failure,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        state.answer?.let { ModelAnswer(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(R.string.conversation_what_you_said),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
        state.asked.forEach { asked ->
            Text(
                text = "${asked.question} — ${asked.answer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        if (state.retry != null) {
            Button(onClick = actions.onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.conversation_try_again))
            }
        }
        if (state.bestGuessWith != null) {
            OutlinedButton(onClick = actions.onBestGuessSoFar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.conversation_best_guess_so_far))
            }
        }
        TextButton(onClick = onTypeItMyself, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.propose_manual))
        }
    }
}

/** What is said while a request is in flight (D58 §2, §7). */
@Composable
internal fun waitingWords(state: ProposalUiState.Waiting): String = stringResource(
    when (state.kind) {
        WaitingFor.ANSWER -> R.string.propose_waiting
        WaitingFor.QUESTION -> R.string.conversation_waiting_question
        WaitingFor.RESULT -> R.string.conversation_waiting_result
    },
)

@Composable
private fun Refused(refused: ActionRefused?) {
    refused?.let {
        Text(
            text = stringResource(it.sentence),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The conversation's stages Back steps through, rather than leaving (D58 §2.5). */
internal fun stepsBack(state: ProposalUiState): Boolean = when (state) {
    is ProposalUiState.Offer, is ProposalUiState.Asking, is ProposalUiState.ConversationFailed -> true
    is ProposalUiState.Waiting -> state.returnTo != null
    is ProposalUiState.Describing, is ProposalUiState.Proposed -> false
}
