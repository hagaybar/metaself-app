package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.trainer.CoachSuggestion
import com.metaself.app.domain.trainer.CoachSuggestions
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.domain.trainer.MainAction
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.PlanPageWording
import com.metaself.app.ui.trainer.TalkWording
import com.metaself.app.ui.trainer.TrainerWeekWording
import com.metaself.app.ui.trainer.TrainerWording
import java.time.ZoneId

/**
 * The coach screen (D157), top to bottom: the coach's note on this week with Reply ›; the box; up to three suggestions
 * (D158); the three latest conversations with All conversations › (D160); Everything else ›; what leaves the phone.
 * Branches only — no early return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun CoachScreen(
    trainer: TrainerViewModel.State,
    coach: CoachViewModel.State,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSend: () -> Unit,
    onAskAboutTheWeek: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onPlanAhead: () -> Unit,
    onSeePlan: () -> Unit,
    onReplyToNote: (WeekNote) -> Unit,
    onConversation: (String) -> Unit,
    onAllConversations: () -> Unit,
    onEverythingElse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = stringResource(R.string.trainer_title), modifier = modifier, onBack = onBack) {
        trainer.note?.let { note -> NoteView(note, zone) { onReplyToNote(note) } }
        Box(coach, onEdit, onSend)
        coach.refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        trainer.home?.let { home ->
            Suggestions(CoachSuggestions.of(home), onAskAboutTheWeek, onReview, onPlan, onPlanAhead, onSeePlan, onEverythingElse)
        }
        if (coach.unreadable) Text(TalkWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            coach.conversations.take(LATEST).forEach { ConversationRow(it, trainer.today, zone, onConversation) }
            if (coach.conversations.isNotEmpty()) PlainRow(TalkWording.ALL_CONVERSATIONS, null, onAllConversations)
            PlainRow(TalkWording.EVERYTHING_ELSE, TalkWording.EVERYTHING_ELSE_LINE, onEverythingElse)
        }
        Text(TrainerWording.privacyTalk(coach.ceiling), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** D160: every conversation, in the coach screen's rows. */
@Composable
fun ConversationsScreen(
    coach: CoachViewModel.State,
    onBack: () -> Unit,
    onConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone).toEpochDay()
    MetaSelfScreen(title = TalkWording.ALL_CONVERSATIONS, modifier = modifier, onBack = onBack) {
        when {
            coach.unreadable -> Text(TalkWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
            coach.loaded && coach.conversations.isEmpty() -> Text(TalkWording.NO_CONVERSATIONS)
            else -> Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                coach.conversations.forEach { ConversationRow(it, today, zone, onConversation) }
            }
        }
    }
}

/** The box and Send: a new conversation (D159). */
@Composable
private fun Box(coach: CoachViewModel.State, onEdit: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        OutlinedTextField(
            value = coach.draft,
            onValueChange = onEdit,
            placeholder = { Text(TalkWording.BOX_PLACEHOLDER) },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, enabled = coach.canSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = TalkWording.SEND)
        }
    }
}

/** D158: each with the label and destination it has on the old page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    suggestions: List<CoachSuggestion>,
    onAskAboutTheWeek: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onPlanAhead: () -> Unit,
    onSeePlan: () -> Unit,
    onEverythingElse: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Related), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        suggestions.forEach { suggestion ->
            when (suggestion) {
                is CoachSuggestion.Review -> Suggestion(TrainerWording.rowAction(null)) { onReview(suggestion.session.id) }
                CoachSuggestion.CountSession -> Suggestion(TalkWording.COUNT_SESSION, onEverythingElse)
                CoachSuggestion.AskAboutTheWeek -> Suggestion(TalkWording.ASK_ABOUT_THE_WEEK, onAskAboutTheWeek)
                is CoachSuggestion.Next -> when (val action = suggestion.action) {
                    is MainAction.PlanOpen -> Suggestion(TrainerWeekWording.planOpen(action.kind), onPlan)
                    MainAction.PlanNext -> Suggestion(stringResource(R.string.trainer_plan_next), onPlan)
                    MainAction.PlanAhead -> Suggestion(stringResource(R.string.trainer_evaluate), onPlanAhead)
                    MainAction.PlanThisWeek -> Suggestion(PlanPageWording.PLAN_THIS_WEEK, onSeePlan)
                    MainAction.FillNextWeek -> Suggestion(PlanPageWording.FILL_NEXT_WEEK, onSeePlan)
                    MainAction.NextBlock -> Suggestion(PlanPageWording.NEXT_BLOCK, onSeePlan)
                }
            }
        }
    }
}

@Composable
private fun Suggestion(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

/** D160: its name, the start of its newest words, when it last moved, and whether a message is not answered. */
@Composable
private fun ConversationRow(summary: ConversationSummary, today: Long, zone: ZoneId, onConversation: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = { onConversation(summary.key) })
            .padding(vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related), verticalAlignment = Alignment.CenterVertically) {
            Text(TalkWording.name(summary), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(TalkWording.lastMoved(summary.lastAtMillis, today, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(TalkWording.firstWords(summary.lastWords), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (summary.notAnswered) Text(TalkWording.NOT_ANSWERED, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** A plain row, in the old page's form. */
@Composable
private fun PlainRow(label: String, line: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text("›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** D157: the conversations the coach screen shows. */
private const val LATEST = 3
