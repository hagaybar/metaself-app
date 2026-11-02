package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * The coach screen (D157, D166): the chat is the focus. The box is at the bottom, where it is in a conversation, with the
 * line on what leaves the phone under it. Above it, the coach's note when there is one and, while the box is empty, up
 * to three quick links. All conversations and Everything else are in the menu.
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
    onLetter: (Long) -> Unit,
    onReplyToNote: (WeekNote) -> Unit,
    onAllConversations: () -> Unit,
    onEverythingElse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(
        title = stringResource(R.string.trainer_title),
        modifier = modifier,
        onBack = onBack,
        scrolls = false,
        actions = { Menu(coach.conversations.isNotEmpty(), onAllConversations, onEverythingElse) },
    ) {
        Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.Related, Alignment.Bottom),
            ) {
                trainer.note?.let { note -> NoteView(note, zone) { onReplyToNote(note) } }
                if (coach.unreadable) Text(TalkWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
                val home = trainer.home
                if (home != null && coach.draft.isBlank()) {
                    QuickLinks(CoachSuggestions.of(home, coach.conversations), onAskAboutTheWeek, onReview, onPlan, onPlanAhead, onSeePlan, onLetter, onEverythingElse)
                }
            }
            coach.refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Box(coach, onEdit, onSend)
            SentToOpenAi(coach.ceiling)
        }
    }
}

/** D166: the title bar's menu. With no conversations there is no way to an empty list. */
@Composable
private fun Menu(hasConversations: Boolean, onAllConversations: () -> Unit, onEverythingElse: () -> Unit) {
    var showing by remember { mutableStateOf(false) }
    IconButton(onClick = { showing = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.today_more))
    }
    DropdownMenu(expanded = showing, onDismissRequest = { showing = false }) {
        if (hasConversations) {
            DropdownMenuItem(text = { Text(TalkWording.ALL_CONVERSATIONS) }, onClick = {
                showing = false
                onAllConversations()
            })
        }
        DropdownMenuItem(text = { Text(TalkWording.EVERYTHING_ELSE) }, onClick = {
            showing = false
            onEverythingElse()
        })
    }
}

/** D166: one line, and the whole of what leaves the phone (D84) a tap away. Also at the head of a conversation. */
@Composable
internal fun SentToOpenAi(ceiling: Int) {
    var showing by remember { mutableStateOf(false) }
    Text(
        TalkWording.SENT_TO_OPENAI,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = { showing = true })
            .wrapContentHeight(Alignment.CenterVertically),
    )
    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(TalkWording.WHATS_SENT) },
            text = { Text(TrainerWording.privacyTalk(ceiling), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showing = false }) { Text(TalkWording.CLOSE) } },
        )
    }
}

/** D160: every conversation, with its newest words. */
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

/** D158, D166: each with the label and destination it has on the old page, as plain rows just above the box. */
@Composable
private fun QuickLinks(
    links: List<CoachSuggestion>,
    onAskAboutTheWeek: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onPlanAhead: () -> Unit,
    onSeePlan: () -> Unit,
    onLetter: (Long) -> Unit,
    onEverythingElse: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        links.forEach { link ->
            when (link) {
                is CoachSuggestion.Review -> QuickLink(TrainerWording.rowAction(null)) { onReview(link.session.id) }
                CoachSuggestion.CountSession -> QuickLink(TalkWording.COUNT_SESSION, onEverythingElse)
                is CoachSuggestion.Letter -> QuickLink(TalkWording.SEE_LETTER) { onLetter(link.weekMonday) }
                CoachSuggestion.AskAboutTheWeek -> QuickLink(TalkWording.ASK_ABOUT_THE_WEEK, onAskAboutTheWeek)
                is CoachSuggestion.Next -> when (val action = link.action) {
                    is MainAction.PlanOpen -> QuickLink(TrainerWeekWording.planOpen(action.kind), onPlan)
                    MainAction.PlanNext -> QuickLink(stringResource(R.string.trainer_plan_next), onPlan)
                    MainAction.PlanAhead -> QuickLink(stringResource(R.string.trainer_evaluate), onPlanAhead)
                    MainAction.PlanThisWeek -> QuickLink(PlanPageWording.PLAN_THIS_WEEK, onSeePlan)
                    MainAction.FillNextWeek -> QuickLink(PlanPageWording.FILL_NEXT_WEEK, onSeePlan)
                    MainAction.NextBlock -> QuickLink(PlanPageWording.NEXT_BLOCK, onSeePlan)
                }
            }
        }
    }
}

@Composable
private fun QuickLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
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
