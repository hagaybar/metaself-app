package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.Sheets
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.SheetWording
import java.time.ZoneId

/**
 * What I know about you (D149, D150), top to bottom: the header line; the four groups under their eyebrows, each fact
 * on a card with its since-line and Edit and Remove; No longer true, struck through, with when each was dropped;
 * "Last rewritten" and Full conversation ›; and Go back to the previous version while the newest version is a rewrite.
 * Before any rewrite, what will appear and when. Branches only — no early return out of an inline composable
 * (InlineComposableReturnGuardTest).
 */
@Composable
fun SheetScreen(
    state: SheetViewModel.State,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onEditText: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onRemove: (Long) -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onGoBack: () -> Unit,
    onConfirmGoBack: () -> Unit,
    onCancelGoBack: () -> Unit,
    /** The conversation, Your trainer (D145). */
    onConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = SheetWording.TITLE, modifier = modifier, onBack = onBack) {
        Text(SheetWording.HEADER, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(SheetWording.PRIVACY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val newest = state.newest
        when {
            state.unreadable -> Text(SheetWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
            !state.loaded -> Unit
            newest == null -> Text(SheetWording.EMPTY, style = MaterialTheme.typography.bodyLarge)
            else -> {
                if (newest.current.isEmpty()) Text(SheetWording.NONE_NOW, style = MaterialTheme.typography.bodyLarge)
                FactGroup.entries.forEach { group ->
                    val facts = newest.current.filter { it.group == group }
                    if (facts.isNotEmpty()) {
                        Eyebrow(SheetWording.group(group))
                        facts.forEach { fact -> FactCard(fact, state.busy, onEdit, onRemove) }
                    }
                }
                val dropped = state.dropped
                if (dropped.isNotEmpty()) {
                    Eyebrow(SheetWording.NO_LONGER_TRUE)
                    dropped.forEach { DroppedFact(it) }
                }
            }
        }
        state.refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.lastRewrite?.let { SheetWording.lastRewritten(it, zone) + " ·" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onConversation) { Text(SheetWording.FULL_CONVERSATION) }
        }
        if (state.canGoBack) {
            OutlinedButton(onClick = onGoBack, modifier = Modifier.fillMaxWidth()) { Text(SheetWording.GO_BACK) }
        }
    }
    state.editing?.let { editing ->
        AlertDialog(
            onDismissRequest = onCancelEdit,
            title = { Text(SheetWording.EDIT_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    OutlinedTextField(
                        value = editing.text,
                        onValueChange = onEditText,
                        minLines = 2,
                        maxLines = 6,
                        isError = editing.text.trim().length > Sheets.MAX_FACT,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(SheetWording.count(editing.text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.editRefused?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = onSaveEdit, enabled = !state.busy) { Text(SheetWording.SAVE) } },
            dismissButton = { TextButton(onClick = onCancelEdit) { Text(SheetWording.CANCEL) } },
        )
    }
    state.removing?.let {
        AlertDialog(
            onDismissRequest = onCancelRemove,
            text = { Text(SheetWording.CONFIRM_REMOVE) },
            confirmButton = { TextButton(onClick = onConfirmRemove) { Text(SheetWording.REMOVE) } },
            dismissButton = { TextButton(onClick = onCancelRemove) { Text(SheetWording.KEEP) } },
        )
    }
    if (state.goingBack) {
        AlertDialog(
            onDismissRequest = onCancelGoBack,
            text = { Text(SheetWording.CONFIRM_GO_BACK) },
            confirmButton = { TextButton(onClick = onConfirmGoBack) { Text(SheetWording.GO_BACK_YES) } },
            dismissButton = { TextButton(onClick = onCancelGoBack) { Text(SheetWording.KEEP) } },
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = Spacing.Related),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One fact: its words, where it came from and since when, and Edit and Remove. */
@Composable
private fun FactCard(fact: SheetFact, busy: Boolean, onEdit: (Long) -> Unit, onRemove: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fact.text, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SheetWording.factLine(fact),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onEdit(fact.id) }, enabled = !busy) { Text(SheetWording.EDIT) }
                TextButton(onClick = { onRemove(fact.id) }, enabled = !busy) { Text(SheetWording.REMOVE) }
            }
        }
    }
}

/** No longer true: the words struck through, and when it held and was dropped. */
@Composable
private fun DroppedFact(fact: SheetFact) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            fact.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = TextDecoration.LineThrough,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(SheetWording.droppedLine(fact), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
