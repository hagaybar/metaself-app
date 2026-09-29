package com.metaself.app.ui.screen.letter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.theme.Spacing

/**
 * Weekly letters (D103), from the Trainer screen: Write it now when a week wants its letter, with what it
 * sends said under it (D16), then every letter newest first, by week and headline. Branches only — no
 * early return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun LettersScreen(
    state: LettersViewModel.State,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onWriteNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(title = LetterWording.LIST_TITLE, modifier = modifier, onBack = onBack) {
        if (state.canWriteNow || state.writing) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Button(onClick = onWriteNow, enabled = !state.writing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.writing) LetterWording.WRITING else LetterWording.WRITE_NOW)
                }
                Text(
                    LetterWording.privacyLetter(state.ceiling),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.said?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        when {
            state.loading -> Unit
            state.unreadable -> Text(LetterWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
            state.letters.isEmpty() -> Text(LetterWording.EMPTY, style = MaterialTheme.typography.bodyMedium)
            else -> Column {
                state.letters.forEach { row ->
                    LetterRow(row, onOpen)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LetterRow(row: LettersViewModel.Row, onOpen: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onOpen(row.weekMonday) }
            .padding(vertical = Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(row.week, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(row.headline, style = MaterialTheme.typography.bodyLarge)
        }
        if (row.new) {
            Text(LetterWording.NEW, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
