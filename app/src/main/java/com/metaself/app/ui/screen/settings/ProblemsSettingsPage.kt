package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/** Settings' Recent problems page (D79): what has gone wrong, to copy or clear. Moved unchanged. */
@Composable
fun ProblemsSettingsPage(
    state: SettingsUiState,
    onCopyProblems: () -> Unit,
    onClearProblems: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(SettingsPage.PROBLEMS.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- what has gone wrong ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_problems_note),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            if (state.problems.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_problems_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.problems.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    TextButton(onClick = onCopyProblems) {
                        Text(stringResource(R.string.settings_problems_copy))
                    }
                    TextButton(onClick = onClearProblems) {
                        Text(stringResource(R.string.settings_problems_clear))
                    }
                }
            }
        }
    }
}
