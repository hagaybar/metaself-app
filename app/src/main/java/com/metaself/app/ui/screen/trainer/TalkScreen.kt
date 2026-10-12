package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TalkWording
import com.metaself.app.ui.trainer.TrainerWording
import java.time.ZoneId

/**
 * Your trainer (D145, D146): one thread, oldest at the top, the newest in view — the trainer's messages on the left on
 * a card, the owner's on the right in light green, a day line where the day changes, a session's feedback in full
 * under its label, a letter as a card that opens it — and the field with Send at the bottom. The AI line and what
 * leaves the phone head the thread. Branches only — no early return out of an inline composable
 * (InlineComposableReturnGuardTest).
 */
@Composable
fun TalkScreen(
    state: TalkViewModel.State,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSend: () -> Unit,
    onTryAgain: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onAskDelete: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    /** D146: a letter's card opens the letter page, by its week's Monday. */
    onLetter: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = TalkWording.TITLE, modifier = modifier, onBack = onBack, scrolls = false) {
        Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            val list = rememberLazyListState()
            val rows = rows(state, zone)
            // The newest in view: on opening, and whenever a message or the writing line arrives.
            LaunchedEffect(rows.size) { list.scrollToItem(rows.lastIndex) }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        ThreadRow.Head -> Head(state)
                        is ThreadRow.Day -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(TalkWording.day(row.epochDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is ThreadRow.Entry -> Item(row.item, state, zone, onTryAgain, onSelect, onAskDelete, onLetter)
                        ThreadRow.Writing -> Text(
                            TalkWording.WRITING,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.failure?.let { Text(TalkWording.failure(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            state.refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Composer(state, onEdit, onSend)
        }
    }
    state.confirmingDelete?.let {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            text = { Text(TalkWording.CONFIRM_DELETE) },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text(TalkWording.DELETE) } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text(TalkWording.KEEP) } },
        )
    }
}

/** One row of the thread's list: the head, a day line, an item, or the trainer writing. */
private sealed interface ThreadRow {
    val key: String

    data object Head : ThreadRow {
        override val key = "head"
    }

    data class Day(val epochDay: Long) : ThreadRow {
        override val key get() = "day-$epochDay"
    }

    data class Entry(val item: ThreadItem) : ThreadRow {
        override val key
            get() = when (item) {
                is ThreadItem.Said -> "said-${item.message.id}"
                is ThreadItem.AfterSession -> "after-${item.workoutId}"
                is ThreadItem.Letter -> "letter-${item.weekMonday}"
            }
    }

    data object Writing : ThreadRow {
        override val key = "writing"
    }
}

/** The head, then each item under a day line where the day changes, then the writing line while the trainer writes. */
private fun rows(state: TalkViewModel.State, zone: ZoneId): List<ThreadRow> = buildList {
    add(ThreadRow.Head)
    var day: Long? = null
    state.thread.forEach { item ->
        val its = TalkWording.epochDay(item.atMillis, zone)
        if (its != day) add(ThreadRow.Day(its))
        day = its
        add(ThreadRow.Entry(item))
    }
    if (state.writing != null) add(ThreadRow.Writing)
}

/** The AI line (D4), what leaves the phone (D148), and with nothing yet said, what this is for. */
@Composable
private fun Head(state: TalkViewModel.State) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(TalkWording.AI, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(TrainerWording.privacyTalk(state.ceiling), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.unreadable -> Text(TalkWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
            state.loaded && state.thread.isEmpty() -> Text(TalkWording.EMPTY, style = MaterialTheme.typography.bodyMedium)
            else -> Unit
        }
    }
}

@Composable
private fun Item(
    item: ThreadItem,
    state: TalkViewModel.State,
    zone: ZoneId,
    onTryAgain: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onAskDelete: (Long) -> Unit,
    onLetter: (Long) -> Unit,
) {
    when (item) {
        is ThreadItem.Said -> if (item.message.fromOwner) {
            Mine(item.message, state, onTryAgain, onSelect, onAskDelete)
        } else {
            Theirs(null, item.message.text)
        }
        is ThreadItem.AfterSession -> Theirs(TalkWording.afterSession(item.kind, item.minutes), Conversation.text(item))
        is ThreadItem.Letter -> LetterCard(item, zone, onLetter)
    }
}

/** The trainer's: on the left, on a card, under a label when it is a session's feedback. */
@Composable
private fun Theirs(label: String?, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The owner's: on the right, light green. Tapped, it shows Delete; not answered, it says so with Try again (D145),
 * which waits while the trainer is writing.
 */
@Composable
private fun Mine(
    message: TrainerMessage,
    state: TalkViewModel.State,
    onTryAgain: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onAskDelete: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp).clickable(role = Role.Button, onClick = { onSelect(message.id) }),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(message.text, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
        val answering = state.writing == message.id
        if (!message.answered && !answering) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Text(TalkWording.NOT_ANSWERED, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onTryAgain(message.id) }, enabled = !state.busy) { Text(TalkWording.TRY_AGAIN) }
            }
        }
        if (state.selected == message.id && !answering) {
            TextButton(onClick = { onAskDelete(message.id) }) { Text(TalkWording.DELETE) }
        }
    }
}

/** D146: the letter as a card — its week and when it arrived — opening the letter page. */
@Composable
private fun LetterCard(letter: ThreadItem.Letter, zone: ZoneId, onLetter: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = { onLetter(letter.weekMonday) }),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(TalkWording.letterTitle(letter.weekMonday), style = MaterialTheme.typography.titleMedium)
                Text(TalkWording.letterLine(letter.atMillis, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The field and Send, which waits while the words are stored or the trainer is writing. */
@Composable
private fun Composer(state: TalkViewModel.State, onEdit: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = onEdit,
            placeholder = { Text(TalkWording.PLACEHOLDER) },
            enabled = state.loaded,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, enabled = state.canSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = TalkWording.SEND)
        }
    }
}
