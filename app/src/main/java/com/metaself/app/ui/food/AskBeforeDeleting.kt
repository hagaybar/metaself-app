package com.metaself.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.ui.theme.Spacing

/**
 * "Delete “Greek salad”? This cannot be undone." with Delete and Keep it (D36).
 *
 * Asked before a whole food or a whole saved meal goes, because neither can be put back: an undo
 * would have to hold a food's names, brand and facts with their provenance, or a meal's parts with
 * their amounts, where a question costs one tap and holds nothing. The day's single entries are the
 * exception — cheap to restore exactly, so they keep delete-then-Undo.
 *
 * Drawn in place of the Delete button it came from, the way the join question is drawn, so it
 * appears where his finger already is. One composable for both screens, so the food's question and
 * the meal's cannot drift into two wordings. The name reaches the sentence through a format argument
 * in `strings.xml`, never by joining strings here.
 */
@Composable
fun AskBeforeDeleting(name: String, onDelete: () -> Unit, onKeep: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(R.string.delete_question, name),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Filled then text, as Join them / Not now: the button that acts is the one that looks it.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Button(onClick = onDelete) { Text(stringResource(R.string.delete_do_it)) }
            TextButton(onClick = onKeep) { Text(stringResource(R.string.delete_keep_it)) }
        }
    }
}
